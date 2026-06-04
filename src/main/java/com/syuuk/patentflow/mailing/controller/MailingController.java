package com.syuuk.patentflow.mailing.controller;

import com.syuuk.patentflow.common.response.ApiResponse;
import com.syuuk.patentflow.mailing.dto.DepartmentRecipientMappingResponse;
import com.syuuk.patentflow.mailing.dto.DepartmentRecipientMappingRequest;
import com.syuuk.patentflow.mailing.dto.MailingHistoryItemResponse;
import com.syuuk.patentflow.mailing.dto.MailingSendRequest;
import com.syuuk.patentflow.mailing.dto.MailingSendResponse;
import com.syuuk.patentflow.mailing.service.MailingService;
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

    public MailingController(MailingService mailingService) {
        this.mailingService = mailingService;
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

    @GetMapping("/history")
    public ApiResponse<List<MailingHistoryItemResponse>> getMailingHistory(
            @RequestParam(required = false) String patentId,
            @RequestParam(required = false) String recipientEmail
    ) {
        return ApiResponse.ok(mailingService.getHistory(patentId, recipientEmail));
    }

    /**
     * @relatedFR FR-LEGAL-14
     * @relatedUI UI-LEGAL-05
     * @description 주소 오류/반송 확인 시 메일 이력을 재발송 필요로 표시하고 포함 특허를 메일 발송 대기로 되돌린다.
     */
    @PostMapping("/history/{mailingId}/retry-required")
    public ApiResponse<MailingSendResponse> markRetryRequired(@PathVariable String mailingId) {
        return ApiResponse.ok(mailingService.markRetryRequired(mailingId));
    }

}
