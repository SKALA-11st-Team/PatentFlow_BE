package com.syuuk.patentflow.patent.dto;

import java.time.LocalDate;

/**
 * CONTRACT-09/DASH-08: 특허 목록 페이징 조회의 서버 필터 묶음.
 *
 * <p>FE가 클라이언트에서 거르던 분기/국가/날짜/영역/검토여부를 서버로 위임하기 위한 입력 캐리어다.
 * 컨트롤러가 @RequestParam 들을 모아 생성하므로 OpenAPI 응답 스키마로 노출되지 않는다.
 *
 * @param keyword              제목·관리/출원/등록번호 LIKE 검색.
 * @param departmentId         최신 이력의 담당 부서.
 * @param reviewWorkflowStatus 최신 이력의 검토 워크플로 상태.
 * @param quarter              "Q1"~"Q4" 또는 quarterKey("2026-Q2"). "ALL"/blank 는 미적용.
 * @param country              국가 코드(대소문자 무시). "ALL"/blank 는 미적용.
 * @param dateFrom             연차료 납부 기준일 하한.
 * @param dateTo               연차료 납부 기준일 상한.
 * @param businessArea         사업영역 정확 일치("미분류"는 공백/"N/A" 매칭).
 * @param technologyArea       기술영역 정확 일치("미분류"는 공백/"N/A" 매칭).
 * @param productName          제품 정확 일치("미분류"는 공백/"N/A" 매칭).
 * @param inReview             현재 분기 검토 대상 여부(null=미적용).
 */
public record PatentListFilter(
        String keyword,
        String departmentId,
        ReviewWorkflowStatus reviewWorkflowStatus,
        String quarter,
        String country,
        LocalDate dateFrom,
        LocalDate dateTo,
        String businessArea,
        String technologyArea,
        String productName,
        Boolean inReview
) {
}
