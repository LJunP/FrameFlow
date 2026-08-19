package com.frameflow.identity.application;

/** Result of an idempotency-guarded write: fresh execution or replayed response. */
public final class IdempotencyExecution<T> {
    private final T value;
    private final boolean replayed;
    private final Integer replayStatus;
    private final String replayBody;

    private IdempotencyExecution(T value, boolean replayed, Integer replayStatus, String replayBody) {
        this.value = value;
        this.replayed = replayed;
        this.replayStatus = replayStatus;
        this.replayBody = replayBody;
    }

    public static <T> IdempotencyExecution<T> fresh(T value) {
        return new IdempotencyExecution<>(value, false, null, null);
    }

    public static <T> IdempotencyExecution<T> replayed(int status, String body) {
        return new IdempotencyExecution<>(null, true, status, body);
    }

    public boolean isReplayed() { return replayed; }
    public T value() { return value; }
    public Integer replayStatus() { return replayStatus; }
    public String replayBody() { return replayBody; }
}
