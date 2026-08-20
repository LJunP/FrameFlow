package com.frameflow.product.infrastructure.worker;

import com.frameflow.product.application.WorkerDispatcher;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Local deterministic worker dispatcher: enqueue records a command id and, when a
 * worker command (frameflow.worker.command) is configured, executes it. In the
 * default (unconfigured) profile it is a thin no-op so the whole pipeline stays
 * testable; the S3 vertical slice and local demo wire real dispatch through
 * WorkerRunner behind the same port.
 */
@Component
public class LocalWorkerDispatcher implements WorkerDispatcher {

    private final Map<Long, String> commands = new ConcurrentHashMap<>();

    @Value("${frameflow.worker.command:}")
    private String workerCommand;

    @Override
    public void enqueue(long runId) {
        commands.put(runId, "cmd_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        if (workerCommand != null && !workerCommand.isBlank()) {
            try {
                Runtime.getRuntime().exec(new String[]{workerCommand, String.valueOf(runId)});
            } catch (Exception ignored) {
                // best effort; result ingestion remains the source of truth
            }
        }
    }

    public String commandFor(long runId) {
        return commands.get(runId);
    }
}
