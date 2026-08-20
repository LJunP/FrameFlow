package com.frameflow.product.api;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Team-scoped authorization from live database facts (never from stale tokens). */
@Component
public class TeamGuard {

    private final JdbcTemplate jdbc;

    public TeamGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Membership(Long teamId, String role) {
    }

    public Membership requireMember(long userId, long teamId) {
        List<String> roles = jdbc.query(
                "SELECT role FROM team_members WHERE team_id = ? AND user_id = ? AND status = 'ACTIVE'",
                (rs, n) -> rs.getString("role"), teamId, userId);
        if (roles.isEmpty()) {
            throw ApiException.notFound("team resource not found");
        }
        return new Membership(teamId, roles.get(0));
    }

    public void requireOwnerOrOperator(long userId, long teamId) {
        Membership m = requireMember(userId, teamId);
        if (!"OWNER".equals(m.role()) && !"OPERATOR".equals(m.role())) {
            throw ApiException.forbidden("requires OWNER or OPERATOR");
        }
    }

    public void requireOwner(long userId, long teamId) {
        Membership m = requireMember(userId, teamId);
        if (!"OWNER".equals(m.role())) {
            throw ApiException.forbidden("requires OWNER");
        }
    }
}
