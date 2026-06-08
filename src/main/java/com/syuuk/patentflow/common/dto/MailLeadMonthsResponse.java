package com.syuuk.patentflow.common.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

// 분기 시작 N개월 전에 검토를 시작(메일 발송)할 기준 개월 수 — GET/PATCH 공용 DTO
public record MailLeadMonthsResponse(@Min(0) @Max(24) int mailLeadMonths) {}
