package com.frameflow.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frameflow.identity.application.AccessTokenService;
import com.frameflow.identity.application.ActiveUserPolicy;
import com.frameflow.identity.application.IdempotencyService;
import com.frameflow.identity.application.RefreshTokenService;
import com.frameflow.identity.application.RequestFingerprint;
import com.frameflow.identity.application.TeamService;
import com.frameflow.identity.application.UserService;
import com.frameflow.identity.application.model.AuthModels.RegisterCommand;
import com.frameflow.identity.application.model.TeamModels.AddMemberCommand;
import com.frameflow.identity.application.port.out.IdempotencyRecordRepository;
import com.frameflow.identity.application.port.out.TeamMemberRepository;
import com.frameflow.identity.application.port.out.TeamRepository;
import com.frameflow.identity.application.port.out.UserRepository;
import com.frameflow.identity.domain.TeamMember;
import com.frameflow.identity.domain.User;
import com.frameflow.identity.error.ApiException;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Forces the exact Spring persistence exception paths in addition to the real race tests. */
class PersistenceConflictTranslationTest {

    @Test
    void duplicateEmailConstraintIsTranslatedToStableConflict() {
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        when(users.findByEmail("race@example.com")).thenReturn(null);
        when(passwords.encode("passw0rd!")).thenReturn("bcrypt");
        when(users.insert(any(User.class))).thenThrow(new DuplicateKeyException("uq_users_email"));

        UserService service = new UserService(users, passwords, mock(AccessTokenService.class),
                mock(RefreshTokenService.class), mock(ActiveUserPolicy.class));
        ApiException error = catchThrowableOfType(ApiException.class, () -> service.register(
                new RegisterCommand("race@example.com", "passw0rd!", "Race")));

        assertThat(error.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(error.getCode()).isEqualTo("USER_EMAIL_CONFLICT");
    }

    @Test
    void memberUniqueConstraintIsTranslatedToStableConflictAcrossIdempotencyKeys() {
        TeamMemberRepository members = mock(TeamMemberRepository.class);
        ActiveUserPolicy activeUsers = mock(ActiveUserPolicy.class);
        IdempotencyRecordRepository idempotencyRecords = mock(IdempotencyRecordRepository.class);
        when(idempotencyRecords.insertIgnore(any())).thenReturn(1);

        User target = new User();
        target.setId(22L);
        target.setEmail("target@example.com");
        target.setStatus("ACTIVE");
        when(activeUsers.requireActiveTarget("target@example.com")).thenReturn(target);

        TeamMember owner = new TeamMember();
        owner.setTeamId(10L);
        owner.setUserId(11L);
        owner.setRole("OWNER");
        owner.setStatus("ACTIVE");
        when(members.findActiveByTeamAndUser(10L, 11L)).thenReturn(owner);
        when(members.findByTeamAndUserForUpdate(10L, 22L)).thenReturn(null);
        when(members.insert(any(TeamMember.class)))
                .thenThrow(new DuplicateKeyException("uq_team_members_team_user"));

        ObjectMapper mapper = new ObjectMapper();
        TeamService service = new TeamService(mock(TeamRepository.class), members, activeUsers,
                new IdempotencyService(idempotencyRecords, Clock.systemUTC()),
                new RequestFingerprint(mapper), mapper);
        ApiException error = catchThrowableOfType(ApiException.class, () -> service.addMember(
                11L, 10L, new AddMemberCommand("target@example.com", "EDITOR"), UUID.randomUUID()));

        assertThat(error.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(error.getCode()).isEqualTo("TEAM_MEMBER_ALREADY_EXISTS");
    }
}
