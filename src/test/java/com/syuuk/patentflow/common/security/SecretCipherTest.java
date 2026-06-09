package com.syuuk.patentflow.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * MAIL-08: AES-GCM 시크릿 암복호화 — 라운드트립·평문 폴백·레거시 평문 복호화 통과.
 */
class SecretCipherTest {

    private static final String KEY_32 = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void encryptThenDecryptRoundTripsWithKey() {
        SecretCipher cipher = new SecretCipher(KEY_32);

        String encrypted = cipher.encrypt("refresh-token-value");

        assertThat(encrypted).startsWith("enc:v1:").isNotEqualTo("refresh-token-value");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("refresh-token-value");
        assertThat(cipher.isEnabled()).isTrue();
    }

    @Test
    void encryptIsNonDeterministicButDecryptsToSame() {
        SecretCipher cipher = new SecretCipher(KEY_32);

        String first = cipher.encrypt("secret");
        String second = cipher.encrypt("secret");

        // 임의 IV로 매번 다른 암호문 — 그러나 둘 다 동일 평문으로 복호화된다.
        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo("secret");
        assertThat(cipher.decrypt(second)).isEqualTo("secret");
    }

    @Test
    void blankAndNullPassThroughUnchanged() {
        SecretCipher cipher = new SecretCipher(KEY_32);

        assertThat(cipher.encrypt("")).isEqualTo("");
        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.decrypt(null)).isNull();
    }

    @Test
    void noKeyMeansPlaintextPassThrough() {
        SecretCipher cipher = new SecretCipher("");

        assertThat(cipher.isEnabled()).isFalse();
        assertThat(cipher.encrypt("plain")).isEqualTo("plain");
        assertThat(cipher.decrypt("plain")).isEqualTo("plain");
    }

    @Test
    void legacyPlaintextWithoutPrefixDecryptsToItself() {
        SecretCipher cipher = new SecretCipher(KEY_32);

        // enc:v1: prefix가 없는 레거시 평문은 복호화 시 그대로 통과(무중단 마이그레이션).
        assertThat(cipher.decrypt("legacy-plaintext")).isEqualTo("legacy-plaintext");
    }
}
