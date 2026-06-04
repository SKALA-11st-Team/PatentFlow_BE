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
            Path storageBase = storageRoot.toAbsolutePath().normalize();
            Path patentDir = storageBase.resolve(safeSegment(patentId));
            Path reportPath = patentDir.resolve(safeSegment(reportId) + ".md").normalize();
            // Path Traversal 차단: 정규화 후 저장 루트를 벗어나는 경로(../ 등)는 거부한다.
            if (!reportPath.startsWith(storageBase)) {
                throw new IllegalArgumentException("저장 경로가 허용된 디렉토리를 벗어났습니다.");
            }
            Files.createDirectories(reportPath.getParent());
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
