package com.syuuk.patentflow.patent.dto;

/**
 * ORCH-06/AIREPORT-02: 근거 출처(제목/URL). 축별 근거(evidenceDetails)와 외부 출처(externalSources)에서 공유한다.
 */
public record SourceResponse(
        String title,
        String url
) {
}
