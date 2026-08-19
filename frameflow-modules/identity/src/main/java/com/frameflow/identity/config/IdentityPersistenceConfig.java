package com.frameflow.identity.config;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.frameflow.identity.infrastructure.persistence.UuidTypeHandler;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the identity module's MyBatis mappers; identity owns its own tables only. */
@Configuration
@MapperScan("com.frameflow.identity.infrastructure.persistence")
public class IdentityPersistenceConfig {

    /**
     * Register the PostgreSQL uuid {@literal <->} {@link java.util.UUID} handler in the MyBatis
     * registry. Annotation-driven mappers do not auto-discover {@code @MappedTypes} handlers, so
     * without this every UUID parameter ({@code idempotencyKey}, {@code familyId}) fails statement
     * construction with "Type handler was null".
     */
    @Bean
    ConfigurationCustomizer uuidTypeHandlerCustomizer() {
        return configuration -> configuration.getTypeHandlerRegistry().register(new UuidTypeHandler());
    }
}