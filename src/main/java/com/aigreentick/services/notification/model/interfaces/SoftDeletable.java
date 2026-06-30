package com.aigreentick.services.notification.model.interfaces;


import java.time.Instant;

public interface SoftDeletable {
   boolean isDeleted();

   Instant getDeletedAt();

   void markDeleted();
}
