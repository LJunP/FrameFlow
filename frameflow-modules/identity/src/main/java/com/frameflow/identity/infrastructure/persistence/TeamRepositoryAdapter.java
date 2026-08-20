package com.frameflow.identity.infrastructure.persistence;

import com.frameflow.identity.application.port.out.TeamRepository;
import com.frameflow.identity.domain.Team;
import org.springframework.stereotype.Repository;

/** MyBatis-backed team persistence adapter. */
@Repository
public class TeamRepositoryAdapter implements TeamRepository {

    private final TeamMapper mapper;

    public TeamRepositoryAdapter(TeamMapper mapper) {
        this.mapper = mapper;
    }

    @Override public int insert(Team team) { return mapper.insert(team); }
    @Override public Team findById(long id) { return mapper.findById(id); }
}
