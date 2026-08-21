package com.frameflow.learning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FrameFlow Select - minimal learning skeleton.
 *
 * This application intentionally contains NO business implementation. It only
 * proves the local toolchain (Java 21 + Maven + Spring Boot) works end to end
 * and exposes the actuator health endpoint. The first real business slice
 * (Project create/query) is the project owner task (see .learning/).
 *
 * The finished reference product lives on archive branch
 * archive/frameflow-select-agent-mvp-v1 (tag frameflow-select-agent-mvp-v1.0.0).
 */
@SpringBootApplication
public class LearningApplication {

    public static void main(String[] args) {
        SpringApplication.run(LearningApplication.class, args);
    }
}
