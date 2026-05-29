package com.syuuk.patentflow.patent.client;

import com.syuuk.patentflow.patent.config.PatentLookupProperties;
import com.syuuk.patentflow.patent.dto.PatentBibliographicInfoResponse;
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
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;

@Component
public class KiprisPatentLookupClient implements ExternalPatentLookupClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final List<String> QUOTA_EXHAUSTED_MARKERS = List.of(
            "quota",
            "limit",
            "exceed",
            "exceeded",
            "too many",
            "일일",
            "월간",
            "초과",
            "한도",
            "제한");

    private final PatentLookupProperties properties;
    private final HttpClient httpClient;
    private final Map<String, YearMonth> exhaustedKeys = new ConcurrentHashMap<>();

    public KiprisPatentLookupClient(PatentLookupProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    @Override
    public Optional<PatentBibliographicInfoResponse> lookup(PatentLookupQuery query) {
        PatentLookupProperties.Kipris kipris = properties.kipris();
        List<String> serviceKeys = serviceKeys(kipris);
        if (serviceKeys.isEmpty()) {
            return Optional.empty();
        }

        String applicationNumber = digitsOnly(query.applicationNumber());
        if (applicationNumber.isBlank()) {
            applicationNumber = digitsOnly(query.keyword());
        }
        if (applicationNumber.isBlank()) {
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
                                kipris.baseUrl(),
                                encode(applicationNumber),
                                encode(serviceKey))))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                String body = response.body();
                if (response.statusCode() < 200 || response.statusCode() >= 300 || body == null || body.isBlank()) {
                    if (isQuotaExhausted(body)) {
                        exhaustedKeys.put(serviceKey, currentMonth);
                        continue;
                    }
                    return Optional.empty();
                }
                if (isQuotaExhausted(body)) {
                    exhaustedKeys.put(serviceKey, currentMonth);
                    continue;
                }
                return parseResponse(body, query);
            } catch (Exception exception) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private Optional<PatentBibliographicInfoResponse> parseResponse(String xml, PatentLookupQuery query) {
        Document document = PatentLookupXmlParser.parse(xml);
        String title = PatentLookupXmlParser.text(
                document,
                "inventionTitle",
                "inventionName",
                "title",
                "astrtCont")
                .orElse(null);
        String applicationNumber = PatentLookupXmlParser.text(document, "applicationNumber", "applNo")
                .orElse(query.applicationNumber());
        String registrationNumber = PatentLookupXmlParser.text(document, "registrationNumber", "registerNumber", "regNo")
                .orElse(query.registrationNumber());
        if (title == null && applicationNumber == null && registrationNumber == null) {
            return Optional.empty();
        }

        return Optional.of(new PatentBibliographicInfoResponse(
                query.keyword(),
                title,
                PatentLookupXmlParser.date(document, "applicationDate", "applDate"),
                PatentLookupXmlParser.text(document, "applicantName", "applicant", "applicantNames").orElse("정보 부족 있음"),
                "KR",
                PatentLookupXmlParser.date(document, "registrationDate", "registerDate", "regDate"),
                applicationNumber,
                registrationNumber,
                null,
                "KIPRIS"));
    }

    private String digitsOnly(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^0-9]", "");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private List<String> serviceKeys(PatentLookupProperties.Kipris kipris) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        addKey(keys, kipris.serviceKey());
        if (kipris.serviceKeys() != null) {
            kipris.serviceKeys().forEach(key -> addKey(keys, key));
        }
        return List.copyOf(keys);
    }

    private void addKey(LinkedHashSet<String> keys, String key) {
        if (key != null && !key.isBlank()) {
            keys.add(key.trim());
        }
    }

    private boolean isQuotaExhausted(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return false;
        }
        String normalizedBody = responseBody.toLowerCase();
        return QUOTA_EXHAUSTED_MARKERS.stream().anyMatch(normalizedBody::contains);
    }
}
