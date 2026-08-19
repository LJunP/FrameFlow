package com.frameflow.identity.application;

import com.frameflow.identity.config.JwtProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.stereotype.Service;

/** Issues short-lived RS256 access tokens with the contract headers/claims. */
@Service
@ConditionalOnWebApplication
public class AccessTokenService {

    private final JwtEncoder encoder;
    private final JwtProperties props;

    public AccessTokenService(JwtEncoder encoder, JwtProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    public String issue(long userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.getIssuer())
                .subject(String.valueOf(userId))
                .audience(List.of(props.getAudience()))
                .issuedAt(now)
                .expiresAt(now.plus(props.getAccessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(props.getKid())
                .type("JWT")
                .build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long expiresInSeconds() {
        return props.getAccessTokenTtl().toSeconds();
    }
}