package com.syuuk.patentflow.notification.controller;

import com.syuuk.patentflow.common.response.ApiResponse;
import com.syuuk.patentflow.notification.dto.NotificationReadStateRequest;
import com.syuuk.patentflow.notification.dto.NotificationResponse;
import com.syuuk.patentflow.notification.service.NotificationService;
import java.util.List;
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
            @RequestParam(required = false, defaultValue = "COMMON") String role
    ) {
        return ApiResponse.ok(notificationService.getNotifications(role));
    }

    /**
     * @relatedFR COM-05
     * @relatedUI UI-COM-03
     * @description 알림 읽음/읽지 않음 상태를 변경한다.
     */
    @PatchMapping("/{notificationId}/read-state")
    public ApiResponse<NotificationResponse> updateReadState(
            @PathVariable String notificationId,
            @RequestBody NotificationReadStateRequest request
    ) {
        return ApiResponse.ok(notificationService.updateReadState(notificationId, request.isRead()));
    }
}
