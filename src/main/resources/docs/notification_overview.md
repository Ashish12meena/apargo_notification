# Notification Service — Overview & Flow Documentation

**Service name:** `notification-service`
**Package root:** `com.aigreentick.services.notification`
**Port:** `7996`
**Stack:** Spring Boot, MongoDB, Redis, Eureka (service discovery), OpenFeign, Resilience4j (retry/circuit breaker), Thymeleaf (email templating), Firebase Admin SDK (FCM), SendGrid/SMTP, async thread pools

This service is responsible for sending **Email** and **Push** notifications on behalf of other internal microservices (e.g. the broadcast/messaging services). It exposes both synchronous and asynchronous APIs, supports multiple providers per channel with fallback, tracks delivery status, and publishes audit events for every send attempt.

---

## 1. High-Level Architecture

```
Client / Internal Service
        │
        ▼
ServiceRateLimitInterceptor  ── (per-service + global rate limiting)
        │
        ▼
   Controller layer  (EmailNotificationController, PushNotificationController, EmailTemplateController)
        │
        ▼
   Orchestrator layer (EmailOrchestratorServiceImpl, PushOrchestratorServiceImpl)
        │   - validates request
        │   - resolves template / device token
        ▼
   Delivery layer (EmailDeliveryServiceImpl, PushDeliveryServiceImpl)
        │   - picks a provider via Selector
        │   - calls provider.send()
        │   - updates notification status (PENDING → PROCESSING → SENT/FAILED)
        │   - persists via Batch Writer (MongoDB)
        │   - publishes audit event (Feign → audit-service)
        ▼
   Provider layer (Strategy pattern)
        Email: SmtpEmailProvider, SendGridEmailProvider
        Push:  FcmPushProvider, ApnsPushProvider, WebPushProvider
```

Cross-cutting concerns sit around this pipeline: **Idempotency** (Redis, dedupes by `eventId`), **Resilience4j Retry/Circuit Breaker** (around provider calls), **Caching** (Thymeleaf templates cached in Redis), and **Audit Events** (fire-and-forget Feign calls to an external `audit-service`).

---

## 2. Domain Model

| Entity | Storage | Purpose |
|---|---|---|
| `EmailNotification` | MongoDB | One record per email send attempt — recipient, subject, body, status, provider used, retry count, timestamps |
| `EmailTemplate` | MongoDB | Reusable Thymeleaf email templates, identified by `templateCode`, cached in Redis, supports activate/deactivate |
| `PushNotification` | MongoDB | One record per push send attempt — user, device token, platform, title/body/data payload, status |
| `DeviceToken` | MongoDB | Registered push tokens per user/device, tied to a platform (iOS/Android/Web), can be deactivated |

`NotificationStatus` lifecycle (shared by both Email and Push): `PENDING → PROCESSING → SENT` (success) or `PENDING → PROCESSING → FAILED` (failure, with `retryCount` incremented).

---

## 3. Email Flows

### 3.1 Synchronous send — `POST /api/v1/notification/email/send`
Multipart request with `request` (JSON body), optional `attachments`, optional `inline` resources (for inline images in HTML emails).

**Flow:**
1. Controller receives multipart request → maps to `EmailNotificationRequest` via `EmailNotificationMapper`.
2. `EmailValidationService` validates recipients, subject, body, attachment constraints.
3. `EmailDeliveryServiceImpl.deliver()` is called **inside a transaction**, wrapped with `@Retry(name = "emailRetry")`:
   - `EmailProviderSelector.selectProvider()` picks the currently *active* provider from config (SMTP or SendGrid) and checks `isAvailable()`.
   - Creates an `EmailNotification` record with status `PROCESSING`.
   - Calls `provider.send(request)`.
   - On success → status `SENT`; on exception → status `FAILED` and a `NotificationSendException` is thrown.
   - Notification is persisted via `BatchEmailNotificationWriter` (queued for batch insert; falls back to direct save if the queue is full or disabled).
