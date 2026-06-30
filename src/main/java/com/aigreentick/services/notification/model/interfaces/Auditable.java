package com.aigreentick.services.notification.model.interfaces;

import java.time.Instant;

public interface Auditable {
   Instant getCreatedAt();

   Instant getUpdatedAt();

   Long getCreatedByUserId();

   Long getUpdatedByUserId();

   void setCreatedAt(Instant createdAt);

   void setUpdatedAt(Instant updatedAt);

   void setCreatedByUserId(Long id);

   void setUpdatedByUserId(Long id);
}

