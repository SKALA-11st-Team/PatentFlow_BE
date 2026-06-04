package com.syuuk.patentflow.patent.dto;

import java.util.List;

/**
 * @description AI 평가 레포트 일괄 생성 요청의 처리 결과와 대상 특허 ID 목록을 반환한다.
 */
public record BatchAiReportResult(
        int requestedCount,
        int generatedCount,
        int skippedCount,
        int failedCount,
        List<String> generatedPatentIds,
        List<String> skippedPatentIds,
        List<String> failedPatentIds
) {}
