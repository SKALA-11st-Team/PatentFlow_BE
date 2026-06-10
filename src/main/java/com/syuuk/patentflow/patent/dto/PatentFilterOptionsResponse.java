package com.syuuk.patentflow.patent.dto;

import java.util.List;

/**
 * CONTRACT-09/DASH-08: 검토 대상 목록 화면의 필터 드롭다운 옵션. 전체 특허에서 산출한 distinct 값이라
 * 서버 필터링으로 목록이 부분집합이 되어도 드롭다운이 줄어들지 않게 한다(클라이언트 전체 배열 의존 제거).
 *
 * @param countries        국가 코드 distinct(정렬).
 * @param businessAreas    사업영역 distinct(정렬).
 * @param technologyAreas  기술영역 distinct(정렬).
 * @param productNames     제품 distinct(정렬).
 */
public record PatentFilterOptionsResponse(
        List<String> countries,
        List<String> businessAreas,
        List<String> technologyAreas,
        List<String> productNames
) {
}
