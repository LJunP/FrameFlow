package com.frameflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class FrameFlowApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(FrameFlowApplication.class, args);
        boolean migrationOnly = context.getEnvironment()
                .getProperty("frameflow.migration-only", Boolean.class, false);
        if (migrationOnly) {
            context.close();
        }
    }
}
