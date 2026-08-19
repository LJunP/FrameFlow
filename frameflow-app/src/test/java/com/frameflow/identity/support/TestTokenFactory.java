package com.frameflow.identity.support;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;

/** Builds deliberately malformed tokens for negative JWT validation tests. */
public final class TestTokenFactory {

    private TestTokenFactory() {
    }

    /**
     * Correctly RS256-signed token with overridable claims/header (e.g. wrong iss/aud/kid/exp).
     *
     * <p>The signing key adopts the final header's kid so the encoder can select it; a
     * {@code h -> h.keyId(...)} customizer therefore produces a token that is genuinely signed
     * with that kid (which then fails the server's strict kid validator when it differs).
     */
    public static String rs256Token(String subject, long ttlMinutes,
                                    Consumer<JwtClaimsSet.Builder> claimsCustomizer,
                                    Consumer<JwsHeader.Builder> headerCustomizer) {
        JwsHeader.Builder header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(TestJwtKeys.KID).type("JWT");
        if (headerCustomizer != null) {
            headerCustomizer.accept(header);
        }
        JwsHeader finalHeader = header.build();
        RSAKey jwk = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) TestJwtKeys.keyPair().getPublic())
                .privateKey((java.security.interfaces.RSAPrivateKey) TestJwtKeys.keyPair().getPrivate())
                .keyID(finalHeader.getKeyId())
                .algorithm(JWSAlgorithm.RS256)
                .build();
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("frameflow")
                .subject(subject)
                .audience(List.of("frameflow-api"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ttlMinutes * 60))
                .id(UUID.randomUUID().toString());
        if (claimsCustomizer != null) {
            claimsCustomizer.accept(claims);
        }
        return encoder.encode(JwtEncoderParameters.from(finalHeader, claims.build())).getTokenValue();
    }

    /** PS256-signed token (wrong algorithm for our RS256-only decoder/validator). */
    public static String nonRs256Token(String subject) {
        RSAKey jwk = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) TestJwtKeys.keyPair().getPublic())
                .privateKey((java.security.interfaces.RSAPrivateKey) TestJwtKeys.keyPair().getPrivate())
                .keyID("ps256-kid")
                .algorithm(JWSAlgorithm.PS256)
                .build();
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("frameflow")
                .subject(subject)
                .audience(List.of("frameflow-api"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(900))
                .id(UUID.randomUUID().toString())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.PS256).keyId("ps256-kid").type("JWT").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
