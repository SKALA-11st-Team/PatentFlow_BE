package com.syuuk.patentflow.common.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * MAIL-08: system_settings에 평문 저장되던 민감 시크릿(앱 비밀번호·OAuth2 refresh_token·client_secret)을
 * 애플리케이션 레벨 AES-GCM으로 선택적 암호화한다.
 *
 * <ul>
 *   <li>키: env {@code PATENTFLOW_SETTINGS_ENC_KEY}(Base64 32바이트). 미설정 시 평문 폴백(로컬/개발 호환).</li>
 *   <li>저장 포맷: {@code enc:v1:} + Base64(IV(12) || ciphertext+tag). prefix 없는 값은 평문 레거시로 취급해
 *       무중단 마이그레이션을 지원한다.</li>
 *   <li>빈 값/널은 그대로 통과(설정됨 여부 판정용 blank 검사를 깨지 않기 위함).</li>
 * </ul>
 */
@Component
public class SecretCipher {

    private static final String PREFIX = "enc:v1:";
    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public SecretCipher(@Value("${patentflow.settings.enc-key:}") String base64Key) {
        this.key = (base64Key == null || base64Key.isBlank())
                ? null
                : new SecretKeySpec(Base64.getDecoder().decode(base64Key.trim()), "AES");
    }

    public boolean isEnabled() {
        return key != null;
    }

    /** 평문을 enc:v1: 포맷으로 암호화한다. 키 미설정·빈 값이면 입력을 그대로 반환한다. */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank() || key == null) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception exception) {
            throw new IllegalStateException("시크릿 암호화에 실패했습니다.", exception);
        }
    }

    /** enc:v1: 포맷이면 복호화, 아니면(평문 레거시·키 미설정) 입력을 그대로 반환한다. */
    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX) || key == null) {
            return stored;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(combined, IV_BYTES, combined.length);
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("시크릿 복호화에 실패했습니다.", exception);
        }
    }
}
