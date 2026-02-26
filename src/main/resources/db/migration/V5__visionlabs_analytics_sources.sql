-- V5: Источники событий/результатов аналитики VisionLabs (LUNA PLATFORM)
--
-- Назначение:
-- 1) обеспечить надёжное получение событий, сохранённых в Events (callback type: luna-event);
-- 2) сохранить checkpoint (последний обработанный event_id) для устойчивости при рестартах.
--
-- Важно: эта таблица НЕ хранит изображения и НЕ хранит секреты.

CREATE TABLE IF NOT EXISTS ib_visionlabs_events_checkpoint (
    source_id            VARCHAR(128) PRIMARY KEY,
    last_event_id        VARCHAR(256) NULL,
    updated_at           TIMESTAMP NOT NULL DEFAULT NOW()
);
