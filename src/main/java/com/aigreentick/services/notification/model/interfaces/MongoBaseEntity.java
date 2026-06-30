package com.aigreentick.services.notification.model.interfaces;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public abstract class MongoBaseEntity extends BaseEntity<String>
        implements Auditable, SoftDeletable {

    @Id
    private String id;

    private Instant createdAt;

    private Instant updatedAt;

    private Long createdByUserId;

    private Long updatedByUserId;

    private boolean isDeleted;

    private Instant deletedAt;

    @Override
    public void markDeleted() {
        this.isDeleted = true;
        this.deletedAt = Instant.now();
    }

    /**
     * Call before inserting a new document.
     */
    public void prePersist() {
        Instant now = Instant.now();

        if (createdAt == null) {
            createdAt = now;
        }

        updatedAt = now;
    }

    /**
     * Call before updating an existing document.
     */
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}