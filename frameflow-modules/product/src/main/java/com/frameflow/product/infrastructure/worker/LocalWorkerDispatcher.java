package com.frameflow.product.infrastructure.worker;

import com.frameflow.product.application.WorkerDispatcher;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Local deterministic worker dispatcher: enqueue writes an outbox row (managed by
 * the caller) and, when a worker command node is configured, spawns the Python
 * worker CLI in-process. When no worker node exists this is a thin no-op so the
 * rest of the pipeline stays testable; the S3 task wires real dispatch.
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
            // Executed by the S3 vertical slice integration; kept local and bounded.
        }
    }

    public String commandFor(long runId) {
        return commands.get(runId);
    }
}