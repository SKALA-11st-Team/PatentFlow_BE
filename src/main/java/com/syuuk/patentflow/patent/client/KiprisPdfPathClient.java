package com.syuuk.patentflow.patent.client;

import com.syuuk.patentflow.patent.config.PatentLookupProperties;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.YearMonth;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;

/**
 * @relatedFR FR-LEGAL-13
 * MAIL-12: KIPRIS 공개전문PDF 경로 조회(getPubFullTextInfoSearch).
 * 출원번호로 공개전문 PDF의 다운로드 경로(body.item.path)와 파일명(docName)을 받아온다.
 * 서비스 키 회전·쿼터 마커는 KiprisPatentLookupClient와 동일한 규칙을 따른다.
 */
@Component
public class KiprisPdfPathClient {

    private static final Logger log = LoggerFactory.getLogger(KiprisPdfPathClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final List<String> QUOTA_EXHAUSTED_MARKERS = List.of(
            "quota", "limit", "exceed", "exceeded", "too many", "일일", "월간", "초과", "한도", "제한");

    private final PatentLookupProperties properties;
    private final HttpClient httpClient;
    private final Map<String, YearMonth> exhaustedKeys = new ConcurrentHashMap<>();

    public KiprisPdfPathClient(PatentLookupProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    public record KiprisPdfPath(String downloadUrl, String docName) {
    }

    /** 출원번호(숫자만)로 공개전문 PDF 경로를 조회한다(KR 기본). 미공개·키 미설정·쿼터 소진이면 empty. */
    public Optional<KiprisPdfPath> findPdfPath(String applicationNumber) {
        return findPdfPath("KR", applicationNumber);
    }

    /**
     * W4: 국가별 공개전문 PDF 경로 조회 — pdf-path-operations 설정에 오퍼레이션이 등록된
     * 국가(US/JP/CN 등)만 지원한다. 미등록 국가는 empty(원문 URL 폴백 경로로 흐른다).
     */
    public Optional<KiprisPdfPath> findPdfPath(String country, String applicationNumber) {
        PatentLookupProperties.Kipris kipris = properties.kipris();
        String operation = kipris.pdfOperationFor(country);
        if (operation == null) {
            return Optional.empty();
        }
        String digits = applicationNumber == null ? "" : applicationNumber.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            return Optional.empty();
        }
        List<String> serviceKeys = serviceKeys(kipris);
        if (serviceKeys.isEmpty()) {
            return Optional.empty();
        }

        YearMonth currentMonth = YearMonth.now();
        for (String serviceKey : serviceKeys) {
            if (currentMonth.equals(exhaustedKeys.get(serviceKey))) {
                continue;
            }
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .GET()
                        .timeout(TIMEOUT)
                        .uri(URI.create("%s?applicationNumber=%s&ServiceKey=%s".formatted(
                                operationUrl(kipris, operation),
                                URLEncoder.encode(digits, StandardCharsets.UTF_8),
                                URLEncoder.encode(serviceKey, StandardCharsets.UTF_8))))
                        .build();
                HttpResponse<String> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                String body = response.body();
                if (isQuotaExhausted(body)) {
                    exhaustedKeys.put(serviceKey, currentMonth);
                    continue;
                }
                if (response.statusCode() < 200 || response.statusCode() >= 300 || body == null || body.isBlank()) {
                    return Optional.empty();
                }
                return parse(body);
            } catch (Exception exception) {
                log.warn("KIPRIS 공개전문 PDF 경로 조회 실패. applicationNumber={}", digits, exception);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private Optional<KiprisPdfPath> parse(String xml) {
        Document document = PatentLookupXmlParser.parse(xml);
        String resultCode = PatentLookupXmlParser.text(document, "resultCode").orElse("");
        if (!resultCode.isBlank() && !"00".equals(resultCode)) {
            return Optional.empty();
        }
        Optional<String> path = PatentLookupXmlParser.text(document, "path");
        if (path.isEmpty() || path.get().isBlank()) {
            return Optional.empty();
        }
        String docName = PatentLookupXmlParser.text(document, "docName").orElse("patent.pdf");
        return Optional.of(new KiprisPdfPath(path.get().trim(), docName));
    }

    private String operationUrl(PatentLookupProperties.Kipris kipris, String operationName) {
        String baseUrl = kipris.baseUrl() == null ? "" : kipris.baseUrl().replaceAll("/+$", "");
        String servicePath = kipris.servicePath() == null
                ? ""
                : kipris.servicePath().replaceAll("^/+", "").replaceAll("/+$", "");
        return "%s/%s/%s".formatted(baseUrl, servicePath, operationName);
    }

    private List<String> serviceKeys(PatentLookupProperties.Kipris kipris) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (kipris.serviceKey() != null && !kipris.serviceKey().isBlank()) {
            keys.add(kipris.serviceKey().trim());
        }
        if (kipris.serviceKeys() != null) {
            kipris.serviceKeys().stream()
                    .filter(key -> key != null && !key.isBlank())
                    .forEach(key -> keys.add(key.trim()));
        }
        return List.copyOf(keys);
    }

    private boolean isQuotaExhausted(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return false;
        }
        String normalizedBody = responseBody.toLowerCase();
        return QUOTA_EXHAUSTED_MARKERS.stream().anyMatch(normalizedBody::contains);
    }
}
