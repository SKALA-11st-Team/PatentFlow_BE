/**
 * @author 유건욱
 * @date 2026-05-06
 */
package com.syuuk.patentflow.mailing.controller;

import com.syuuk.patentflow.common.response.ApiResponse;
import com.syuuk.patentflow.common.response.PageResponse;
import com.syuuk.patentflow.mailing.dto.DepartmentRecipientMappingResponse;
import com.syuuk.patentflow.mailing.dto.DepartmentRecipientMappingRequest;
import com.syuuk.patentflow.mailing.dto.MailingHistoryItemResponse;
import com.syuuk.patentflow.mailing.dto.MailingSendRequest;
import com.syuuk.patentflow.mailing.dto.MailingSendResponse;
import com.syuuk.patentflow.mailing.dto.PatentPdfLinkRequest;
import com.syuuk.patentflow.mailing.dto.PatentPdfLinkResponse;
import com.syuuk.patentflow.mailing.service.MailingService;
import com.syuuk.patentflow.patent.service.PatentPdfService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mailings")
public class MailingController {

    private final MailingService mailingService;
    private final PatentPdfService patentPdfService;

    public MailingController(MailingService mailingService, PatentPdfService patentPdfService) {
        this.mailingService = mailingService;
        this.patentPdfService = patentPdfService;
    }

    @GetMapping("/department-recipient-mappings")
    public ApiResponse<List<DepartmentRecipientMappingResponse>> getRecipientMappings(
            @RequestParam(required = false) String departmentId
    ) {
        return ApiResponse.ok(mailingService.getRecipientMappings(departmentId));
    }

    @PutMapping("/department-recipient-mappings/{departmentId}")
    public ApiResponse<DepartmentRecipientMappingResponse> updateRecipientMapping(
            @PathVariable String departmentId,
            @RequestBody DepartmentRecipientMappingRequest request
    ) {
        return ApiResponse.ok(mailingService.updateRecipientMapping(departmentId, request));
    }

    /**
     * @relatedFR FR-LEGAL-12, FR-LEGAL-13, FR-LEGAL-14
     * @relatedUI UI-LEGAL-05
     * @description 사업부 검토 요청 메일 발송 처리 API.
     */
    @PostMapping("/send")
    public ApiResponse<MailingSendResponse> sendMailing(@Valid @RequestBody MailingSendRequest request) {
        return ApiResponse.ok(mailingService.send(request));
    }

    /**
     * @relatedFR FR-LEGAL-13, FR-LEGAL-14
     * @relatedUI UI-LEGAL-05
     * @description MAIL-12: 메일 초안에 실을 특허 PDF 다운로드 링크 일괄 해석 API.
     * KR 특허는 KIPRIS 공개전문 PDF의 S3 presigned 링크, 그 외/실패는 원문 URL 폴백.
     */
    @PostMapping("/patent-pdf-links")
    public ApiResponse<List<PatentPdfLinkResponse>> resolvePatentPdfLinks(
            @Valid @RequestBody PatentPdfLinkRequest request
    ) {
        return ApiResponse.ok(patentPdfService.resolvePdfLinks(request.patentIds()));
    }

    @GetMapping("/history")
    public PageResponse<MailingHistoryItemResponse> getMailingHistory(
            @RequestParam(required = false) String patentId,
            @RequestParam(required = false) String recipientEmail,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return mailingService.getHistory(patentId, recipientEmail, page, size);
    }


}
