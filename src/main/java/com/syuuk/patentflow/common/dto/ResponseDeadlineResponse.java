package com.syuuk.patentflow.common.dto;

// 사업부 회신 기한 설정 — 분기 시작일 기준 "N개월 M일 전"으로 계산
// GET/PATCH /api/v1/settings/response-deadline 공용 DTO
public record ResponseDeadlineResponse(int months, int days) {}
