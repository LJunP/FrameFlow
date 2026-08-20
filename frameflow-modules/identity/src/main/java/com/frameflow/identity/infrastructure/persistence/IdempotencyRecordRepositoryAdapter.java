package com.frameflow.identity.infrastructure.persistence;

import com.frameflow.identity.application.port.out.IdempotencyRecordRepository;
import com.frameflow.identity.domain.IdempotencyRecord;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** MyBatis-backed idempotency fact-source adapter. */
@Repository
public class IdempotencyRecordRepositoryAdapter implements IdempotencyRecordRepository {

    private final IdempotencyRecordMapper mapper;

    public IdempotencyRecordRepositoryAdapter(IdempotencyRecordMapper mapper) {
        this.mapper = mapper;
    }

    @Override public int insertIgnore(IdempotencyRecord record) { return mapper.insertIgnore(record); }
    @Override public IdempotencyRecord findByScopeAndKeyForUpdate(String scope, UUID key) {
        return mapper.findByScopeAndKeyForUpdate(scope, key);
    }
    @Override public IdempotencyRecord findByScopeAndKey(String scope, UUID key) {
        return mapper.findByScopeAndKey(scope, key);
    }
    @Override public int complete(long id, int status, String body) { return mapper.complete(id, status, body); }
    @Override public int deleteByScopeAndKey(String scope, UUID key) { return mapper.deleteByScopeAndKey(scope, key); }
}
