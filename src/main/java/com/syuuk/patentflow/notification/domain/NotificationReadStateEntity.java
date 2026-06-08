package com.syuuk.patentflow.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "notification_read_states",
        indexes = {
                @Index(name = "idx_notification_read_user", columnList = "user_id, is_read")
        })
public class NotificationReadStateEntity {

    @Id
    @Column(length = 160)
    private String id;

    @Column(name = "notification_id", nullable = false, length = 64)
    private String notificationId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected NotificationReadStateEntity() {
    }

    public NotificationReadStateEntity(String notificationId, String userId, boolean read, OffsetDateTime updatedAt) {
        this.id = id(notificationId, userId);
        this.notificationId = notificationId;
        this.userId = userId;
        this.read = read;
        this.updatedAt = updatedAt;
    }

    public static String id(String notificationId, String userId) {
        return userId + "::" + notificationId;
    }

    public String getNotificationId() {
        return notificationId;
    }

    public boolean isRead() {
        return read;
    }

    public void update(boolean read, OffsetDateTime updatedAt) {
        this.read = read;
        this.updatedAt = updatedAt;
    }
}
