package com.syuuk.patentflow.patent.dto;

import java.util.List;

/**
 * DASH-F3: 검토 대상 특허의 영역별(사업/기술/제품) 분포 집계. 대시보드 분포 카드가 전체 특허 배열을
 * 받아 클라이언트에서 재집계하던 것을 서버 집계로 대체한다(테이블과 동일 필터 기준으로 정합 보장).
 *
 * @param totalCount       집계 대상 특허 총수(= review-targets 필터 결과 크기).
 * @param businessArea     사업영역별 분포(보조 라벨 = 부서명).
 * @param technologyArea   기술영역별 분포(보조 라벨 = 사업영역).
 * @param product          제품별 분포(보조 라벨 = 기술영역).
 * @param country          출원 국가별 분포(보조 라벨 = 사업영역).
 */
public record AreaDistributionResponse(
        int totalCount,
        List<AreaGroupResponse> businessArea,
        List<AreaGroupResponse> technologyArea,
        List<AreaGroupResponse> product,
        List<AreaGroupResponse> country
) {
}
