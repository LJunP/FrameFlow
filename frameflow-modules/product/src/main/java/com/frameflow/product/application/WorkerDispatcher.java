package com.frameflow.product.application;

/** Port for dispatching analysis runs to the Python worker (S3). */
public interface WorkerDispatcher {
    void enqueue(long runId);

    /** Best-effort command id for a run (may be null before dispatch). */
    default String commandFor(long runId) {
        return null;
    }
}
