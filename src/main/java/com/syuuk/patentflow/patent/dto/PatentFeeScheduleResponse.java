package com.syuuk.patentflow.patent.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * @relatedFR FR-LEGAL-24
 * @relatedUI UI-LEGAL-04
 * FEE-06: 특허 상세의 연차료 일정 — 도래일·검토 시작일(고지 메일 발송일)·수신처를 한 번에 내려
 * FE가 국가별 규칙을 중복 계산하지 않게 한다.
 */
public record PatentFeeScheduleResponse(
        String patentId,
        String country,
        String basis,
        LocalDate basisDate,
        String paymentRuleLabel,
        int initialLumpYears,
        int mailLeadMonths,
        FeeScheduleRecipient recipient,
        List<FeeScheduleEntry> items
) {

    /** 고지 메일 수신처 — 담당 부서의 주 수신자/CC (users 테이블 파생, FR-LEGAL-12와 동일 규칙). */
    public record FeeScheduleRecipient(
            String departmentId,
            String departmentName,
            String managerName,
            String managerEmail,
            List<String> ccEmails
    ) {
    }

    /**
     * 일정 한 줄 — status: PAID_LUMP(설정등록 시 일괄 납부) | PAST(도래 경과) | NEXT(다음 도래) | FUTURE(이후 예정).
     * reviewStartDate는 도래일에서 메일 리드타임을 뺀 검토 시작(고지 발송) 예정일이며 일괄 구간에는 없다.
     * adjusted는 NEXT 도래일이 관리자 조정값(FR-LEGAL-24)으로 대체되었음을 뜻한다.
     */
    public record FeeScheduleEntry(
            String yearLabel,
            int yearNumber,
            boolean lump,
            LocalDate dueDate,
            LocalDate reviewStartDate,
            String status,
            boolean adjusted,
            // F2: 납부 예상액(국가 요금표 기반, 청구항 수 미반영 개략치). 요금표 없는 국가는 null.
            Long estimatedAmount,
            String currency
    ) {
    }
}
