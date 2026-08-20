package com.frameflow.product.config;

import com.frameflow.product.application.StoragePort;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProductStorageConfig {

    @Bean
    StoragePort storagePort(@Value("${frameflow.storage.root:data/media}") String root) {
        return StoragePort.local(Path.of(root).toAbsolutePath().normalize());
    }
}
