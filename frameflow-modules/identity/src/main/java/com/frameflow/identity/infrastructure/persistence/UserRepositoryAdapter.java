package com.frameflow.identity.infrastructure.persistence;

import com.frameflow.identity.application.port.out.UserRepository;
import com.frameflow.identity.domain.User;
import org.springframework.stereotype.Repository;

/** MyBatis-backed user persistence adapter. */
@Repository
public class UserRepositoryAdapter implements UserRepository {

    private final UserMapper mapper;

    public UserRepositoryAdapter(UserMapper mapper) {
        this.mapper = mapper;
    }

    @Override public int insert(User user) { return mapper.insert(user); }
    @Override public User findByEmail(String email) { return mapper.findByEmail(email); }
    @Override public User findById(long id) { return mapper.findById(id); }
    @Override public int touchLastLogin(long id) { return mapper.touchLastLogin(id); }
}
