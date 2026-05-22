package com.syuuk.patentflow.patent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "annual_fee_adjustments")
public class AnnualFeeAdjustmentEntity {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Id
    @Column(name = "adjustment_id", length = 64)
    private String adjustmentId;

    @Column(name = "patent_id", length = 32, nullable = false)
    private String patentId;

    @Column(name = "previous_due_date")
    private LocalDate previousDueDate;

    @Column(name = "adjusted_due_date", nullable = false)
    private LocalDate adjustedDueDate;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Column(name = "adjusted_by", length = 128)
    private String adjustedBy;

    @Column(name = "adjusted_at")
    private OffsetDateTime adjustedAt;

    protected AnnualFeeAdjustmentEntity() {
    }

    public AnnualFeeAdjustmentEntity(
            String adjustmentId,
            String patentId,
            LocalDate previousDueDate,
            LocalDate adjustedDueDate,
            String reason,
            String adjustedBy
    ) {
        this.adjustmentId = adjustmentId;
        this.patentId = patentId;
        this.previousDueDate = previousDueDate;
        this.adjustedDueDate = adjustedDueDate;
        this.reason = reason;
        this.adjustedBy = adjustedBy;
        this.adjustedAt = OffsetDateTime.now(KST);
    }

    public String getAdjustmentId() { return adjustmentId; }
    public String getPatentId() { return patentId; }
    public LocalDate getPreviousDueDate() { return previousDueDate; }
    public LocalDate getAdjustedDueDate() { return adjustedDueDate; }
    public String getReason() { return reason; }
    public String getAdjustedBy() { return adjustedBy; }
    public OffsetDateTime getAdjustedAt() { return adjustedAt; }
}
