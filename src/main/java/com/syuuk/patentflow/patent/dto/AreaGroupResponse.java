package com.syuuk.patentflow.patent.dto;

import java.util.List;

/**
 * DASH-F3: 대시보드 영역 분포의 한 그룹(1차값 기준 건수 + 중복 제거된 보조 라벨).
 *
 * @param value         1차 분류값(사업/기술/제품). 공백·"N/A"는 "미분류"로 정규화됨.
 * @param count         해당 분류값의 특허 건수.
 * @param relatedLabels 그룹 내 보조 라벨 집합(사업영역→부서명, 기술영역→사업영역, 제품→기술영역). 중복 제거.
 */
public record AreaGroupResponse(
        String value,
        int count,
        List<String> relatedLabels
) {
}
