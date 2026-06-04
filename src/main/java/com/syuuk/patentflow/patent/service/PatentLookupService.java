package com.syuuk.patentflow.patent.service;

import com.syuuk.patentflow.patent.client.GooglePatentsLookupClient;
import com.syuuk.patentflow.patent.client.KiprisPatentLookupClient;
import com.syuuk.patentflow.patent.client.PatentLookupQuery;
import com.syuuk.patentflow.patent.dto.PatentBibliographicInfoResponse;
import com.syuuk.patentflow.patent.dto.PatentContextSuggestionRequest;
import com.syuuk.patentflow.patent.dto.PatentContextSuggestionResponse;
import com.syuuk.patentflow.patent.dto.PatentDetailResponse;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * 외부 특허 정보 조회 및 컨텍스트 추천 서비스.
 *
 * - KIPRIS / Google Patents API를 통한 서지 정보 조회
 * - 입력 키워드와 내부 특허 metadata를 비교해 사업 분야/기술 영역을 추천
 * - allPatents 목록은 호출자(PatentReviewService)가 전달 — 이 서비스는 외부 클라이언트만 의존
 */
@Service
public class PatentLookupService {

    private static final Set<String> CONTEXT_STOP_WORDS =
            Set.of("관련", "기술", "시스템", "방법", "특허", "장치");
    private static final Pattern CONTEXT_TOKEN_SPLITTER = Pattern.compile("[^0-9a-z가-힣]+");

    private final KiprisPatentLookupClient kiprisPatentLookupClient;
    private final GooglePatentsLookupClient googlePatentsLookupClient;

    public PatentLookupService(
            KiprisPatentLookupClient kiprisPatentLookupClient,
            GooglePatentsLookupClient googlePatentsLookupClient
    ) {
        this.kiprisPatentLookupClient = kiprisPatentLookupClient;
        this.googlePatentsLookupClient = googlePatentsLookupClient;
    }

    // ── 서지 정보 조회 ────────────────────────────────────────

    /**
     * 관리번호/등록번호로 KIPRIS 또는 Google Patents에서 서지 정보를 조회한다.
     * allPatents는 PatentReviewService.loadPatentsFromDatabase()에서 전달받는다.
     */
    public PatentBibliographicInfoResponse lookupBibliographicInfo(
            String managementNumber,
            String applicationNumber,
            String registrationNumber,
            String sourcePriority,
            List<PatentDetailResponse> allPatents
    ) {
        String lookupValue = firstNonBlank(applicationNumber, firstNonBlank(registrationNumber, managementNumber));
        if (lookupValue == null) return null;

        String keyword = lookupValue.trim().toLowerCase(Locale.ROOT);
        PatentDetailResponse knownPatent = allPatents.stream()
                .filter(patent -> lowerEquals(patent.managementNumber(), keyword)
                        || lowerEquals(patent.applicationNumber(), keyword)
                        || lowerEquals(patent.registrationNumber(), keyword))
                .findFirst()
                .orElse(null);

        PatentLookupQuery query = new PatentLookupQuery(
                knownPatent == null ? lookupValue.trim() : knownPatent.managementNumber(),
                knownPatent == null ? firstNonBlank(applicationNumber, lookupValue.trim()) : knownPatent.applicationNumber(),
                knownPatent == null ? registrationNumber : knownPatent.registrationNumber(),
                knownPatent == null ? "KR" : knownPatent.country());

        for (String source : lookupPriority(sourcePriority)) {
            PatentBibliographicInfoResponse externalResult = switch (source) {
                case "KIPRIS" -> kiprisPatentLookupClient.lookup(query).orElse(null);
                case "GOOGLE_PATENTS" -> googlePatentsLookupClient.lookup(query).orElse(null);
                default -> null;
            };
            if (externalResult != null) {
                return mergeBibliographicInfo(externalResult, knownPatent);
            }
        }

        return knownPatent == null ? null : toBibliographicInfo(knownPatent);
    }

    // ── 컨텍스트 추천 ─────────────────────────────────────────

