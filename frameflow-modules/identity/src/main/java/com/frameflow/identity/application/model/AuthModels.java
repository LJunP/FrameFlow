package com.frameflow.identity.application.model;

import java.time.OffsetDateTime;

/** Framework-free commands and results owned by the identity application layer. */
public final class AuthModels {

    private AuthModels() {
    }

    public record RegisterCommand(String email, String password, String displayName) {
    }

    public record LoginCommand(String email, String password) {
    }

    public record UserView(long id, String email, String displayName, String status,
                           OffsetDateTime lastLoginAt) {
    }

    public record TokenPairView(String accessToken, String refreshToken, long expiresInSeconds) {
    }
}
