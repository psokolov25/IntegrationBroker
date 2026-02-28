package ru.aritmos.integrationbroker.adapters;

import jakarta.inject.Singleton;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Эксплуатационный счётчик конфликтов VisitManager (HTTP 409).
 */
@Singleton
public class VisitManagerConflictMetrics {

    private final AtomicLong conflicts409 = new AtomicLong();

    public void increment409() {
        conflicts409.incrementAndGet();
    }

    public long conflicts409() {
        return conflicts409.get();
    }
}

