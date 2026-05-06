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
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;

@Component
public class KiprisPatentLookupClient implements ExternalPatentLookupClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final PatentLookupProperties properties;
    private final HttpClient httpClient;

    public KiprisPatentLookupClient(PatentLookupProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    @Override
    public Optional<PatentBibliographicInfoResponse> lookup(PatentLookupQuery query) {
        PatentLookupProperties.Kipris kipris = properties.kipris();
        if (!kipris.enabled() || kipris.serviceKey() == null || kipris.serviceKey().isBlank()) {
            return Optional.empty();
        }

        String applicationNumber = digitsOnly(query.applicationNumber());
        if (applicationNumber.isBlank()) {
            applicationNumber = digitsOnly(query.keyword());
        }
        if (applicationNumber.isBlank()) {
            return Optional.empty();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .GET()
                    .timeout(TIMEOUT)
                    .uri(URI.create("%s?applicationNumber=%s&ServiceKey=%s".formatted(
                            kipris.baseUrl(),
                            encode(applicationNumber),
                            encode(kipris.serviceKey()))))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body().isBlank()) {
                return Optional.empty();
            }
            return parseResponse(response.body(), query);
        } catch (Exception exception) {
            return Optional.empty();
        }
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
}