    /**
     * 입력값과 내부 특허 metadata를 비교해 가장 유사한 사업 분야/기술 영역을 추천한다.
     */
    public PatentContextSuggestionResponse suggestContext(
            PatentContextSuggestionRequest request,
            List<PatentDetailResponse> allPatents
    ) {
        List<String> sourceTokens = tokenizeContextText(String.join(" ",
                valueOrDefault(request.title(), ""),
                valueOrDefault(request.productName(), ""),
                valueOrDefault(request.technologyArea(), ""),
                valueOrDefault(request.businessArea(), ""),
                valueOrDefault(request.applicationNumber(), "")));
        if (sourceTokens.isEmpty()) return null;

        return allPatents.stream()
                .map(patent -> scoredSuggestion(sourceTokens, patent))
                .max(Comparator.comparingInt(ScoredContextSuggestion::score))
                .filter(candidate -> candidate.score() > 0)
                .map(candidate -> new PatentContextSuggestionResponse(
                        candidate.patent().businessArea(),
                        confidenceText(candidate.score()),
                        "%s 특허의 공식 metadata 키워드와 입력값을 비교했습니다."
                                .formatted(candidate.patent().title()),
                        candidate.patent().technologyArea()))
                .orElse(null);
    }

    // ── 내부 유틸 ─────────────────────────────────────────────

    private PatentBibliographicInfoResponse toBibliographicInfo(PatentDetailResponse patent) {
        return new PatentBibliographicInfoResponse(
                patent.managementNumber(),
                valueOrDefault(patent.title(), patent.draftTitle()),
                patent.applicationDate(), patent.coApplicants(), patent.country(),
                patent.registrationDate(), patent.applicationNumber(),
                patent.registrationNumber(), patent.expectedExpirationDate(), "KIPRIS");
    }

    private PatentBibliographicInfoResponse mergeBibliographicInfo(
            PatentBibliographicInfoResponse externalResult,
            PatentDetailResponse knownPatent
    ) {
        if (knownPatent == null) return externalResult;
        return new PatentBibliographicInfoResponse(
                valueOrDefault(knownPatent.managementNumber(), externalResult.managementNumber()),
                valueOrDefault(externalResult.title(), knownPatent.title()),
                valueOrDefault(externalResult.applicationDate(), knownPatent.applicationDate()),
                valueOrDefault(externalResult.coApplicants(), knownPatent.coApplicants()),
                valueOrDefault(externalResult.country(), knownPatent.country()),
                valueOrDefault(externalResult.registrationDate(), knownPatent.registrationDate()),
                valueOrDefault(externalResult.applicationNumber(), knownPatent.applicationNumber()),
                valueOrDefault(externalResult.registrationNumber(), knownPatent.registrationNumber()),
                valueOrDefault(externalResult.expectedExpirationDate(), knownPatent.expectedExpirationDate()),
                externalResult.source());
    }

    private List<String> lookupPriority(String sourcePriority) {
        if (sourcePriority == null || sourcePriority.isBlank()) return List.of("KIPRIS", "GOOGLE_PATENTS");
        return Arrays.stream(sourcePriority.split(","))
                .map(String::trim).filter(s -> !s.isBlank()).toList();
    }

    private ScoredContextSuggestion scoredSuggestion(List<String> sourceTokens, PatentDetailResponse patent) {
        Set<String> targetTokens = Set.copyOf(tokenizeContextText(String.join(" ",
                valueOrDefault(patent.title(), ""), valueOrDefault(patent.draftTitle(), ""),
                valueOrDefault(patent.productName(), ""), valueOrDefault(patent.businessArea(), ""),
                valueOrDefault(patent.technologyArea(), ""))));
        int overlapScore = sourceTokens.stream()
                .mapToInt(token -> targetTokens.contains(token) ? contextTokenWeight(token) : 0).sum();
        int categoryScore = sourceTokens.stream()
                .anyMatch(token -> lowerContains(patent.businessArea(), token)
                        || lowerContains(patent.technologyArea(), token)) ? 3 : 0;
        return new ScoredContextSuggestion(patent, overlapScore + categoryScore);
    }

    private List<String> tokenizeContextText(String value) {
        return Arrays.stream(CONTEXT_TOKEN_SPLITTER.split(value.toLowerCase(Locale.ROOT)))
                .map(String::trim).filter(t -> t.length() >= 2)
                .filter(t -> !CONTEXT_STOP_WORDS.contains(t)).distinct().toList();
    }

    private int contextTokenWeight(String token) { return token.length() >= 4 ? 2 : 1; }

    private String confidenceText(int score) {
        if (score >= 6) return "높음";
        if (score >= 3) return "보통";
        return "낮음";
    }

    private String valueOrDefault(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    private java.time.LocalDate valueOrDefault(java.time.LocalDate value, java.time.LocalDate defaultValue) {
        return value != null ? value : defaultValue;
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }

    private boolean lowerEquals(String value, String lowerKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).equals(lowerKeyword);
    }

    private boolean lowerContains(String value, String token) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(token);
    }

    private record ScoredContextSuggestion(PatentDetailResponse patent, int score) {}
}
