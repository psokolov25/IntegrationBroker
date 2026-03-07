package ru.aritmos.integrationbroker.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminOperationsMetricsTest {

    @Test
    void shouldAccumulateBatchCountersAndRememberLastLimit() {
        AdminOperationsMetrics metrics = new AdminOperationsMetrics();

        metrics.recordDlqReplayBatch(5, 3, 1, 1, 0, 120, 100, true);
        metrics.recordDlqMarkIgnoredBatch(7, 80, 80);
        metrics.recordRestCancelBatch(4, 250, 200);

        AdminOperationsMetrics.Snapshot snapshot = metrics.snapshot();

        assertEquals(1L, snapshot.dlqReplayBatchRuns());
        assertEquals(5L, snapshot.dlqReplayBatchSelected());
        assertEquals(3L, snapshot.dlqReplayBatchSuccess());
        assertEquals(1L, snapshot.dlqReplayBatchLocked());
        assertEquals(1L, snapshot.dlqReplayBatchFailed());
        assertEquals(0L, snapshot.dlqReplayBatchDead());

        assertEquals(1L, snapshot.dlqMarkIgnoredBatchRuns());
        assertEquals(7L, snapshot.dlqMarkIgnoredBatchUpdated());

        assertEquals(1L, snapshot.restCancelBatchRuns());
        assertEquals(4L, snapshot.restCancelBatchCancelled());

        assertEquals(250L, snapshot.lastRequestedLimit());
        assertEquals(200L, snapshot.lastAppliedLimit());
        assertTrue(snapshot.lastLimitClamped());
    }

    @Test
    void shouldClampNegativeValuesToZero() {
        AdminOperationsMetrics metrics = new AdminOperationsMetrics();

        metrics.recordDlqReplayBatch(-1, -2, -3, -4, -5, -6, -7, false);
        metrics.recordDlqMarkIgnoredBatch(-9, -10, -11);
        metrics.recordRestCancelBatch(-12, -13, -14);

        AdminOperationsMetrics.Snapshot snapshot = metrics.snapshot();

        assertEquals(0L, snapshot.dlqReplayBatchSelected());
        assertEquals(0L, snapshot.dlqReplayBatchSuccess());
        assertEquals(0L, snapshot.dlqReplayBatchLocked());
        assertEquals(0L, snapshot.dlqReplayBatchFailed());
        assertEquals(0L, snapshot.dlqReplayBatchDead());
        assertEquals(0L, snapshot.dlqMarkIgnoredBatchUpdated());
        assertEquals(0L, snapshot.restCancelBatchCancelled());
        assertEquals(0L, snapshot.lastRequestedLimit());
        assertEquals(0L, snapshot.lastAppliedLimit());
        assertTrue(snapshot.lastLimitClamped());
    }
}
