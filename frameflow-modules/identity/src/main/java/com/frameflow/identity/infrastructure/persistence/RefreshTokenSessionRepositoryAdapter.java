package com.frameflow.identity.infrastructure.persistence;

import com.frameflow.identity.application.port.out.RefreshTokenSessionRepository;
import com.frameflow.identity.domain.RefreshTokenSession;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** MyBatis-backed refresh-token session adapter. */
@Repository
public class RefreshTokenSessionRepositoryAdapter implements RefreshTokenSessionRepository {

    private final RefreshTokenSessionMapper mapper;

    public RefreshTokenSessionRepositoryAdapter(RefreshTokenSessionMapper mapper) {
        this.mapper = mapper;
    }

    @Override public int insert(RefreshTokenSession session) { return mapper.insert(session); }
    @Override public RefreshTokenSession findByHashForUpdate(String hash) { return mapper.findByHashForUpdate(hash); }
    @Override public int revokeActiveByFamily(UUID familyId) { return mapper.revokeActiveByFamily(familyId); }
    @Override public int markRotated(long id, long replacedBy) { return mapper.markRotated(id, replacedBy); }
    @Override public int touchLastUsed(long id) { return mapper.touchLastUsed(id); }
}
