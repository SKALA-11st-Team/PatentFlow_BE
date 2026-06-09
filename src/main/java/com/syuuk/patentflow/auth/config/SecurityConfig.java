package com.syuuk.patentflow.auth.config;

import com.syuuk.patentflow.auth.security.CsrfCookieFilter;
import com.syuuk.patentflow.auth.security.JwtAuthenticationFilter;
import com.syuuk.patentflow.auth.security.SpaCsrfTokenRequestHandler;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    private final List<String> allowedOrigins;
    private final AuthProperties authProperties;

    public SecurityConfig(
            @Value("${patentflow.cors.allowed-origins}") String allowedOrigins,
            AuthProperties authProperties
    ) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();
        this.authProperties = authProperties;
    }

    /**
     * CSRF 토큰 쿠키(XSRF-TOKEN) 설정. 크로스 서브도메인(FE patentflow.live ↔ BE api.patentflow.live)
     * 환경에서 SPA가 JS로 쿠키를 읽을 수 있도록 상위 도메인을 지정한다(설정값이 있을 때).
     */
    private CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(builder -> {
            builder.secure(authProperties.isCookieSecure());
            builder.sameSite(authProperties.getCookieSameSite());
            String cookieDomain = authProperties.getCookieDomain();
            if (cookieDomain != null && !cookieDomain.isBlank()) {
                builder.domain(cookieDomain);
            }
        });
        return repository;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            ObjectProvider<RateLimitFilter> rateLimitFilterProvider
    ) throws Exception {
        // P6 BE-RATELIMIT: 활성화된 경우에만 인증 처리 앞단에 레이트리밋 필터를 배치한다.
        RateLimitFilter rateLimitFilter = rateLimitFilterProvider.getIfAvailable();
        if (rateLimitFilter != null) {
            http.addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);
        }
        return http
            .csrf(csrf -> csrf
                    .csrfTokenRepository(csrfTokenRepository())
                    .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                    .ignoringRequestMatchers(
                            "/api/v1/auth/login",
                            "/api/v1/auth/refresh",
                            "/api/v1/auth/logout"))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        // AUTH-06: 실제 매핑된 콜백은 /admin/settings/... 하나뿐. 컨트롤러가 없는
                        // orphan permitAll(/api/v1/settings/mail/oauth2/google/callback)을 제거해 미인증 표면을 축소한다.
                        .requestMatchers(
                                "/api/v1/admin/settings/mail/oauth2/google/callback").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/**", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/settings/review-quarters/active").hasAnyRole("ADMIN", "BUSINESS")
                        .requestMatchers(HttpMethod.GET, "/api/v1/business/checklist-items").authenticated()
                        .requestMatchers("/api/v1/admin/**", "/api/v1/legal/**", "/api/v1/settings/**",
                                "/api/v1/mailings/**", "/api/v1/departments/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/annual-fees/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/business/**").hasRole("BUSINESS")
                        .requestMatchers(HttpMethod.POST, "/api/v1/patents/*/business-submissions").hasRole("BUSINESS")
                        .requestMatchers(HttpMethod.GET, "/api/v1/patents/*/business-submissions").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/patents/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/patents/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/patents/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/patents/**").hasRole("ADMIN")
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
