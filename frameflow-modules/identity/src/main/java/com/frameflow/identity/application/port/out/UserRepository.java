package com.frameflow.identity.application.port.out;

import com.frameflow.identity.domain.User;

/** Persistence port for users; implemented by the infrastructure layer. */
public interface UserRepository {
    int insert(User user);
    User findByEmail(String email);
    User findById(long id);
    int touchLastLogin(long id);
}
