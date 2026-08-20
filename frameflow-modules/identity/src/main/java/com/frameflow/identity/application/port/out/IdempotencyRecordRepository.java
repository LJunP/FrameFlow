package com.frameflow.identity.application.port.out;

import com.frameflow.identity.domain.IdempotencyRecord;
import java.util.UUID;

/** PostgreSQL idempotency fact-source port. */
public interface IdempotencyRecordRepository {
    int insertIgnore(IdempotencyRecord record);
    IdempotencyRecord findByScopeAndKeyForUpdate(String scope, UUID key);
    IdempotencyRecord findByScopeAndKey(String scope, UUID key);
    int complete(long id, int status, String body);
    int deleteByScopeAndKey(String scope, UUID key);
}
