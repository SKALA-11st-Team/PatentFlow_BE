package com.syuuk.patentflow.business.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 체크리스트 항목 생성/수정 요청(관리자 — 리걸팀).
 * 점수 라벨은 4점(최고)~1점(최저) 선택지의 설명 문구다.
 */
public record BusinessChecklistItemRequest(
        @NotBlank String category,
        @NotBlank String title,
        String description,
        @NotBlank String score4Label,
        @NotBlank String score3Label,
        @NotBlank String score2Label,
        @NotBlank String score1Label
) {
}