4. If all retries are exhausted, the Resilience4j fallback (`deliverFallback`) creates a `FAILED` record instead of propagating the raw exception.
5. Controller returns `201 Created` with the notification's status.

### 3.2 Asynchronous send — `POST /api/v1/notification/email/send/async`
Same input shape as the sync endpoint, but returns immediately.

**Flow:**
1. Validate request (same as sync).
2. `EmailDeliveryServiceImpl.createPendingNotification()` immediately persists a record with status `PENDING` and returns its ID.
3. `EmailDeliveryServiceImpl.deliverAsync()` is dispatched to a dedicated thread pool (`emailTaskExecutor`, configured via `async.email.*` in `application.yml`: core pool 5, max pool 10, queue capacity 100) and:
   - Updates status to `PROCESSING`.
   - Selects provider, calls `provider.send()`.
   - On success → status `SENT`, publishes an `EMAIL_SENT` audit event (recipients, subject, processing time) via `AuditEventPublisher` → `AuditFeignClient` → external audit-service.
   - On failure → status `FAILED`, retryCount incremented, exception logged (retry is governed by `@Retry(name = "emailRetry")`; final fallback marks the record `FAILED` permanently).
4. Controller immediately returns `202 Accepted` with an `AsyncEmailResponse` containing `notificationId`, `PENDING` status, and a `statusCheckUrl` (`/api/v1/notification/email/status/{id}`) for polling.

### 3.3 Templated send — `POST /api/v1/notification/email/send/templated` (sync) and `/send/templated/async`
Used when the caller wants to send a pre-defined template (e.g. "welcome email", "OTP email") rather than a raw subject/body.

**Flow:**
1. `EmailOrchestratorServiceImpl.processTemplate()` builds a base `EmailNotificationRequest` (to/cc/bcc/attachments) from the incoming `SendTemplatedEmailRequest`.
2. `EmailTemplateProcessorService.processTemplateByCode()`:
   - Looks up the `EmailTemplate` by `templateCode` — **cached in Redis** (`@Cacheable(value = "emailTemplates")`) to avoid a Mongo hit on every send.
   - Verifies the template is `active`; throws `EmailTemplateNotFoundException`/processing exception otherwise.
   - Renders the subject/body using Thymeleaf (`stringTemplateEngine` for inline string templates, `fileTemplateEngine` for file-based ones), substituting the caller-supplied `variables` map.
3. The rendered request is validated and then routed through the same sync/async delivery path described in 3.1/3.2.

### 3.4 Batch send — `POST /api/v1/notification/email/send/batch/async` *(marked "not use ready" in code — present but not production-hardened)*
Accepts a list of email requests with shared attachment/inline files and simply loops, calling `sendEmailAsync()` for each — returning a list of `AsyncEmailResponse`.

### 3.5 Status check — `GET /api/v1/notification/email/status/{notificationId}`
Looks up the `EmailNotification` by ID and returns its current status (useful for polling after an async/templated-async send).

### 3.6 Template management — `EmailTemplateController` (`/api/v1/notifications/email/templates`)
Standard CRUD plus lifecycle:
- `POST /` create template, `PUT /{id}` update, `GET /{id}` / `GET /code/{code}` / `GET /` fetch.
- `DELETE /{id}` delete.
- `PUT /{id}/activate` and `PUT /{id}/deactivate` toggle whether a template can be used for sending (an inactive template causes templated-send to fail).

---

## 4. Push Notification Flows

### 4.1 Device registration — `POST /api/v1/notification/push/device/register`
Registers a device token for a user/platform combination via `DeviceTokenService.registerToken()`. Other endpoints:
- `GET /device/user/{userId}` — list all tokens for a user.
- `DELETE /device/{deviceToken}` — deactivate a token (also triggered automatically — see 4.4).

### 4.2 Synchronous send — `POST /api/v1/notification/push/send`
**Flow:**
1. `PushValidationService` validates the request (must include either `deviceToken` or `userId`, plus title/body).
2. `resolveDeviceToken()`:
   - If `deviceToken` is given → look up that specific active token.
   - Else if `userId` is given → fetch all active tokens for the user and use the first one.
