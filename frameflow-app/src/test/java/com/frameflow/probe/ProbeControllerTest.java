package com.frameflow.probe;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProbeControllerTest {

    private ReadinessChecker readinessChecker;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        readinessChecker = mock(ReadinessChecker.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController(readinessChecker)).build();
    }

    @Test
    void healthDoesNotConsultDatabaseReadiness() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"status\":\"UP\"}", JsonCompareMode.STRICT));

        verifyNoInteractions(readinessChecker);
    }

    @Test
    void readinessReturnsReadyWhenDatabaseIsAvailable() throws Exception {
        when(readinessChecker.isReady()).thenReturn(true);

        mockMvc.perform(get("/readiness"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"READY\"}", JsonCompareMode.STRICT));
    }

    @Test
    void readinessReturnsServiceUnavailableWhenDatabaseIsUnavailable() throws Exception {
        when(readinessChecker.isReady()).thenReturn(false);

        mockMvc.perform(get("/readiness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("{\"status\":\"NOT_READY\"}", JsonCompareMode.STRICT));
    }
}
