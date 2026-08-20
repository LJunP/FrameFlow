package com.frameflow.identity.application.port.out;

import com.frameflow.identity.domain.Team;

/** Persistence port for teams; implemented by the infrastructure layer. */
public interface TeamRepository {
    int insert(Team team);
    Team findById(long id);
}
