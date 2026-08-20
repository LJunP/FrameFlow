package com.frameflow.identity.application.port.out;

import com.frameflow.identity.domain.RefreshTokenSession;
import java.util.UUID;

/** Persistence port for refresh-token sessions. */
public interface RefreshTokenSessionRepository {
    int insert(RefreshTokenSession session);
    RefreshTokenSession findByHashForUpdate(String hash);
    int revokeActiveByFamily(UUID familyId);
    int markRotated(long id, long replacedBy);
    int touchLastUsed(long id);
}
