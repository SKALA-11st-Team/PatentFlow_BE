package com.syuuk.patentflow.mailing.controller;

import com.syuuk.patentflow.common.response.ApiResponse;
import com.syuuk.patentflow.mailing.dto.MailingSendRequest;
import com.syuuk.patentflow.mailing.dto.MailingSendResponse;
import com.syuuk.patentflow.patent.service.PatentFixtureService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mailings")
public class MailingController {

    private final PatentFixtureService patentFixtureService;

    public MailingController(PatentFixtureService patentFixtureService) {
        this.patentFixtureService = patentFixtureService;
    }

    /**
     * @relatedFR FR-014, FR-015, FR-016
     * @relatedUI UI-007
     * @description 사업부 검토 요청 메일 발송 처리 API.
     */
    @PostMapping("/send")
    public ApiResponse<MailingSendResponse> sendMailing(@Valid @RequestBody MailingSendRequest request) {
        List<String> updatedPatentIds = patentFixtureService.markMailingSent(request.patentIds());
        return ApiResponse.ok(new MailingSendResponse(updatedPatentIds.size(), updatedPatentIds));
    }
}
