package com.frameflow.learning.identity.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class RsaKeyProviderTest {

    @Test
    void remote_mode_fails_closed_without_configured_key_pair() {
        assertThatThrownBy(() -> new RsaKeyProvider(properties("", "", false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已禁用临时 RSA 密钥");
    }

    @Test
    void configured_pair_is_stable_across_provider_instances() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        String privatePem = pem("PRIVATE KEY", pair.getPrivate().getEncoded());
        String publicPem = pem("PUBLIC KEY", pair.getPublic().getEncoded());

        var first = new RsaKeyProvider(properties(privatePem, publicPem, false));
        var second = new RsaKeyProvider(properties(privatePem, publicPem, false));

        assertThat(first.rsaKey().getKeyID()).isEqualTo(second.rsaKey().getKeyID());
        assertThat(first.rsaKey().toRSAPublicKey()).isEqualTo(second.rsaKey().toRSAPublicKey());
    }

    @Test
    void mismatched_public_and_private_keys_are_rejected() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var first = generator.generateKeyPair();
        var second = generator.generateKeyPair();

        assertThatThrownBy(() -> new RsaKeyProvider(properties(
                pem("PRIVATE KEY", first.getPrivate().getEncoded()),
                pem("PUBLIC KEY", second.getPublic().getEncoded()),
                false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不属于同一密钥对");
    }

    private SecurityProperties properties(String privatePem, String publicPem, boolean ephemeral) {
        return new SecurityProperties(
                Duration.ofMinutes(15), Duration.ofDays(14),
                privatePem, publicPem, "", "", ephemeral);
    }

    private String pem(String type, byte[] encoded) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded)
                + "\n-----END " + type + "-----\n";
    }
}
