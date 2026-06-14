package com.syuuk.patentflow.invitation.controller;

import com.syuuk.patentflow.common.response.ApiResponse;
import com.syuuk.patentflow.invitation.dto.InvitationAcceptRequest;
import com.syuuk.patentflow.invitation.dto.InvitationValidationResponse;
import com.syuuk.patentflow.invitation.service.InvitationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @relatedFR FR-LEGAL-12
 * @relatedUI UI-LEGAL-08
 * @description 공개 초대 토큰 검증·수락. 인증 불필요(SecurityConfig에서 permitAll 화이트리스트).
 */
@RestController
@RequestMapping("/api/v1/invitations")
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @GetMapping("/{token}")
    public ApiResponse<InvitationValidationResponse> validate(@PathVariable String token) {
        InvitationService.InvitationValidation validation = invitationService.validate(token);
        return ApiResponse.ok(new InvitationValidationResponse(
                validation.valid(),
                validation.status().name(),
                validation.email(),
                validation.responseDeadline()));
    }

    @PostMapping("/accept")
    public ApiResponse<Void> accept(@Valid @RequestBody InvitationAcceptRequest request) {
        invitationService.accept(request.token(), request.newPassword());
        return ApiResponse.ok(null);
    }
}
