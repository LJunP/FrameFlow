package com.frameflow.learning;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LearningApplicationTests {

    @Test
    void contextLoads() {
        assertThat(LearningApplication.class).isNotNull();
    }
}
