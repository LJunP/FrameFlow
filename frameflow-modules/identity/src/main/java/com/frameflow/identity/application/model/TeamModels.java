package com.frameflow.identity.application.model;

import java.time.OffsetDateTime;
import java.util.List;

/** Framework-free team commands and results owned by the application layer. */
public final class TeamModels {

    private TeamModels() {
    }

    public record CreateTeamCommand(String name) {
    }

    public record AddMemberCommand(String email, String role) {
    }

    public record UpdateMemberRoleCommand(String role) {
    }

    public record TeamView(long id, String name, OffsetDateTime createdAt) {
    }

    public record TeamMemberView(long id, long teamId, long userId, String email,
                                 String role, String status) {
    }

    public record TeamMembershipView(long teamId, String name, String role, String status,
                                     OffsetDateTime createdAt) {
    }

    public record TeamListView(List<TeamMembershipView> items) {
    }

    public record MemberListView(List<TeamMemberView> items) {
    }
}
