package com.syuuk.patentflow.notification.controller;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.syuuk.patentflow.auth.dto.UserPrincipalResponse;
import com.syuuk.patentflow.notification.domain.NotificationEntity;
import com.syuuk.patentflow.notification.repository.NotificationRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * NotificationController 역할 매핑 회귀 가드. xcomp-api-contract-1:
 * LEGAL 사용자는 검토 업무에서 ADMIN과 동일한 1급 역할이므로 role=ADMIN 알림 조회가
 * UNAUTHORIZED가 아니라 ADMIN 대상 알림으로 정상 조회되어야 한다.
 */
@SpringBootTest(properties = {
        "patentflow.lookup.google-patents.enabled=false"
})
@AutoConfigureMockMvc(addFilters = false)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void seedDeterministicNotifications() {
        notificationRepository.deleteAll();
        notificationRepository.saveAll(List.of(
                notification("NOTIF-TEST-ADMIN", "관리자 검토 알림", "ADMIN"),
                notification("NOTIF-TEST-BUSINESS", "사업부 알림", "BUSINESS"),
                notification("NOTIF-TEST-COMMON", "공통 알림", "COMMON")));
    }

    private static NotificationEntity notification(String id, String title, String targetRole) {
        return new NotificationEntity(id, title, "m", targetRole, OffsetDateTime.now(), null);
    }

    private RequestPostProcessor auth(String role) {
        UserPrincipalResponse principal = new UserPrincipalResponse(
                role.toLowerCase() + "@syuuk.test",
                role + " 사용자",
                List.of("ROLE_" + role),
                "USER-" + role,
                role,
                null,
                null);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        return request -> {
            request.setUserPrincipal(authentication);
            return request;
        };
    }

    @Test
    void legalUserCanLoadAdminNotifications() throws Exception {
        mockMvc.perform(get("/api/v1/notifications").param("role", "ADMIN").with(auth("LEGAL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].targetRole", everyItem(is(in(List.of("ADMIN", "COMMON"))))))
                .andExpect(jsonPath("$.data[*].title", hasItem("관리자 검토 알림")))
                .andExpect(jsonPath("$.data[*].title", hasItem("공통 알림")));
    }

    @Test
    void legalUserUnreadCountForAdminScopeIsAuthorized() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/unread-count").param("role", "ADMIN").with(auth("LEGAL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(2));
    }

    @Test
    void businessUserCannotLoadAdminNotifications() throws Exception {
        mockMvc.perform(get("/api/v1/notifications").param("role", "ADMIN").with(auth("BUSINESS")))
                .andExpect(status().isUnauthorized());
    }
}
