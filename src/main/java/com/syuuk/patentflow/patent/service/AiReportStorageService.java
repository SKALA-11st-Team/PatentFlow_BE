package com.syuuk.patentflow.patent.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AiReportStorageService {

    private final Path storageRoot;

    public AiReportStorageService(
            @Value("${patentflow.ai-report.storage-dir:storage/ai-reports}") String storageDir
    ) {
        this.storageRoot = Path.of(storageDir);
    }

    public String storeMarkdown(String patentId, String reportId, String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return null;
        }

        try {
            Path patentDir = storageRoot.resolve(safeSegment(patentId));
            Files.createDirectories(patentDir);
            Path reportPath = patentDir.resolve(safeSegment(reportId) + ".md");
            Files.writeString(reportPath, markdown, StandardCharsets.UTF_8);
            return reportPath.toString();
        } catch (IOException exception) {
            throw new IllegalStateException("AI 평가 레포트 markdown 파일을 저장할 수 없습니다.", exception);
        }
    }

    private String safeSegment(String value) {
        String normalized = value == null ? "unknown" : value.trim().toLowerCase(Locale.ROOT);
        String safe = normalized.replaceAll("[^a-z0-9._-]", "-");
        return safe.isBlank() ? "unknown" : safe;
    }
}
