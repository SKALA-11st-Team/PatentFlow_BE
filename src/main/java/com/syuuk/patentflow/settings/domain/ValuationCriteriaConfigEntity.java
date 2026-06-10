package com.syuuk.patentflow.settings.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * AI 가치평가 기준(축 가중치/등급 컷오프/유지 임계/subscore 배점)의 버전 관리 저장소.
 *
 * 매 수정마다 새 버전 행이 추가되며(append-only), 최신 버전이 활성 설정이다.
 * 레포트 생성 시 적용된 버전 스냅샷이 patent_review_history.ai_applied_criteria_json에
 * 함께 저장되어 "이 레포트는 어떤 기준으로 산정됐나"를 추적할 수 있다.
 * key-value SystemSettings 대신 전용 엔티티를 쓰는 이유는 버전 이력이 핵심 요구이기 때문.
 */
@Entity
@Table(name = "valuation_criteria_configs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"version"}))
public class ValuationCriteriaConfigEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "version", nullable = false)
    private int version;

    // 계약 C1의 valuationConfig 형태(camelCase JSON) 그대로 저장 — agent에 무변환 전달.
    @Column(name = "config_json", nullable = false, columnDefinition = "TEXT")
    private String configJson;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected ValuationCriteriaConfigEntity() {
    }

    public ValuationCriteriaConfigEntity(int version, String configJson, String createdBy, OffsetDateTime createdAt) {
        this.id = UUID.randomUUID().toString();
        this.version = version;
        this.configJson = configJson;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public int getVersion() {
        return version;
    }

    public String getConfigJson() {
        return configJson;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
