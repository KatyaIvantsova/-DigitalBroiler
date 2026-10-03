-- S3-01: справочник норм по показателю, кроссу и возрасту с версионированием.
-- Строка нормы не меняется: правка закрывает текущую версию (valid_to) и добавляет новую (version + 1).
-- Начальная таблица (S1-03 + S2-11) загружается при старте из classpath:norms/norms-v1.csv, если справочник пуст.
CREATE TABLE norms (
    id           UUID PRIMARY KEY,
    breed_code   VARCHAR(32) REFERENCES breeds (code),
    metric       VARCHAR(32)  NOT NULL,
    age_from_day INTEGER      NOT NULL CHECK (age_from_day >= 0),
    age_to_day   INTEGER      NOT NULL,
    min_value    DOUBLE PRECISION,
    target_value DOUBLE PRECISION,
    max_value    DOUBLE PRECISION,
    unit         VARCHAR(16)  NOT NULL,
    source       TEXT         NOT NULL,
    version      INTEGER      NOT NULL DEFAULT 1,
    -- Строка, которую эта версия заменила (цепочка версий одной нормы)
    previous_id  UUID REFERENCES norms (id),
    valid_from   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    valid_to     TIMESTAMPTZ,
    changed_by   VARCHAR(255),
    change_comment TEXT,
    CONSTRAINT ck_norms_age CHECK (age_to_day >= age_from_day)
);

CREATE INDEX idx_norms_current ON norms (metric, breed_code, age_from_day) WHERE valid_to IS NULL;

-- S2-08 / S3-03: правила движка. Пороги — данные: правка применяется со следующего цикла проверки.
CREATE TABLE rules (
    code             VARCHAR(64) PRIMARY KEY,
    name             VARCHAR(255) NOT NULL,
    metric           VARCHAR(32),
    sensor_type      VARCHAR(32),
    incident_type    VARCHAR(64)  NOT NULL,
    warn_minutes     INTEGER      NOT NULL DEFAULT 30,
    critical_delta   DOUBLE PRECISION,
    critical_minutes INTEGER      NOT NULL DEFAULT 15,
    clear_minutes    INTEGER      NOT NULL DEFAULT 15,
    enabled          BOOLEAN      NOT NULL DEFAULT TRUE
);

INSERT INTO rules (code, name, metric, sensor_type, incident_type, warn_minutes, critical_delta, critical_minutes, clear_minutes) VALUES
    ('MICROCLIMATE_TEMPERATURE', 'Температура вне нормы', 'TEMPERATURE', 'TEMPERATURE', 'MICROCLIMATE', 30, 1.0, 15, 15),
    ('MICROCLIMATE_HUMIDITY', 'Влажность вне нормы', 'HUMIDITY', 'HUMIDITY', 'MICROCLIMATE', 60, 10.0, 30, 15),
    ('MICROCLIMATE_CO2', 'CO2 выше нормы', 'CO2', 'CO2', 'MICROCLIMATE', 30, 500.0, 15, 15),
    ('MICROCLIMATE_AMMONIA', 'Аммиак выше нормы', 'AMMONIA', 'AMMONIA', 'MICROCLIMATE', 30, 10.0, 15, 15),
    ('LIGHTING_INTENSITY', 'Освещённость вне нормы', 'LIGHT_INTENSITY', 'LIGHT', 'LIGHTING_ILLUMINANCE_LOW', 30, NULL, 15, 15),
    ('LIGHTING_PROGRAM', 'Нарушение программы освещения', 'LIGHT_HOURS', 'LIGHT', 'LIGHTING_SCHEDULE_DEVIATION', 0, NULL, 0, 0),
    ('SENSOR_NO_DATA', 'Нет данных от датчика', NULL, NULL, 'SENSOR_NO_DATA', 30, NULL, 0, 0);

-- Ключ дедупликации автоматических инцидентов: правило + датчик / птичник / партия и дата (S3-04)
ALTER TABLE incidents ADD COLUMN dedup_key VARCHAR(160);
CREATE INDEX idx_incidents_dedup_open ON incidents (dedup_key) WHERE status IN ('OPEN', 'IN_PROGRESS');
