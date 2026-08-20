package com.frameflow.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.frameflow.identity.config.JwtKeyConfig;
import com.frameflow.identity.config.JwtProperties;
import com.frameflow.identity.support.TestJwtKeys;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtKeyRotationTest {

    @Test
    void currentSignerAndOldVerificationKeyAreSelectedByKid() throws Exception {
        KeyPair old = generate(2048);
        JwtProperties props = new JwtProperties();
        props.getCurrentSigningKey().setKid("current-kid");
        props.getCurrentSigningKey().setPublicKeyPem(TestJwtKeys.publicPem());
        props.getCurrentSigningKey().setPrivateKeyPem(TestJwtKeys.privatePem());
        JwtProperties.VerificationKey oldVerification = new JwtProperties.VerificationKey();
        oldVerification.setKid("old-kid");
        oldVerification.setPublicKeyPem(publicPem(old));
        props.setVerificationKeys(List.of(oldVerification));

        JwtKeyConfig config = new JwtKeyConfig(props);
        String current = config.jwtEncoder().encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId("current-kid").type("JWT").build(),
                claims("1001"))).getTokenValue();
        String oldToken = encoder(old, "old-kid").encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId("old-kid").type("JWT").build(),
                claims("1002"))).getTokenValue();

        assertThat(config.jwtDecoder().decode(current).getSubject()).isEqualTo("1001");
        assertThat(config.jwtDecoder().decode(oldToken).getSubject()).isEqualTo("1002");
    }

    @Test
    void legacySingleKeyPropertiesRemainSupported() {
        JwtProperties props = new JwtProperties();
        props.setKid(TestJwtKeys.KID);
        props.setPublicKeyPem(TestJwtKeys.publicPem());
        props.setPrivateKeyPem(TestJwtKeys.privatePem());

        JwtKeyConfig config = new JwtKeyConfig(props);
        String token = config.jwtEncoder().encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId(TestJwtKeys.KID).type("JWT").build(),
                claims("2001"))).getTokenValue();
        assertThat(config.jwtDecoder().decode(token).getSubject()).isEqualTo("2001");
    }

    @Test
    void rsaKeysBelow2048BitsFailFast() throws Exception {
        KeyPair weak = generate(1024);
        JwtProperties props = new JwtProperties();
        props.setKid("weak-kid");
        props.setPublicKeyPem(publicPem(weak));
        props.setPrivateKeyPem(privatePem(weak));

        assertThatThrownBy(() -> new JwtKeyConfig(props).jwtEncoder())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 2048 bits");
    }

    private static JwtClaimsSet claims(String subject) {
        Instant now = Instant.now();
        return JwtClaimsSet.builder()
                .issuer("frameflow")
                .audience(List.of("frameflow-api"))
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(900))
                .build();
    }

    private static NimbusJwtEncoder encoder(KeyPair pair, String kid) {
        RSAKey key = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate())
                .keyID(kid)
                .algorithm(JWSAlgorithm.RS256)
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }

    private static KeyPair generate(int bits) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(bits);
        return generator.generateKeyPair();
    }

    private static String publicPem(KeyPair pair) {
        return pem("PUBLIC KEY", pair.getPublic().getEncoded());
    }

    private static String privatePem(KeyPair pair) {
        return pem("PRIVATE KEY", pair.getPrivate().getEncoded());
    }

    private static String pem(String label, byte[] der) {
        return "-----BEGIN " + label + "-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der)
                + "\n-----END " + label + "-----\n";
    }
}
