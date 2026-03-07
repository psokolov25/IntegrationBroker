package ru.aritmos.integrationbroker.core;

import jakarta.inject.Singleton;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Операционные метрики admin batch-операций (DLQ/Outbox).
 * Хранятся в памяти процесса и предназначены для быстрого отображения в monitoring/dashboard.
 */
@Singleton
public class AdminOperationsMetrics {

    private final AtomicLong dlqReplayBatchRuns = new AtomicLong();
    private final AtomicLong dlqReplayBatchSelected = new AtomicLong();
    private final AtomicLong dlqReplayBatchSuccess = new AtomicLong();
    private final AtomicLong dlqReplayBatchLocked = new AtomicLong();
    private final AtomicLong dlqReplayBatchFailed = new AtomicLong();
    private final AtomicLong dlqReplayBatchDead = new AtomicLong();

    private final AtomicLong dlqMarkIgnoredBatchRuns = new AtomicLong();
    private final AtomicLong dlqMarkIgnoredBatchUpdated = new AtomicLong();

    private final AtomicLong restCancelBatchRuns = new AtomicLong();
    private final AtomicLong restCancelBatchCancelled = new AtomicLong();

    private final AtomicLong lastRequestedLimit = new AtomicLong();
    private final AtomicLong lastAppliedLimit = new AtomicLong();
    private final AtomicLong lastLimitClamped = new AtomicLong();

    public void recordDlqReplayBatch(int selected, int success, int locked, int failed, int dead, int requestedLimit, int appliedLimit, boolean limitClamped) {
        dlqReplayBatchRuns.incrementAndGet();
        dlqReplayBatchSelected.addAndGet(Math.max(0, selected));
        dlqReplayBatchSuccess.addAndGet(Math.max(0, success));
        dlqReplayBatchLocked.addAndGet(Math.max(0, locked));
        dlqReplayBatchFailed.addAndGet(Math.max(0, failed));
        dlqReplayBatchDead.addAndGet(Math.max(0, dead));
        rememberLimit(requestedLimit, appliedLimit, limitClamped);
    }

    public void recordDlqMarkIgnoredBatch(int updated, int requestedLimit, int appliedLimit) {
        dlqMarkIgnoredBatchRuns.incrementAndGet();
        dlqMarkIgnoredBatchUpdated.addAndGet(Math.max(0, updated));
        rememberLimit(requestedLimit, appliedLimit, requestedLimit != appliedLimit);
    }

    public void recordRestCancelBatch(int cancelled, int requestedLimit, int appliedLimit) {
        restCancelBatchRuns.incrementAndGet();
        restCancelBatchCancelled.addAndGet(Math.max(0, cancelled));
        rememberLimit(requestedLimit, appliedLimit, requestedLimit != appliedLimit);
    }

    public Snapshot snapshot() {
        return new Snapshot(
                dlqReplayBatchRuns.get(),
                dlqReplayBatchSelected.get(),
                dlqReplayBatchSuccess.get(),
                dlqReplayBatchLocked.get(),
                dlqReplayBatchFailed.get(),
                dlqReplayBatchDead.get(),
                dlqMarkIgnoredBatchRuns.get(),
                dlqMarkIgnoredBatchUpdated.get(),
                restCancelBatchRuns.get(),
                restCancelBatchCancelled.get(),
                lastRequestedLimit.get(),
                lastAppliedLimit.get(),
                lastLimitClamped.get() == 1L
        );
    }

    private void rememberLimit(int requestedLimit, int appliedLimit, boolean clamped) {
        lastRequestedLimit.set(Math.max(0, requestedLimit));
        lastAppliedLimit.set(Math.max(0, appliedLimit));
        lastLimitClamped.set(clamped ? 1L : 0L);
    }

    public record Snapshot(
            long dlqReplayBatchRuns,
            long dlqReplayBatchSelected,
            long dlqReplayBatchSuccess,
            long dlqReplayBatchLocked,
            long dlqReplayBatchFailed,
            long dlqReplayBatchDead,
            long dlqMarkIgnoredBatchRuns,
            long dlqMarkIgnoredBatchUpdated,
            long restCancelBatchRuns,
            long restCancelBatchCancelled,
            long lastRequestedLimit,
            long lastAppliedLimit,
            boolean lastLimitClamped
    ) {
    }
}
