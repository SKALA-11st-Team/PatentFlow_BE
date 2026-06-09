package com.syuuk.patentflow.patent.dto;

/**
 * ORCH-06/AIREPORT-02: 축별 세부 근거. 근거 문구와 클릭형 출처(SourceResponse)를 담는다.
 */
public record EvidenceDetailResponse(
        String text,
        SourceResponse source
) {
}
