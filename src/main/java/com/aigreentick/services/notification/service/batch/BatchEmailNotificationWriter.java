
package com.aigreentick.services.notification.service.batch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.aigreentick.services.notification.model.entity.EmailNotification;
import com.aigreentick.services.notification.repository.EmailNotificationRepository;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Batch writer for high-volume email notification persistence
 * Reduces MongoDB write load by batching inserts
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchEmailNotificationWriter {
    
    private final EmailNotificationRepository notificationRepository;
    
    private static final int BATCH_SIZE = 50;
    private static final int QUEUE_CAPACITY = 1000;
    private static final long FLUSH_INTERVAL_MS = 1000; // 1 second
    
    private final BlockingQueue<EmailNotification> writeQueue = 
            new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    
    private volatile boolean running = true;
    private Thread writerThread;
    
    @PostConstruct
    public void init() {
        startBatchWriter();
    }
    
    @PreDestroy
    public void shutdown() {
        running = false;
        if (writerThread != null) {
            writerThread.interrupt();
        }
        flushAll(); // Flush remaining items
    }
    
    /**
     * Add notification to batch queue (non-blocking)
     */
    public boolean enqueue(EmailNotification notification) {
        try {
            boolean added = writeQueue.offer(notification, 100, TimeUnit.MILLISECONDS);
            if (!added) {
                log.warn("Batch write queue is full. Writing synchronously.");
                notificationRepository.save(notification);
            }
            return added;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while enqueueing notification", e);
            return false;
        }
    }
    
    /**
     * Start background batch writer thread
     */
    private void startBatchWriter() {
        writerThread = new Thread(() -> {
            log.info("Batch notification writer started");
            
            List<EmailNotification> batch = new ArrayList<>(BATCH_SIZE);
            long lastFlushTime = System.currentTimeMillis();
            
            while (running) {
                try {
                    EmailNotification notification = writeQueue.poll(100, TimeUnit.MILLISECONDS);
                    
                    if (notification != null) {
                        batch.add(notification);
                    }
                    
                    long now = System.currentTimeMillis();
                    boolean timeToFlush = (now - lastFlushTime) >= FLUSH_INTERVAL_MS;
                    
                    // Flush if batch is full or enough time has passed
                    if (batch.size() >= BATCH_SIZE || (timeToFlush && !batch.isEmpty())) {
                        flushBatch(batch);
                        batch.clear();
                        lastFlushTime = now;
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.info("Batch writer interrupted");
                    break;
                } catch (Exception e) {
                    log.error("Error in batch writer", e);
                }
            }
            
            // Final flush on shutdown
            if (!batch.isEmpty()) {
                flushBatch(batch);
            }
            
            log.info("Batch notification writer stopped");
        }, "batch-notification-writer");
        
        writerThread.setDaemon(false); // Ensure it completes on shutdown
        writerThread.start();
    }
    
    /**
     * Flush batch to MongoDB
     */
    private void flushBatch(List<EmailNotification> batch) {
        try {
            long startTime = System.currentTimeMillis();
            notificationRepository.saveAll(batch);
            long duration = System.currentTimeMillis() - startTime;
            
            log.info("Flushed {} notifications to MongoDB in {}ms", batch.size(), duration);
            
        } catch (Exception e) {
            log.error("Error flushing batch of {} notifications", batch.size(), e);
            // Fallback: try individual saves
            for (EmailNotification notification : batch) {
                try {
                    notificationRepository.save(notification);
                } catch (Exception ex) {
                    log.error("Error saving individual notification: {}", 
                            notification.getId(), ex);
                }
            }
        }
    }
    
    /**
     * Flush all remaining items (called on shutdown)
     */
    private void flushAll() {
        List<EmailNotification> remaining = new ArrayList<>();
        writeQueue.drainTo(remaining);
        
        if (!remaining.isEmpty()) {
            log.info("Flushing {} remaining notifications on shutdown", remaining.size());
            flushBatch(remaining);
        }
    }
    
    /**
     * Scheduled health check
     */
    @Scheduled(fixedDelay = 30000)
    public void logQueueStatus() {
        log.debug("Batch writer queue size: {}/{}", writeQueue.size(), QUEUE_CAPACITY);
    }

}