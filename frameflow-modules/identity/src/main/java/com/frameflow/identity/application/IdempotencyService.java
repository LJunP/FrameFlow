package com.frameflow.identity.application;

import com.frameflow.identity.domain.IdempotencyRecord;
import com.frameflow.identity.error.ApiException;
import com.frameflow.identity.error.ErrorCodes;
import com.frameflow.identity.infrastructure.persistence.IdempotencyRecordMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL-backed request deduplication (M01 fact source; idempotency-contract.md §3.1).
 *
 * The unique (scope, idempotency_key) constraint serializes concurrent attempts:
 * the winner executes the business action and records the response in the same
 * transaction; the losers replay the recorded response. A different payload under
 * the same key is rejected with IDEMPOTENCY_CONFLICT; a still-processing attempt
 * beyond the bounded wait is rejected with IDEMPOTENCY_IN_PROGRESS.
 */
@Service
public class IdempotencyService {

    private static final long RECORD_TTL_HOURS = 24;
    private static final int WAIT_ROUNDS = 20;
    private static final long WAIT_MILLIS = 100;

    private final IdempotencyRecordMapper recordMapper;
    private final Clock clock;

    public IdempotencyService(IdempotencyRecordMapper recordMapper, Clock clock) {
        this.recordMapper = recordMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public <T> IdempotencyExecution<T> execute(
            String scope, UUID key, String requestHash,
            Supplier<T> businessAction, Function<T, StoredResponse> toStoredResponse) {
        return executeOnce(scope, key, requestHash, businessAction, toStoredResponse, 0);
    }

    private <T> IdempotencyExecution<T> executeOnce(
            String scope, UUID key, String requestHash,
            Supplier<T> businessAction, Function<T, StoredResponse> toStoredResponse, int expiryRetry) {
        OffsetDateTime now = OffsetDateTime.now(clock);

        IdempotencyRecord candidate = new IdempotencyRecord();
        candidate.setScope(scope);
        candidate.setIdempotencyKey(key);
        candidate.setRequestHash(requestHash);
        candidate.setExpiresAt(now.plusSeconds(RECORD_TTL_HOURS * 3600));

        int inserted = recordMapper.insertIgnore(candidate);
        if (inserted == 1) {
            T value = businessAction.get();
            StoredResponse stored = toStoredResponse.apply(value);
            recordMapper.complete(candidate.getId(), stored.status(), stored.bodyJson());
            return IdempotencyExecution.fresh(value);
        }

        IdempotencyRecord existing = recordMapper.findByScopeAndKeyForUpdate(scope, key);
        if (existing == null) {
            if (expiryRetry < 1) {
                return executeOnce(scope, key, requestHash, businessAction, toStoredResponse, expiryRetry + 1);
            }
            throw ApiException.conflict(ErrorCodes.IDEMPOTENCY_IN_PROGRESS, "相同幂等键的请求仍在处理中，请使用同一键稍后重试");
        }

        if (existing.getExpiresAt() == null || existing.getExpiresAt().isBefore(now)) {
            recordMapper.deleteByScopeAndKey(scope, key);
            return executeOnce(scope, key, requestHash, businessAction, toStoredResponse, expiryRetry + 1);
        }

        if (!existing.getRequestHash().equals(requestHash)) {
            throw ApiException.conflict(ErrorCodes.IDEMPOTENCY_CONFLICT, "相同幂等键已用于不同的请求内容");
        }

        if ("COMPLETED".equals(existing.getStatus())) {
            return IdempotencyExecution.replayed(existing.getResponseStatus(), existing.getResponseBody());
        }

        for (int i = 0; i < WAIT_ROUNDS; i++) {
            try {
                Thread.sleep(WAIT_MILLIS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
            IdempotencyRecord latest = recordMapper.findByScopeAndKey(scope, key);
            if (latest == null) {
                if (expiryRetry < 1) {
                    return executeOnce(scope, key, requestHash, businessAction, toStoredResponse, expiryRetry + 1);
                }
                break;
            }
            if ("COMPLETED".equals(latest.getStatus())) {
                if (latest.getRequestHash().equals(requestHash)) {
                    return IdempotencyExecution.replayed(latest.getResponseStatus(), latest.getResponseBody());
                }
                throw ApiException.conflict(ErrorCodes.IDEMPOTENCY_CONFLICT, "相同幂等键已用于不同的请求内容");
            }
        }
        throw ApiException.conflict(ErrorCodes.IDEMPOTENCY_IN_PROGRESS, "相同幂等键的请求仍在处理中，请使用同一键稍后重试");
    }
}
