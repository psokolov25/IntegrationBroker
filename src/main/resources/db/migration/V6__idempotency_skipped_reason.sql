-- Добавляет причину пропуска idempotency-обработки и поддержку ручной разморозки LOCKED записей.
ALTER TABLE ib_idempotency
    ADD COLUMN IF NOT EXISTS skipped_reason VARCHAR(32) NULL;
