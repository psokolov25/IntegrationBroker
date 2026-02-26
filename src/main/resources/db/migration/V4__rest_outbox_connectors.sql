-- Добавление поддержки "коннекторов" для REST outbox.
--
-- Ключевая идея:
-- 1) Секреты/авторизация НЕ должны сохраняться в outbox-таблице.
-- 2) В outbox сохраняются только: connector_id + path + (санитизированные) заголовки + body.
-- 3) Базовый URL и политика авторизации берутся из runtime-конфига в момент отправки.

ALTER TABLE ib_rest_outbox
    ADD COLUMN IF NOT EXISTS connector_id VARCHAR(100);

ALTER TABLE ib_rest_outbox
    ADD COLUMN IF NOT EXISTS path VARCHAR(2000);

-- URL остаётся для обратной совместимости и админ-диагностики.
-- Если connector_id задан — при отправке используется baseUrl из runtime-конфига и поле path.
