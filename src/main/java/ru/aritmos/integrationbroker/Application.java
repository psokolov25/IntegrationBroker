package ru.aritmos.integrationbroker;

import io.micronaut.runtime.Micronaut;

/**
 * Точка входа Integration Broker.
 * <p>
 * Сервис предназначен для приёма событий/команд от СУО (VisitManager), DeviceManager и внешних систем,
 * нормализации в единый контракт, выбора интеграционного flow и выполнения orchestration через Groovy.
 * <p>
 * Важно: логика интеграции не должна «расползаться» в Groovy.
 * Groovy применяется для маршрутизации и склейки шагов, а типизированные Java-адаптеры остаются источником
 * контрактов и правил интеграции.
 */
public final class Application {

    private Application() {
        // утилитарный класс
    }

    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }
}