3. `PushDeliveryServiceImpl.deliver()` (transactional, `@Retry(name = "emailRetry")` — shares the email retry policy):
   - `PushProviderSelector.selectProviderByPlatform(platform)` picks a provider per-platform:
     - **iOS** → APNs first, falls back to FCM.
     - **Android** → FCM.
     - **Web** → Web Push first, falls back to FCM.
     - If no platform-specific provider is available, falls back to whatever `selectProvider()` deems globally active (with a priority-sorted fallback among all available providers).
   - Sends via `provider.send()`, creates/updates the `PushNotification` record (`PROCESSING` → `SENT`/`FAILED`).
   - If the provider error message indicates an invalid/unregistered token, the device token is **automatically deactivated** (see 4.4).
   - Persisted via `BatchPushNotificationWriter` (same batching pattern as email).

### 4.3 Asynchronous send — `POST /api/v1/notification/push/send/async`
Same as sync, but creates a `PENDING` record immediately and dispatches `deliverAsync()` on the `pushTaskExecutor` thread pool, returning `202 Accepted` with a `statusCheckUrl`. On success, a `NOTIFICATION_PROCESSED` audit event is published with userId, platform, title, and processing time.

### 4.4 Send to all of a user's devices — `POST /api/v1/notification/push/send/user`
Fetches **all active device tokens** for `userId`, and for each one independently creates a pending notification and dispatches an async delivery — returning a list of `AsyncPushResponse`, one per device. This is the "send everywhere this user is logged in" flow (e.g. mobile + web).

