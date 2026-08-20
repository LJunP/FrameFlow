package com.frameflow.identity.application;

import com.frameflow.identity.application.port.out.UserRepository;
import com.frameflow.identity.domain.User;
import com.frameflow.identity.error.ApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;

/** Single application-layer policy for operations that require an ACTIVE user. */
@Component
@ConditionalOnWebApplication
public class ActiveUserPolicy {

    private final UserRepository users;

    public ActiveUserPolicy(UserRepository users) {
        this.users = users;
    }

    public User requireActive(long userId) {
        User user = users.findById(userId);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw ApiException.unauthenticated("用户不存在或已禁用");
        }
        return user;
    }

    public User requireActiveTarget(String normalizedEmail) {
        User user = users.findByEmail(normalizedEmail);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            // Do not expose whether a disabled account exists.
            throw ApiException.notFound("目标用户不存在");
        }
        return user;
    }
}
