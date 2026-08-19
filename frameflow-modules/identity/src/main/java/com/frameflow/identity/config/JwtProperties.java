package com.frameflow.identity.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT configuration bound from properties/environment:
 * frameflow.jwt.issuer / audience / kid / private-key-pem / public-key-pem /
 * access-token-ttl / refresh-token-ttl / clock-skew.
 *
 * Keys are injected via environment variables or secrets only; they are never
 * committed to the repository or stored in the database (token-contract.md §5).
 */
@ConfigurationProperties(prefix = "frameflow.jwt")
public class JwtProperties {

    private String issuer = "frameflow";
    private String audience = "frameflow-api";
    private String kid;
    private String privateKeyPem;
    private String publicKeyPem;
    private Duration accessTokenTtl = Duration.ofMinutes(15);
    private Duration refreshTokenTtl = Duration.ofDays(30);
    private Duration clockSkew = Duration.ofSeconds(30);

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
    public String getKid() { return kid; }
    public void setKid(String kid) { this.kid = kid; }
    public String getPrivateKeyPem() { return privateKeyPem; }
    public void setPrivateKeyPem(String privateKeyPem) { this.privateKeyPem = privateKeyPem; }
    public String getPublicKeyPem() { return publicKeyPem; }
    public void setPublicKeyPem(String publicKeyPem) { this.publicKeyPem = publicKeyPem; }
    public Duration getAccessTokenTtl() { return accessTokenTtl; }
    public void setAccessTokenTtl(Duration accessTokenTtl) { this.accessTokenTtl = accessTokenTtl; }
    public Duration getRefreshTokenTtl() { return refreshTokenTtl; }
    public void setRefreshTokenTtl(Duration refreshTokenTtl) { this.refreshTokenTtl = refreshTokenTtl; }
    public Duration getClockSkew() { return clockSkew; }
    public void setClockSkew(Duration clockSkew) { this.clockSkew = clockSkew; }
}
