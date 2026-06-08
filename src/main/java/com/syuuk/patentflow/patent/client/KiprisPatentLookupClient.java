package com.syuuk.patentflow.patent.client;

import com.syuuk.patentflow.patent.config.PatentLookupProperties;
import com.syuuk.patentflow.patent.dto.PatentBibliographicInfoResponse;
import com.syuuk.patentflow.patent.dto.PatentLookupStatus;
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

@Component
public class KiprisPatentLookupClient implements ExternalPatentLookupClient {

    private static final Logger log = LoggerFactory.getLogger(KiprisPatentLookupClient.class);
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
            throw new PatentLookupException("KIPRIS", "KIPRIS service key is not configured");
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
                KiprisResponse detailResponse = request(kipris, kipris.bibliographyOperation(), applicationNumber, serviceKey);
                if (detailResponse.quotaExhausted()) {
                    exhaustedKeys.put(serviceKey, currentMonth);
                    continue;
                }
                if (!detailResponse.success()) {
                    return Optional.empty();
                }

                Optional<PatentBibliographicInfoResponse> detailResult = parseResponse(detailResponse.body(), query);
                if (detailResult.isPresent()) {
                    return detailResult;
                }
                if (kipris.applicationSearchOperation() == null || kipris.applicationSearchOperation().isBlank()) {
                    return Optional.empty();
                }

                KiprisResponse searchResponse = request(kipris, kipris.applicationSearchOperation(), applicationNumber, serviceKey);
                if (searchResponse.quotaExhausted()) {
                    exhaustedKeys.put(serviceKey, currentMonth);
                    continue;
                }
                if (!searchResponse.success()) {
                    return Optional.empty();
                }
                return parseResponse(searchResponse.body(), query);
            } catch (PatentLookupException exception) {
                throw exception;
            } catch (Exception exception) {
                log.warn("KIPRIS lookup failed. operation applicationNumber={}, keyword={}",
                        applicationNumber, query.keyword(), exception);
                throw new PatentLookupException("KIPRIS", "KIPRIS lookup failed", exception);
            }
        }
        return Optional.empty();
    }

    private Optional<PatentBibliographicInfoResponse> parseResponse(String xml, PatentLookupQuery query) {
        Document document = PatentLookupXmlParser.parse(xml);
        String resultCode = PatentLookupXmlParser.text(document, "resultCode").orElse("");
        if (!resultCode.isBlank() && !"00".equals(resultCode)) {
            return Optional.empty();
        }
        String title = PatentLookupXmlParser.text(
                document,
                "inventionTitle",
                "inventionName",
                "title",
                "astrtCont")
                .orElse(null);
        Optional<String> parsedApplicationNumber = PatentLookupXmlParser.text(document, "applicationNumber", "applNo");
        Optional<String> parsedRegistrationNumber = PatentLookupXmlParser.text(document, "registrationNumber", "registerNumber", "regNo");
        if (title == null && parsedApplicationNumber.isEmpty() && parsedRegistrationNumber.isEmpty()) {
            return Optional.empty();
        }
        String applicationNumber = parsedApplicationNumber.orElse(query.applicationNumber());
        String registrationNumber = parsedRegistrationNumber.orElse(query.registrationNumber());

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
                "KIPRIS",
                PatentLookupStatus.FOUND,
                "HIGH",
                null));
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

    private KiprisResponse request(
            PatentLookupProperties.Kipris kipris,
            String operationName,
            String applicationNumber,
            String serviceKey
    ) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .timeout(TIMEOUT)
                .uri(URI.create("%s?applicationNumber=%s&ServiceKey=%s".formatted(
                        operationUrl(kipris, operationName),
                        encode(applicationNumber),
                        encode(serviceKey))))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String body = response.body();
        boolean quotaExhausted = isQuotaExhausted(body);
        boolean success = response.statusCode() >= 200
                && response.statusCode() < 300
                && body != null
                && !body.isBlank()
                && !quotaExhausted;
        return new KiprisResponse(success, quotaExhausted, body);
    }

    private String operationUrl(PatentLookupProperties.Kipris kipris, String operationName) {
        String baseUrl = trimTrailingSlash(kipris.baseUrl());
        if (baseUrl.endsWith("/" + operationName)) {
            return baseUrl;
        }
        if (baseUrl.contains("/kipo-api/") || baseUrl.contains("/openapi/rest/")) {
            int lastSlashIndex = baseUrl.lastIndexOf('/');
            String serviceUrl = lastSlashIndex > 0 ? baseUrl.substring(0, lastSlashIndex) : baseUrl;
            return "%s/%s".formatted(serviceUrl, operationName);
        }
        return "%s/%s/%s".formatted(
                baseUrl,
                trimSlashes(kipris.servicePath()),
                operationName);
    }

    private String trimTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private String trimSlashes(String value) {
        return value == null ? "" : value.replaceAll("^/+", "").replaceAll("/+$", "");
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

    private record KiprisResponse(boolean success, boolean quotaExhausted, String body) {
    }
}
