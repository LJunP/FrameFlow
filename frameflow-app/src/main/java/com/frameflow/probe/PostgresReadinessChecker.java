package com.frameflow.probe;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
final class PostgresReadinessChecker implements ReadinessChecker {

    private final JdbcTemplate jdbcTemplate;

    PostgresReadinessChecker(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean isReady() {
        try {
            Integer value = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return Integer.valueOf(1).equals(value);
        } catch (DataAccessException ignored) {
            return false;
        }
    }
}
