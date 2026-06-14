package com.syuuk.patentflow.business.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 체크리스트 항목 생성/수정 요청(관리자 — 리걸팀).
 * 점수 라벨은 4점(최고)~1점(최저) 선택지의 설명 문구다.
 * @Size 상한은 BusinessChecklistItemEntity 컬럼 길이와 정렬한다(초과 시 400 검증 실패).
 */
public record BusinessChecklistItemRequest(
        @NotBlank @Size(max = 128) String category,
        @NotBlank @Size(max = 256) String title,
        String description,
        @NotBlank @Size(max = 500) String score4Label,
        @NotBlank @Size(max = 500) String score3Label,
        @NotBlank @Size(max = 500) String score2Label,
        @NotBlank @Size(max = 500) String score1Label
) {
}
