package com.syuuk.patentflow.patent.service;

import com.syuuk.patentflow.patent.client.GooglePatentsLookupClient;
import com.syuuk.patentflow.patent.client.KiprisPatentLookupClient;
import com.syuuk.patentflow.patent.client.PatentLookupException;
import com.syuuk.patentflow.patent.client.PatentLookupQuery;
import com.syuuk.patentflow.patent.dto.PatentBibliographicInfoResponse;
import com.syuuk.patentflow.patent.dto.PatentContextSuggestionRequest;
import com.syuuk.patentflow.patent.dto.PatentContextSuggestionResponse;
import com.syuuk.patentflow.patent.dto.PatentDetailResponse;
import com.syuuk.patentflow.patent.dto.PatentLookupStatus;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(PatentLookupService.class);
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
                        || lowerEquals(patent.registrationNumber(), keyword)
                        || normalizedPatentNumberEquals(patent.country(), patent.registrationNumber(), lookupValue)
                        || normalizedPatentNumberEquals(patent.country(), patent.applicationNumber(), lookupValue))
                .findFirst()
                .orElse(null);

        PatentLookupQuery query = new PatentLookupQuery(
                knownPatent == null ? lookupValue.trim() : knownPatent.managementNumber(),
                knownPatent == null ? firstNonBlank(applicationNumber, lookupValue.trim()) : knownPatent.applicationNumber(),
                knownPatent == null ? registrationNumber : knownPatent.registrationNumber(),
                knownPatent == null ? "KR" : knownPatent.country());

        PatentLookupException lastLookupException = null;
        for (String source : lookupPriority(sourcePriority)) {
            PatentBibliographicInfoResponse externalResult;
            try {
                externalResult = switch (source) {
                    case "KIPRIS" -> kiprisPatentLookupClient.lookup(query).orElse(null);
                    case "GOOGLE_PATENTS" -> googlePatentsLookupClient.lookup(query).orElse(null);
                    default -> null;
                };
            } catch (PatentLookupException exception) {
                if (lookupStatusFor(exception) == PatentLookupStatus.SOURCE_UNCONFIGURED) {
                    log.warn("External patent lookup source is not configured. source={}, keyword={}",
                            exception.source(), query.keyword());
                } else {
                    log.warn("External patent lookup source failed. source={}, keyword={}",
                            exception.source(), query.keyword(), exception);
                }
                lastLookupException = exception;
                continue;
            }
            if (externalResult != null) {
                return mergeBibliographicInfo(externalResult, knownPatent);
            }
        }

        if (knownPatent != null) {
            PatentLookupStatus status = lastLookupException == null
                    ? PatentLookupStatus.NOT_FOUND
                    : lookupStatusFor(lastLookupException);
            return toBibliographicInfo(knownPatent, status, lookupMessageFor(lastLookupException));
        }
        if (lastLookupException != null) {
            return emptyLookupResult(lookupValue.trim(), lookupStatusFor(lastLookupException), emptyLookupMessageFor(lastLookupException));
        }
        return emptyLookupResult(lookupValue.trim(), PatentLookupStatus.NOT_FOUND, "외부 특허 정보와 내부 metadata에서 일치 항목을 찾지 못했습니다.");
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

    private PatentBibliographicInfoResponse toBibliographicInfo(
            PatentDetailResponse patent,
            PatentLookupStatus lookupStatus,
            String lookupMessage
    ) {
        return new PatentBibliographicInfoResponse(
                patent.managementNumber(),
                valueOrDefault(patent.title(), patent.draftTitle()),
                patent.applicationDate(), patent.coApplicants(), patent.country(),
                patent.registrationDate(), patent.applicationNumber(),
                patent.registrationNumber(), patent.expectedExpirationDate(), "INTERNAL_METADATA",
                lookupStatus, "LOW", lookupMessage);
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
                externalResult.source(),
                externalResult.lookupStatus(),
                externalResult.sourceConfidence(),
                externalResult.lookupMessage());
    }

    private PatentBibliographicInfoResponse emptyLookupResult(
            String lookupValue,
            PatentLookupStatus lookupStatus,
            String lookupMessage
    ) {
        return new PatentBibliographicInfoResponse(
                lookupValue,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                lookupStatus,
                "NONE",
                lookupMessage);
    }

    private PatentLookupStatus lookupStatusFor(PatentLookupException exception) {
        if (exception == null) {
            return PatentLookupStatus.NOT_FOUND;
        }
        return exception.getMessage() != null && exception.getMessage().contains("not configured")
                ? PatentLookupStatus.SOURCE_UNCONFIGURED
                : PatentLookupStatus.SOURCE_ERROR;
    }

    private String lookupMessageFor(PatentLookupException exception) {
        if (exception == null) {
            return "외부 특허 정보에서 일치 항목을 찾지 못해 내부 metadata를 반환했습니다.";
        }
        if (lookupStatusFor(exception) == PatentLookupStatus.SOURCE_UNCONFIGURED) {
            return "%s 조회 키가 설정되지 않아 내부 metadata를 반환했습니다.".formatted(exception.source());
        }
        return "%s 조회 중 오류가 발생해 내부 metadata를 반환했습니다.".formatted(exception.source());
    }

    private String emptyLookupMessageFor(PatentLookupException exception) {
        if (lookupStatusFor(exception) == PatentLookupStatus.SOURCE_UNCONFIGURED) {
            return "%s 조회 키가 설정되지 않아 외부 조회를 수행할 수 없습니다.".formatted(exception.source());
        }
        return "%s 조회 중 오류가 발생했고 내부 metadata에서도 일치 항목을 찾지 못했습니다.".formatted(exception.source());
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

    private boolean normalizedPatentNumberEquals(String country, String storedValue, String inputValue) {
        String stored = normalizePatentNumber(country, storedValue);
        String input = normalizePatentNumber(country, inputValue);
        return !stored.isBlank() && stored.equals(input);
    }

    private String normalizePatentNumber(String country, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalizedCountry = country == null || country.isBlank() ? "KR" : country.trim().toUpperCase(Locale.ROOT);
        String digits = value.replaceAll("[^0-9]", "");
        if ("KR".equals(normalizedCountry) && digits.length() == 9 && digits.startsWith("10")) {
            return "KR:" + digits.substring(0, 2) + "-" + digits.substring(2);
        }
        return normalizedCountry + ":" + digits;
    }

    private boolean lowerContains(String value, String token) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(token);
    }

    private record ScoredContextSuggestion(PatentDetailResponse patent, int score) {}
}
