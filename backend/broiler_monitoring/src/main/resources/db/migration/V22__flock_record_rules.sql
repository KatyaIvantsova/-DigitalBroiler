-- Правила по ежедневному учёту (S4-05, docs/sprint2/08-rules-engine-spec.md п. 6).
-- warn_delta / critical_delta: для падежа — во сколько раз выше нормы дня, для корма и воды — падение к прошлым суткам, %.
ALTER TABLE rules ADD COLUMN warn_delta DOUBLE PRECISION;

INSERT INTO rules (code, name, metric, sensor_type, incident_type, warn_minutes, warn_delta, critical_delta, critical_minutes, clear_minutes) VALUES
    ('FLOCK_MORTALITY_DAILY', 'Падёж за сутки выше нормы', 'MORTALITY', NULL, 'FLOCK_HEALTH', 0, 1.0, 2.0, 0, 0),
    ('FEED_DROP', 'Падение потребления корма', 'FEED_INTAKE', NULL, 'FEEDING', 0, 10.0, 20.0, 0, 0),
    ('WATER_DROP', 'Падение потребления воды', 'WATER_INTAKE', NULL, 'WATER_SUPPLY', 0, 10.0, 20.0, 0, 0);
