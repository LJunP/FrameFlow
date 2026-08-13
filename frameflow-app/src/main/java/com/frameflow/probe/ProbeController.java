package com.frameflow.probe;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
final class ProbeController {

    private static final ProbeResponse UP = new ProbeResponse("UP");
    private static final ProbeResponse READY = new ProbeResponse("READY");
    private static final ProbeResponse NOT_READY = new ProbeResponse("NOT_READY");

    private final ReadinessChecker readinessChecker;

    ProbeController(ReadinessChecker readinessChecker) {
        this.readinessChecker = readinessChecker;
    }

    @GetMapping(path = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    ProbeResponse health() {
        return UP;
    }

    @GetMapping(path = "/readiness", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ProbeResponse> readiness() {
        if (readinessChecker.isReady()) {
            return ResponseEntity.ok(READY);
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(NOT_READY);
    }
}
