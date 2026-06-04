package com.syuuk.patentflow.mailing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "mailing_history")
public class MailingHistoryEntity {

    @Id
    @Column(length = 64)
    private String mailingId;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(columnDefinition = "TEXT")
    private String ccEmailsJson;

    private int patentCount;

    @Column(columnDefinition = "TEXT")
    private String patentsJson;

    @Column(length = 256)
    private String recipientEmail;

    @Column(length = 128)
    private String recipientName;

    @Column(name = "department_id", length = 64)
    private String departmentId;

    private OffsetDateTime sentAt;

    @Column(length = 128)
    private String sentBy;

    @Column(length = 32)
    private String status;

    @Column(length = 500)
    private String subject;

    protected MailingHistoryEntity() {
    }

    public MailingHistoryEntity(
            String mailingId,
            String body,
            String ccEmailsJson,
            int patentCount,
            String patentsJson,
            String recipientEmail,
            String recipientName,
            String departmentId,
            OffsetDateTime sentAt,
            String sentBy,
            String status,
            String subject
    ) {
        this.mailingId = mailingId;
        this.body = body;
        this.ccEmailsJson = ccEmailsJson;
        this.patentCount = patentCount;
        this.patentsJson = patentsJson;
        this.recipientEmail = recipientEmail;
        this.recipientName = recipientName;
        this.departmentId = departmentId;
        this.sentAt = sentAt;
        this.sentBy = sentBy;
        this.status = status;
        this.subject = subject;
    }

    public String getMailingId() {
        return mailingId;
    }

    public String getBody() {
        return body;
    }

    public String getCcEmailsJson() {
        return ccEmailsJson;
    }

    public int getPatentCount() {
        return patentCount;
    }

    public String getPatentsJson() {
        return patentsJson;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public OffsetDateTime getSentAt() {
        return sentAt;
    }

    public String getSentBy() {
        return sentBy;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSubject() {
        return subject;
    }
}
