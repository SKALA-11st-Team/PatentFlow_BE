package com.syuuk.patentflow.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "notifications",
        indexes = {
                @Index(name = "idx_notification_target_created", columnList = "target_role, created_at")
        })
public class NotificationEntity {

    @Id
    @Column(name = "notification_id", length = 64)
    private String notificationId;

    @Column(nullable = false, length = 200)
    private String title;

    // 특허 제목(최대 1000자)이 삽입되는 메시지라 1000자 제한 초과 가능 — TEXT로 저장
    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "target_role", nullable = false, length = 32)
    private String targetRole;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(length = 500)
    private String link;

    protected NotificationEntity() {
    }

    public NotificationEntity(String notificationId, String title, String message,
            String targetRole, OffsetDateTime createdAt, String link) {
        this.notificationId = notificationId;
        this.title = title;
        this.message = message;
        this.targetRole = targetRole;
        this.createdAt = createdAt;
        this.link = link;
    }

    public String getNotificationId() {
        return notificationId;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getTargetRole() {
        return targetRole;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public String getLink() {
        return link;
    }
}
