-- Служебные таблицы Integration Broker.
-- ВНИМАНИЕ: любые изменения схемы выполняются только через Flyway-миграции.

CREATE TABLE IF NOT EXISTS ib_idempotency (
    idem_key              VARCHAR(128) PRIMARY KEY,
    strategy              VARCHAR(32)  NOT NULL,
    status                VARCHAR(32)  NOT NULL,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    lock_until            TIMESTAMP WITH TIME ZONE NOT NULL,
    result_json           TEXT NULL,
    last_error_code       VARCHAR(64) NULL,
    last_error_message    TEXT NULL
);

CREATE INDEX IF NOT EXISTS ix_ib_idempotency_status_updated
    ON ib_idempotency (status, updated_at DESC);
