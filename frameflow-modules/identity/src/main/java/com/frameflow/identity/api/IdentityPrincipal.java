package com.frameflow.identity.api;

/** Published identity API: the authenticated principal available to other modules. */
public record IdentityPrincipal(long userId) {
    public static IdentityPrincipal of(long userId) {
        return new IdentityPrincipal(userId);
    }
}