### 4.5 Auto-deactivation of bad tokens
Both sync and async push delivery paths check `isInvalidTokenError()` (looks for "invalid"/"unregistered"/"token" in the provider's exception message). If matched, `DeviceTokenService.deactivateToken()` is called so future sends skip that dead token — this keeps the device registry self-healing without manual cleanup.

### 4.6 Status check — `GET /api/v1/notification/push/status/{notificationId}`
Same polling pattern as email status check.

---

## 5. Cross-Cutting Mechanisms

### 5.1 Rate limiting (`ServiceRateLimitInterceptor`)
Applied to all `/api/v1/notification/*` routes. Each calling internal service must send an `X-Service-Id` header (falls back to `unknown-service` with a warning if missing). `InternalServiceRateLimiter` enforces:
- **Global** cap: 1000 requests/min, burst 1500 (protects the whole system).
- **Per-service** cap: 200 requests/min, burst 300 (protects against one noisy caller starving others).
On rejection, the interceptor returns `429 Too Many Requests` with a structured JSON error and writes `X-RateLimit-*` headers on every allowed response for visibility.

### 5.2 Provider selection (Strategy pattern)
- **Email:** `EmailProviderSelector` picks the single "active" provider from `EmailProviderProperties` config (e.g. SMTP vs SendGrid) and checks availability — no automatic fallback between email providers, it throws `ProviderNotAvailableException` if the active one is down.
- **Push:** `PushProviderSelector` is platform-aware and *does* fall back automatically (APNs→FCM for iOS, WebPush→FCM for web), plus a priority-ordered fallback across all available providers if the configured active one is down.

### 5.3 Resilience (Resilience4j)
- `@Retry(name = "emailRetry")` wraps both sync delivery methods (email and push reuse the same named retry policy) and async delivery methods, with dedicated fallback methods that mark the notification `FAILED` once retries are exhausted instead of bubbling an unhandled exception.
- Circuit breaker configuration also exists (`CircuitBreakerConfiguration`, `ResilienceConfig`) to guard provider calls against cascading failures.

### 5.4 Idempotency (`IdempotencyService`)
Redis-backed deduplication keyed by `idempotency:email:{eventId}` with a 24-hour TTL, using `SETNX` semantics (`setIfAbsent`) so only the **first** caller for a given `eventId` proceeds; duplicates are detected and skipped. Designed to protect against duplicate event delivery from upstream Kafka/queue consumers. Fails open (allows processing) if Redis itself is unavailable, to avoid blocking notifications on an infra outage.

### 5.5 Batch persistence (`BatchEmailNotificationWriter` / `BatchPushNotificationWriter`)
Rather than writing every notification status update straight to MongoDB, status changes are pushed onto an in-memory `BlockingQueue` and flushed periodically (configurable: batch size 50, queue capacity 1000, flush interval 1000ms, toggle via `batch.notification.enabled`). This reduces write load under high-throughput bulk sending. If the queue is full or batching is disabled, the write falls back to a direct synchronous save so no status update is silently lost.

### 5.6 Audit trail (`AuditEventPublisher` → `AuditFeignClient`)
Every successful async delivery (email and push) publishes an `AuditEvent` (event type, entity id/type, action, status, and channel-specific metadata) to an **external audit-service** over Feign (`AuditFeignClient`, fire-and-forget — "no fallback needed, if audit fails we just log it"). This gives a separate, queryable audit log outside the notification service's own status tracking.

### 5.7 Template caching
`EmailTemplateProcessorService.getTemplateByCode()` is `@Cacheable(value = "emailTemplates")`, backed by Redis, so repeated sends of the same template (e.g. OTP, welcome email) skip the MongoDB lookup after the first hit. Template create/update/delete/activate/deactivate operations should evict this cache (see `@CacheEvict` usage in the service) to keep it consistent.

---

## 6. Configuration Profiles
`application.yml` activates profile `dev` and includes: `resilience`, `feign`, `database`, `eureka`, `redis`, `notification-provider` — each broken into its own `application-<profile>.yml` for: Resilience4j settings, Feign client config (used for the audit-service call), MongoDB connection, Eureka service discovery registration, Redis connection (idempotency + template cache), and active email/push provider selection + credentials (SMTP/SendGrid, Firebase/APNs/WebPush keys).

---

## 7. API Summary Table

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/notification/email/send` | Send email, wait for result (sync) |
| POST | `/api/v1/notification/email/send/async` | Send email, return tracking ID immediately |
| POST | `/api/v1/notification/email/send/templated` | Send templated email (sync) |
| POST | `/api/v1/notification/email/send/templated/async` | Send templated email (async) |
| POST | `/api/v1/notification/email/send/batch/async` | Send multiple emails (async, not production-ready) |
| GET | `/api/v1/notification/email/status/{id}` | Poll email delivery status |
| POST | `/api/v1/notifications/email/templates` | Create email template |
| PUT | `/api/v1/notifications/email/templates/{id}` | Update template |
| GET | `/api/v1/notifications/email/templates/{id}` \| `/code/{code}` \| `/` | Fetch template(s) |
| DELETE | `/api/v1/notifications/email/templates/{id}` | Delete template |
| PUT | `/api/v1/notifications/email/templates/{id}/activate` \| `/deactivate` | Toggle template usability |
| POST | `/api/v1/notification/push/device/register` | Register a device token |
| GET | `/api/v1/notification/push/device/user/{userId}` | List a user's device tokens |
| DELETE | `/api/v1/notification/push/device/{deviceToken}` | Deactivate a device token |
| POST | `/api/v1/notification/push/send` | Send push to one device (sync) |
| POST | `/api/v1/notification/push/send/async` | Send push to one device (async) |
| POST | `/api/v1/notification/push/send/user` | Send push to every device of a user (async, fan-out) |
| GET | `/api/v1/notification/push/status/{id}` | Poll push delivery status |

---

## 8. Notable Design Patterns Used
- **Strategy pattern** for providers (`EmailProviderStrategy`, `PushProviderStrategy`) with a `Selector` choosing the implementation at runtime.
- **Orchestrator → Delivery → Provider** layering, separating request shaping/validation from actual send mechanics and from network/SDK calls.
- **Sync/Async dual API** on nearly every operation, letting callers choose between immediate confirmation and fire-and-forget with polling.
- **Outbox-ish audit publishing** (best-effort Feign call after successful delivery) decoupled from the core transactional write path.
- **Self-healing device registry** via automatic token deactivation on provider-reported invalid tokens.
