package ru.aritmos.integrationbroker.core;

import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Простой in-memory TTL-кэш.
 * <p>
 * Используется для краткоживущих данных (например, enrichment по KeycloakProxy), чтобы:
 * <ul>
 *   <li>снизить нагрузку на внешнюю зависимость;</li>
 *   <li>минимизировать повторные сетевые вызовы при «шторме» повторных сообщений;</li>
 *   <li>не хранить чувствительные данные дольше необходимого.</li>
 * </ul>
 * <p>
 * Важно: кэш не является источником истины и не заменяет PostgreSQL для outbox/DLQ/idempotency.
 * Redis-кэш может быть добавлен позднее как расширение, но базовая реализация должна работать без него.
 */
public final class TtlCache<K, V> {

    private final ConcurrentHashMap<K, Entry<V>> map = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxEntries;

    public TtlCache(Clock clock, int maxEntries) {
        this.clock = clock;
        this.maxEntries = Math.max(1, maxEntries);
    }

    /**
     * Получить значение по ключу, если оно не истекло.
     */
    public Optional<V> get(K key) {
        if (key == null) {
            return Optional.empty();
        }
        Entry<V> e = map.get(key);
        if (e == null) {
            return Optional.empty();
        }
        long now = clock.millis();
        if (e.expiresAtMs <= now) {
            map.remove(key);
            return Optional.empty();
        }
        return Optional.ofNullable(e.value);
    }

    /**
     * Положить значение в кэш.
     * <p>
     * При переполнении используется простая стратегия: очищаем кэш целиком.
     * LRU будет добавлен отдельной итерацией, когда кэш станет критичным для производительности.
     */
    public void put(K key, V value, long ttlSeconds) {
        if (key == null) {
            return;
        }
        if (map.size() > maxEntries) {
            map.clear();
        }
        long ttlMs = Math.max(0, ttlSeconds) * 1000L;
        long expiresAt = clock.millis() + ttlMs;
        map.put(key, new Entry<>(value, expiresAt));
    }

    /**
     * Количество элементов в кэше.
     */
    public int size() {
        return map.size();
    }

    private record Entry<V>(V value, long expiresAtMs) {
    }
}
