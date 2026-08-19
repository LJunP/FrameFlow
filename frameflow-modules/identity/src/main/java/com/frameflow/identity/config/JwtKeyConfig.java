package com.frameflow.identity.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
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
 * Builds the RS256 signer/verifier from injected PEM keys. Fails fast when keys
 * are missing (security over convenience). All environments use RS256; the JWT
 * header carries alg=RS256 and a non-empty kid (token-contract.md §2).
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtKeyConfig {

    private final JwtProperties props;

    public JwtKeyConfig(JwtProperties props) {
        this.props = props;
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        RSAKey key = new RSAKey.Builder(publicKey())
                .privateKey(privateKey())
                .keyID(kid())
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey()).build();
        // Mandatory checks: exp/nbf within 30s skew, iss, aud, alg=RS256, non-empty matching kid.
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ofSeconds(props.getClockSkew().toSeconds())),
                new JwtIssuerValidator(props.getIssuer()),
                claim("audience", jwt -> jwt.getAudience() != null && jwt.getAudience().contains(props.getAudience())),
                claim("kid", jwt -> props.getKid().equals(jwt.getHeaders().get("kid"))),
                claim("alg", jwt -> "RS256".equals(jwt.getHeaders().get("alg"))))); 
        return decoder;
    }

    private OAuth2TokenValidator<Jwt> claim(String name, Predicate<Jwt> test) {
        return jwt -> test.test(jwt)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", name + " validation failed", null));
    }

    private String kid() {
        return require(props.getKid(), "frameflow.jwt.kid");
    }

    private RSAPublicKey publicKey() {
        return (RSAPublicKey) parseKey(require(props.getPublicKeyPem(), "frameflow.jwt.public-key-pem"), true);
    }

    private RSAPrivateKey privateKey() {
        return (RSAPrivateKey) parseKey(require(props.getPrivateKeyPem(), "frameflow.jwt.private-key-pem"), false);
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing required JWT key property " + name + ". Inject it via environment/secret; keys must not be committed.");
        }
        return value;
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
            throw new IllegalStateException("Failed to parse injected JWT " + (publicKey ? "public" : "private") + " key PEM", e);
        }
    }
}