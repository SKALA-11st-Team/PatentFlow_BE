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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class GooglePatentsLookupClient implements ExternalPatentLookupClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final Pattern TITLE_PATTERN = Pattern.compile("<meta\\s+name=\"DC.title\"\\s+content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern PUBLICATION_PATTERN = Pattern.compile("<meta\\s+name=\"citation_patent_publication_number\"\\s+content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern APPLICATION_PATTERN = Pattern.compile("<meta\\s+name=\"citation_patent_application_number\"\\s+content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    private final PatentLookupProperties properties;
    private final HttpClient httpClient;

    public GooglePatentsLookupClient(PatentLookupProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public Optional<PatentBibliographicInfoResponse> lookup(PatentLookupQuery query) {
        PatentLookupProperties.GooglePatents googlePatents = properties.googlePatents();
        if (!googlePatents.enabled()) {
            return Optional.empty();
        }

        return patentPageCandidates(query).stream()
                .map(candidate -> fetchPatentPage(googlePatents.baseUrl(), candidate, query))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    private java.util.List<String> patentPageCandidates(PatentLookupQuery query) {
        String registration = digitsOnly(query.registrationNumber());
        String application = digitsOnly(query.applicationNumber());
        String keyword = digitsOnly(query.keyword());
        java.util.ArrayList<String> candidates = new java.util.ArrayList<>();
        if (!registration.isBlank()) {
            candidates.add("KR%sB1".formatted(registration));
            candidates.add("KR%s".formatted(registration));
        }
        if (!application.isBlank()) {
            candidates.add("KR%sA".formatted(application));
        }
        if (!keyword.isBlank()) {
            candidates.add(keyword);
        }
        return candidates.stream().distinct().toList();
    }

    private Optional<PatentBibliographicInfoResponse> fetchPatentPage(
            String baseUrl,
            String candidate,
            PatentLookupQuery query
    ) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .GET()
                    .timeout(TIMEOUT)
                    .uri(URI.create("%s/patent/%s/en".formatted(baseUrl, encodePath(candidate))))
                    .header("User-Agent", "PatentFlow-BE/0.1")
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }
            return parseHtml(response.body(), query);
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private Optional<PatentBibliographicInfoResponse> parseHtml(String html, PatentLookupQuery query) {
        String title = metaContent(TITLE_PATTERN, html).orElse(null);
        String publicationNumber = metaContent(PUBLICATION_PATTERN, html).orElse(query.registrationNumber());
        String applicationNumber = metaContent(APPLICATION_PATTERN, html).orElse(query.applicationNumber());
        if (title == null && publicationNumber == null && applicationNumber == null) {
            return Optional.empty();
        }

        return Optional.of(new PatentBibliographicInfoResponse(
                query.keyword(),
                title,
                null,
                "정보 부족 있음",
                query.country() == null || query.country().isBlank() ? "KR" : query.country(),
                null,
                applicationNumber,
                publicationNumber,
                null,
                "GOOGLE_PATENTS"));
    }

    private Optional<String> metaContent(Pattern pattern, String html) {
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return Optional.of(htmlDecode(matcher.group(1).trim()));
        }
        return Optional.empty();
    }

    private String htmlDecode(String value) {
        return value.replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&#39;", "'");
    }

    private String digitsOnly(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^0-9]", "");
    }

    private String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
