package com.syuuk.patentflow.auth.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syuuk.patentflow.auth.config.AuthProperties;
import com.syuuk.patentflow.auth.dto.UserPrincipalResponse;
import com.syuuk.patentflow.user.domain.UserEntity;
import com.syuuk.patentflow.user.security.UserDetailsImpl;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    private final AuthProperties properties;
    private final ObjectMapper objectMapper;

    public JwtTokenProvider(AuthProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String createToken(UserDetails userDetails) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(properties.getAccessTokenExpirationSeconds());
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        UserPrincipalResponse principal = toPrincipalResponse(userDetails, roles);

        return createToken(principal, issuedAt, expiresAt);
    }

    public Instant getExpiresAt(String token) {
        return Instant.ofEpochSecond(((Number) claims(token).get("exp")).longValue());
    }

    // sub = email (로그인 ID) — Spring Security의 username 개념과 동일
    public String getUsername(String token) {
        return (String) claims(token).get("sub");
    }

    public List<String> getRoles(String token) {
        Object roles = claims(token).get("roles");
        if (roles instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    public UserPrincipalResponse getUserPrincipal(String token) {
        Map<String, Object> claims = claims(token);
        String email = stringClaim(claims, "sub", "");
        List<String> roles = getRoles(token);
        return new UserPrincipalResponse(
                email,
                stringClaim(claims, "username", email),
                roles,
                stringClaim(claims, "userId", email),
                stringClaim(claims, "role", roles.stream().anyMatch("ROLE_ADMIN"::equals) ? "ADMIN" : "BUSINESS"),
                stringClaim(claims, "departmentId", null),
                stringClaim(claims, "departmentName", null));
    }

    public boolean isValid(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return false;
            }
            String signedContent = parts[0] + "." + parts[1];
            // MessageDigest.isEqual — 타이밍 어택 방지를 위한 상수 시간 비교
            if (!MessageDigest.isEqual(sign(signedContent).getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
                return false;
            }
            Instant expiresAt = Instant.ofEpochSecond(((Number) claims(token).get("exp")).longValue());
            return expiresAt.isAfter(Instant.now());
        } catch (Exception exception) {
            return false;
        }
    }

    private String createToken(UserPrincipalResponse principal, Instant issuedAt, Instant expiresAt) {
        try {
            String header = encodeJson(Map.of("alg", "HS256", "typ", "JWT"));
            String payload = encodeJson(Map.ofEntries(
                    Map.entry("sub", principal.email()),      // sub = 로그인 ID (email)
                    Map.entry("roles", principal.roles()),
                    Map.entry("userId", principal.userId()),
                    Map.entry("username", principal.username()), // 실제 이름
                    Map.entry("email", principal.email()),
                    Map.entry("role", principal.role()),
                    Map.entry("departmentId", valueOrEmpty(principal.departmentId())),
                    Map.entry("departmentName", valueOrEmpty(principal.departmentName())),
                    Map.entry("jti", UUID.randomUUID().toString()),
                    Map.entry("iat", issuedAt.getEpochSecond()),
                    Map.entry("exp", expiresAt.getEpochSecond())));
            String signedContent = header + "." + payload;
            return signedContent + "." + sign(signedContent);
        } catch (Exception exception) {
            throw new IllegalStateException("JWT 토큰을 생성할 수 없습니다.", exception);
        }
    }

    private UserPrincipalResponse toPrincipalResponse(UserDetails userDetails, List<String> roles) {
        if (userDetails instanceof UserDetailsImpl impl) {
            UserEntity user = impl.getUser();
            return new UserPrincipalResponse(
                    user.getEmail(), user.getUsername(), roles,
                    user.getId(), user.getRole(),
                    user.getDepartmentId(), user.getDepartmentName());
        }
        String email = userDetails.getUsername();
        return new UserPrincipalResponse(email, email, roles, email, "BUSINESS", null, null);
    }

    private String stringClaim(Map<String, Object> claims, String key, String defaultValue) {
        Object value = claims.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        return String.valueOf(value);
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private Map<String, Object> claims(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid JWT token");
            }
            byte[] decodedPayload = BASE64_URL_DECODER.decode(parts[1]);
            return objectMapper.readValue(decodedPayload, new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw new IllegalArgumentException("JWT 토큰을 읽을 수 없습니다.", exception);
        }
    }

    private String encodeJson(Map<String, Object> value) throws Exception {
        return BASE64_URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
    }

    private String sign(String value) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(properties.getJwtSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
        return BASE64_URL_ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
}
