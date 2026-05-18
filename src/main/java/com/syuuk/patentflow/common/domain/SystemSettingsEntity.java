package com.syuuk.patentflow.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "system_settings")
public class SystemSettingsEntity {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Id
    @Column(length = 128)
    private String key;

    @Column(columnDefinition = "TEXT")
    private String value;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    protected SystemSettingsEntity() {}

    public SystemSettingsEntity(String key) {
        this.key = key;
        this.updatedAt = OffsetDateTime.now(KST);
    }

    public String getKey() { return key; }
    public String getValue() { return value; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void setValue(String value) {
        this.value = value;
        this.updatedAt = OffsetDateTime.now(KST);
    }
}
