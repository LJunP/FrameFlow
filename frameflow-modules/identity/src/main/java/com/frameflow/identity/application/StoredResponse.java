package com.frameflow.identity.application;

/** HTTP status + raw JSON body persisted for idempotent replay. */
public record StoredResponse(int status, String bodyJson) {
}
