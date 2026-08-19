package com.frameflow.identity.security;

/** Authenticated principal: the user id carried by the validated access token (sub claim). */
public record CurrentUser(long userId) {
}
