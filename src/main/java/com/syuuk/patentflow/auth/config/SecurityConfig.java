package com.syuuk.patentflow.auth.config;

import com.syuuk.patentflow.auth.security.CsrfCookieFilter;
import com.syuuk.patentflow.auth.security.JsonAccessDeniedHandler;
import com.syuuk.patentflow.auth.security.JwtAuthenticationFilter;
import com.syuuk.patentflow.auth.security.SpaCsrfTokenRequestHandler;
import com.syuuk.patentflow.auth.security.StableCsrfTokenRepository;
import com.syuuk.patentflow.common.ratelimit.RateLimitFilter;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    private final List<String> allowedOrigins;
    private final AuthProperties authProperties;
    // E2E/로컬 검증 전용(기본 true): false면 CSRF 보호를 끈다. 운영/데모에서는 절대 끄지 않는다.
    private final boolean csrfEnabled;

    public SecurityConfig(
            @Value("${patentflow.cors.allowed-origins}") String allowedOrigins,
            @Value("${patentflow.security.csrf-enabled:true}") boolean csrfEnabled,
            AuthProperties authProperties
    ) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();
        this.csrfEnabled = csrfEnabled;
        this.authProperties = authProperties;
    }

    /**
     * CSRF 토큰 쿠키(XSRF-TOKEN) 설정. 크로스 서브도메인(FE patentflow.live ↔ BE api.patentflow.live)
     * 환경에서 SPA가 JS로 쿠키를 읽을 수 있도록 상위 도메인을 지정한다(설정값이 있을 때).
     */
    private CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(builder -> {
            builder.secure(authProperties.isCookieSecure());
            builder.sameSite(authProperties.getCookieSameSite());
            String cookieDomain = authProperties.getCookieDomain();
            if (cookieDomain != null && !cookieDomain.isBlank()) {
                builder.domain(cookieDomain);
            }
        });
        // BE-14: 인증 시 토큰 회전(삭제·재발급)을 억제해 동시 요청 핑퐁(간헐 403)을 제거한다.
        return new StableCsrfTokenRepository(repository);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            JsonAccessDeniedHandler jsonAccessDeniedHandler,
            ObjectProvider<RateLimitFilter> rateLimitFilterProvider
    ) throws Exception {
        // P6 BE-RATELIMIT: 활성화된 경우에만 인증 처리 앞단에 레이트리밋 필터를 배치한다.
        RateLimitFilter rateLimitFilter = rateLimitFilterProvider.getIfAvailable();
        if (rateLimitFilter != null) {
            http.addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);
        }
        return http
            .csrf(csrf -> {
                if (!csrfEnabled) {
                    // E2E/로컬 전용 — 운영/데모에서는 기본값 true로 항상 켜진다.
                    csrf.disable();
                    return;
                }
                csrf
                    .csrfTokenRepository(csrfTokenRepository())
                    .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                    .ignoringRequestMatchers(
                            "/api/v1/auth/login",
                            "/api/v1/auth/refresh",
                            "/api/v1/auth/logout");
            })
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // BE-14: 403을 sendError 없이 직접 JSON으로 응답 — ERROR dispatch가 401로 바꿔치기하는
                // CSRF 핑퐁 차단. CsrfFilter도 이 핸들러를 사용한다(ExceptionHandlingConfigurer 공유).
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler(jsonAccessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        // BE-14: XSRF-TOKEN 쿠키 프라이밍용 no-op GET — 로그인 전에도 토큰을 받을 수 있어야 한다.
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                        // AUTH-06: 실제 매핑된 콜백은 /admin/settings/... 하나뿐. 컨트롤러가 없는
                        // orphan permitAll(/api/v1/settings/mail/oauth2/google/callback)을 제거해 미인증 표면을 축소한다.
                        .requestMatchers(
                                "/api/v1/admin/settings/mail/oauth2/google/callback").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/settings/review-quarters/active").hasAnyRole("ADMIN", "LEGAL", "BUSINESS")
                        .requestMatchers(HttpMethod.GET, "/api/v1/business/checklist-items").authenticated()
                        // I3: 역할 분리 — 검토 업무(특허·연차료·메일·legal 대시보드)는 ADMIN+LEGAL,
                        // 운영(admin 설정·계정/부서 관리·시스템 설정)은 ADMIN 전용으로 유지한다.
                        // FE-01: 부서 목록 조회는 LEGAL 검토 화면(담당부서 표시·메일 수신처)에서도 필요 —
                        // 읽기 전용 GET만 LEGAL에 허용한다(첫 매치 우선, 변형은 /admin/departments로 ADMIN 전용).
                        .requestMatchers(HttpMethod.GET, "/api/v1/departments").hasAnyRole("ADMIN", "LEGAL")
                        .requestMatchers("/api/v1/admin/**", "/api/v1/settings/**",
                                "/api/v1/departments/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/legal/**", "/api/v1/mailings/**").hasAnyRole("ADMIN", "LEGAL")
                        .requestMatchers("/api/v1/annual-fees/**").hasAnyRole("ADMIN", "LEGAL")
                        .requestMatchers("/api/v1/business/**").hasRole("BUSINESS")
                        .requestMatchers(HttpMethod.POST, "/api/v1/patents/*/business-submissions").hasRole("BUSINESS")
                        .requestMatchers(HttpMethod.GET, "/api/v1/patents/*/business-submissions").authenticated()
                        // FR-LEGAL-06/18: AI 레포트 재생성은 법무팀(ADMIN/LEGAL)과 사업부(BUSINESS) 모두 수행 가능.
                        // 재생성 요청과 진행상태 폴링 두 경로만 BUSINESS에 허용하고, 그 외 patents 쓰기/조회는 종전대로 제한한다.
                        .requestMatchers(HttpMethod.POST, "/api/v1/patents/*/request-ai-report").hasAnyRole("ADMIN", "LEGAL", "BUSINESS")
                        .requestMatchers(HttpMethod.GET, "/api/v1/patents/*/ai-report/status").hasAnyRole("ADMIN", "LEGAL", "BUSINESS")
                        .requestMatchers(HttpMethod.GET, "/api/v1/patents/**").hasAnyRole("ADMIN", "LEGAL")
                        .requestMatchers(HttpMethod.POST, "/api/v1/patents/**").hasAnyRole("ADMIN", "LEGAL")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/patents/**").hasAnyRole("ADMIN", "LEGAL")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/patents/**").hasAnyRole("ADMIN", "LEGAL")
                        // DELETE 규칙 부재 시 anyRequest().authenticated()로 흘러 BUSINESS도 통과하던 구멍을 막는다
                        // (레포트 편집 되돌리기 DELETE /api/v1/patents/*/ai-report/edits 추가에 맞춰 명시).
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/patents/**").hasAnyRole("ADMIN", "LEGAL")
                        // 메서드 미지정 캐치올: HEAD 등 위 메서드별 규칙을 비껴가는 요청이
                        // anyRequest().authenticated()로 흘러 BUSINESS가 ADMIN GET 핸들러를
                        // 실행할 수 있던 구멍 차단(Spring MVC는 HEAD를 GET 핸들러로 처리한다).
                        .requestMatchers("/api/v1/patents/**").hasAnyRole("ADMIN", "LEGAL")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new CsrfCookieFilter(), JwtAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter jwtAuthenticationFilter
    ) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(jwtAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }

    // P6 BE-RATELIMIT: 시큐리티 체인에서만 실행하도록 서블릿 자동 등록을 비활성화(이중 실행 방지).
    @Bean
    @ConditionalOnProperty(name = "patentflow.ratelimit.enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter rateLimitFilter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(rateLimitFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
