package com.syuuk.patentflow.settings.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 연도 무관 분기 경계(월/일)를 저장하는 템플릿.
 * 기존에는 연도별로 quarter_settings 레코드를 미리 시드해야 했는데,
 * 이 테이블을 두면 새 연도가 시작될 때마다 시드를 추가할 필요 없이
 * 활성화 시점에 템플릿 + 연도로 QuarterSettingEntity를 동적으로 생성할 수 있다.
 */
@Entity
@Table(name = "review_period_templates")
public class ReviewPeriodTemplateEntity {

    @Id
    private int periodNumber; // 1-based (1, 2, 3, 4)

    private int startMonth;
    private int startDay;
    private int endMonth;
    private int endDay;

    protected ReviewPeriodTemplateEntity() {}

    public ReviewPeriodTemplateEntity(int periodNumber, int startMonth, int startDay, int endMonth, int endDay) {
        this.periodNumber = periodNumber;
        this.startMonth = startMonth;
        this.startDay = startDay;
        this.endMonth = endMonth;
        this.endDay = endDay;
    }

    public int getPeriodNumber() { return periodNumber; }
    public int getStartMonth() { return startMonth; }
    public int getStartDay() { return startDay; }
    public int getEndMonth() { return endMonth; }
    public int getEndDay() { return endDay; }

    public void update(int startMonth, int startDay, int endMonth, int endDay) {
        this.startMonth = startMonth;
        this.startDay = startDay;
        this.endMonth = endMonth;
        this.endDay = endDay;
    }
}
