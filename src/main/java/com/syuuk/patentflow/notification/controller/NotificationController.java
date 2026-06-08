package com.syuuk.patentflow.notification.controller;

import com.syuuk.patentflow.common.error.ErrorCode;
import com.syuuk.patentflow.common.error.PatentFlowException;
import com.syuuk.patentflow.common.response.ApiResponse;
import com.syuuk.patentflow.auth.dto.UserPrincipalResponse;
import com.syuuk.patentflow.notification.dto.NotificationReadStateRequest;
import com.syuuk.patentflow.notification.dto.NotificationResponse;
import com.syuuk.patentflow.notification.dto.NotificationUnreadCountResponse;
import com.syuuk.patentflow.notification.service.NotificationService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * @relatedFR COM-04
     * @relatedUI UI-COM-03
     * @description 역할 기반 알림 목록을 조회한다.
     */
    @GetMapping
    public ApiResponse<List<NotificationResponse>> getNotifications(
            @RequestParam(required = false, defaultValue = "COMMON") String role,
            Authentication authentication
    ) {
        String currentRole = currentRole(authentication);
        if (!"COMMON".equals(role) && !currentRole.equals(role)) {
            throw new PatentFlowException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.ok(notificationService.getNotifications(role, currentUserId(authentication)));
    }

    @GetMapping("/unread-count")
    public ApiResponse<NotificationUnreadCountResponse> unreadCount(
            @RequestParam(required = false, defaultValue = "COMMON") String role,
            Authentication authentication
    ) {
        String currentRole = currentRole(authentication);
        if (!"COMMON".equals(role) && !currentRole.equals(role)) {
            throw new PatentFlowException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.ok(new NotificationUnreadCountResponse(
                notificationService.unreadCount(role, currentUserId(authentication))));
    }

    /**
     * @relatedFR COM-05
     * @relatedUI UI-COM-03
     * @description 알림 읽음/읽지 않음 상태를 변경한다.
     */
    @PatchMapping("/{notificationId}/read-state")
    public ApiResponse<NotificationResponse> updateReadState(
            @PathVariable String notificationId,
            @RequestBody NotificationReadStateRequest request,
            Authentication authentication
    ) {
        return ApiResponse.ok(notificationService.updateReadState(
                notificationId, request.isRead(), currentRole(authentication), currentUserId(authentication)));
    }

    @PatchMapping("/read-all")
    public ApiResponse<Void> markAllRead(
            @RequestParam(required = false, defaultValue = "COMMON") String role,
            Authentication authentication
    ) {
        String currentRole = currentRole(authentication);
        if (!"COMMON".equals(role) && !currentRole.equals(role)) {
            throw new PatentFlowException(ErrorCode.UNAUTHORIZED);
        }
        notificationService.markAllRead(role, currentUserId(authentication));
        return ApiResponse.ok(null);
    }

    private String currentRole(Authentication authentication) {
        if (authentication == null) {
            throw new PatentFlowException(ErrorCode.UNAUTHORIZED);
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()))
                ? "ADMIN"
                : "BUSINESS";
    }

    private String currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipalResponse principal)) {
            throw new PatentFlowException(ErrorCode.UNAUTHORIZED);
        }
        return principal.userId();
    }
}
