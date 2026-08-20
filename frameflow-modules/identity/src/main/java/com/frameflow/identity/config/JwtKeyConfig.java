package com.frameflow.identity.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Builds an RS256 current signer and a kid-addressed verification key ring.
 * Legacy single-key properties remain valid; rotated-out public keys can be kept
 * under {@code verification-keys} until every token they signed has expired.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtKeyConfig {

    private static final int MIN_RSA_BITS = 2048;

    private final JwtProperties props;

    public JwtKeyConfig(JwtProperties props) {
        this.props = props;
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        KeyRing ring = keyRing();
        RSAKey signer = new RSAKey.Builder(ring.currentPublic())
                .privateKey(ring.currentPrivate())
                .keyID(ring.currentKid())
                .algorithm(JWSAlgorithm.RS256)
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signer)));
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        KeyRing ring = keyRing();
        List<JWK> publicJwks = ring.verificationKeys().entrySet().stream()
                .map(entry -> (JWK) new RSAKey.Builder(entry.getValue())
                        .keyID(entry.getKey())
                        .algorithm(JWSAlgorithm.RS256)
                        .build())
                .toList();
        JWKSource<SecurityContext> source = new ImmutableJWKSet<>(new JWKSet(publicJwks));
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, source));

        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
        Set<String> acceptedKids = Set.copyOf(ring.verificationKeys().keySet());
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ofSeconds(props.getClockSkew().toSeconds())),
                new JwtIssuerValidator(props.getIssuer()),
                claim("audience", jwt -> jwt.getAudience() != null
                        && jwt.getAudience().contains(props.getAudience())),
                claim("kid", jwt -> acceptedKids.contains(jwt.getHeaders().get("kid"))),
                claim("alg", jwt -> "RS256".equals(jwt.getHeaders().get("alg")))));
        return decoder;
    }

    private OAuth2TokenValidator<Jwt> claim(String name, Predicate<Jwt> test) {
        return jwt -> test.test(jwt)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", name + " validation failed", null));
    }

    private KeyRing keyRing() {
        JwtProperties.SigningKey preferred = props.getCurrentSigningKey();
        boolean preferredConfigured = hasText(preferred.getKid())
                || hasText(preferred.getPublicKeyPem()) || hasText(preferred.getPrivateKeyPem());

        String currentKid = require(preferredConfigured ? preferred.getKid() : props.getKid(),
                preferredConfigured ? "frameflow.jwt.current-signing-key.kid" : "frameflow.jwt.kid");
        RSAPublicKey currentPublic = parsePublic(require(
                preferredConfigured ? preferred.getPublicKeyPem() : props.getPublicKeyPem(),
                preferredConfigured ? "frameflow.jwt.current-signing-key.public-key-pem"
                        : "frameflow.jwt.public-key-pem"));
        RSAPrivateKey currentPrivate = parsePrivate(require(
                preferredConfigured ? preferred.getPrivateKeyPem() : props.getPrivateKeyPem(),
                preferredConfigured ? "frameflow.jwt.current-signing-key.private-key-pem"
                        : "frameflow.jwt.private-key-pem"));
        requireStrong(currentKid, currentPublic);
        requireStrong(currentKid, currentPrivate);
        if (!currentPublic.getModulus().equals(currentPrivate.getModulus())) {
            throw new IllegalStateException("JWT current signing public/private keys do not form a pair for kid "
                    + currentKid);
        }

        Map<String, RSAPublicKey> verification = new LinkedHashMap<>();
        addVerification(verification, currentKid, currentPublic);

        // During migration to current-signing-key.*, legacy kid/public-key-pem can
        // remain configured and automatically become an accepted old verification key.
        if (preferredConfigured && hasText(props.getKid()) && hasText(props.getPublicKeyPem())) {
            RSAPublicKey legacyPublic = parsePublic(props.getPublicKeyPem());
            requireStrong(props.getKid(), legacyPublic);
            addVerification(verification, props.getKid(), legacyPublic);
        }
        for (JwtProperties.VerificationKey candidate : new ArrayList<>(props.getVerificationKeys())) {
            String kid = require(candidate.getKid(), "frameflow.jwt.verification-keys[].kid");
            RSAPublicKey publicKey = parsePublic(require(candidate.getPublicKeyPem(),
                    "frameflow.jwt.verification-keys[" + kid + "].public-key-pem"));
            requireStrong(kid, publicKey);
            addVerification(verification, kid, publicKey);
        }
        return new KeyRing(currentKid, currentPublic, currentPrivate, Map.copyOf(verification));
    }

    private static void addVerification(Map<String, RSAPublicKey> verification, String kid, RSAPublicKey key) {
        RSAPublicKey existing = verification.putIfAbsent(kid, key);
        if (existing != null && !existing.getModulus().equals(key.getModulus())) {
            throw new IllegalStateException("JWT kid " + kid + " is configured with multiple public keys");
        }
    }

    private static void requireStrong(String kid, java.security.interfaces.RSAKey key) {
        if (key.getModulus().bitLength() < MIN_RSA_BITS) {
            throw new IllegalStateException("JWT RSA key " + kid + " must be at least " + MIN_RSA_BITS
                    + " bits, got " + key.getModulus().bitLength());
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String require(String value, String name) {
        if (!hasText(value)) {
            throw new IllegalStateException("Missing required JWT key property " + name
                    + ". Inject it via environment/secret; keys must not be committed.");
        }
        return value;
    }

    private static RSAPublicKey parsePublic(String pem) {
        return (RSAPublicKey) parseKey(pem, true);
    }

    private static RSAPrivateKey parsePrivate(String pem) {
        return (RSAPrivateKey) parseKey(pem, false);
    }

    private static java.security.Key parseKey(String pem, boolean publicKey) {
        try {
            String body = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "")
                    .trim();
            byte[] der = Base64.getDecoder().decode(body);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            if (publicKey) {
                return factory.generatePublic(new X509EncodedKeySpec(der));
            }
            return factory.generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse injected JWT "
                    + (publicKey ? "public" : "private") + " key PEM", e);
        }
    }

    private record KeyRing(String currentKid, RSAPublicKey currentPublic, RSAPrivateKey currentPrivate,
                           Map<String, RSAPublicKey> verificationKeys) {
    }
}
