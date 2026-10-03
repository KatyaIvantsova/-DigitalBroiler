-- S2-01: структура производства (площадка → птичник → зона), кроссы, партии, ежедневный учёт и взвешивания.
-- S2-05: назначение пользователей на птичники. S2-06: журнал действий. S2-07: привязка датчиков к птичнику и зоне.
-- Модель описана в docs/sprint1/02-data-model.md.

CREATE TABLE sites (
    id         UUID PRIMARY KEY,
    code       VARCHAR(32)  NOT NULL UNIQUE,
    name       VARCHAR(255) NOT NULL,
    address    TEXT,
    timezone   VARCHAR(64)  NOT NULL DEFAULT 'Europe/Samara',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE houses (
    id             UUID PRIMARY KEY,
    site_id        UUID         NOT NULL REFERENCES sites (id),
    code           VARCHAR(32)  NOT NULL,
    name           VARCHAR(255) NOT NULL,
    area_m2        DOUBLE PRECISION,
    capacity_heads INTEGER,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_houses_site_code UNIQUE (site_id, code)
);

CREATE TABLE zones (
    id         UUID PRIMARY KEY,
    house_id   UUID         NOT NULL REFERENCES houses (id) ON DELETE CASCADE,
    code       VARCHAR(32)  NOT NULL,
    name       VARCHAR(255) NOT NULL,
    -- Координаты зоны на схеме птичника (S5-03), JSON: {"x":0,"y":0,"w":20,"h":100} в процентах
    layout     TEXT,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_zones_house_code UNIQUE (house_id, code)
);

CREATE TABLE breeds (
    code VARCHAR(32) PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);

INSERT INTO breeds (code, name) VALUES
    ('ROSS_308', 'Ross 308'),
    ('COBB_500', 'Cobb 500');

CREATE TABLE flocks (
    id                     UUID PRIMARY KEY,
    house_id               UUID        NOT NULL REFERENCES houses (id),
    code                   VARCHAR(32) NOT NULL UNIQUE,
    breed_code             VARCHAR(32) NOT NULL REFERENCES breeds (code),
    placed_at              DATE        NOT NULL,
    placed_heads           INTEGER     NOT NULL CHECK (placed_heads > 0),
    placed_avg_weight_g    DOUBLE PRECISION,
    sex                    VARCHAR(16) NOT NULL DEFAULT 'MIXED',
    hatchery               VARCHAR(255),
    target_age_days        INTEGER,
    status                 VARCHAR(16) NOT NULL,
    closed_at              DATE,
    shipped_heads          INTEGER,
    shipped_live_weight_kg DOUBLE PRECISION,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ,
    CONSTRAINT ck_flocks_status CHECK (status IN ('PLANNED', 'ACTIVE', 'CLOSED')),
    CONSTRAINT ck_flocks_sex CHECK (sex IN ('MIXED', 'MALE', 'FEMALE'))
);

-- В птичнике одновременно не больше одной активной партии
CREATE UNIQUE INDEX ux_flocks_active_house ON flocks (house_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_flocks_house ON flocks (house_id, placed_at DESC);

CREATE TABLE daily_records (
    id               UUID PRIMARY KEY,
    flock_id         UUID        NOT NULL REFERENCES flocks (id) ON DELETE CASCADE,
    record_date      DATE        NOT NULL,
    mortality_heads  INTEGER     NOT NULL DEFAULT 0 CHECK (mortality_heads >= 0),
    culled_heads     INTEGER     NOT NULL DEFAULT 0 CHECK (culled_heads >= 0),
    feed_consumed_kg DOUBLE PRECISION CHECK (feed_consumed_kg >= 0),
    water_consumed_l DOUBLE PRECISION CHECK (water_consumed_l >= 0),
    comment          TEXT,
    created_by       UUID REFERENCES users (id),
    updated_by       UUID REFERENCES users (id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ,
    CONSTRAINT ux_daily_records_flock_date UNIQUE (flock_id, record_date)
);

CREATE TABLE weighings (
    id             UUID PRIMARY KEY,
    flock_id       UUID             NOT NULL REFERENCES flocks (id) ON DELETE CASCADE,
    zone_id        UUID REFERENCES zones (id) ON DELETE SET NULL,
    weighed_at     TIMESTAMPTZ      NOT NULL,
    sample_heads   INTEGER          NOT NULL CHECK (sample_heads > 0),
    avg_weight_g   DOUBLE PRECISION NOT NULL CHECK (avg_weight_g > 0),
    uniformity_pct DOUBLE PRECISION CHECK (uniformity_pct BETWEEN 0 AND 100),
    method         VARCHAR(16)      NOT NULL DEFAULT 'MANUAL',
    created_by     UUID REFERENCES users (id),
    created_at     TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CONSTRAINT ck_weighings_method CHECK (method IN ('MANUAL', 'SCALE', 'VIDEO'))
);

CREATE INDEX idx_weighings_flock ON weighings (flock_id, weighed_at);

CREATE TABLE user_houses (
    user_id  UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    house_id UUID NOT NULL REFERENCES houses (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, house_id)
);

CREATE TABLE audit_log (
    id          UUID PRIMARY KEY,
    entity_type VARCHAR(32)  NOT NULL,
    entity_id   VARCHAR(64)  NOT NULL,
    action      VARCHAR(32)  NOT NULL,
    actor_id    UUID,
    actor_name  VARCHAR(255),
    summary     TEXT         NOT NULL,
    changes     TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_entity ON audit_log (entity_type, entity_id, created_at DESC);
CREATE INDEX idx_audit_log_created_at ON audit_log (created_at DESC);

ALTER TABLE sensors ADD COLUMN house_id UUID REFERENCES houses (id) ON DELETE SET NULL;
ALTER TABLE sensors ADD COLUMN zone_id UUID REFERENCES zones (id) ON DELETE SET NULL;

ALTER TABLE incidents ADD COLUMN house_id UUID REFERENCES houses (id) ON DELETE SET NULL;
ALTER TABLE incidents ADD COLUMN zone_id UUID REFERENCES zones (id) ON DELETE SET NULL;
ALTER TABLE incidents ADD COLUMN flock_id UUID REFERENCES flocks (id) ON DELETE SET NULL;
ALTER TABLE incidents ADD COLUMN sensor_id UUID REFERENCES sensors (id) ON DELETE SET NULL;
ALTER TABLE incidents ADD COLUMN rule_code VARCHAR(64);

CREATE INDEX idx_incidents_house ON incidents (house_id);
CREATE INDEX idx_incidents_flock ON incidents (flock_id);

-- Текущая инсталляция: «Ферма 1», «Птичник 4» с пятью зонами — к ним уже привязаны датчики из V5, V6, V11.
INSERT INTO sites (id, code, name, timezone)
VALUES ('a0000000-0000-0000-0000-000000000001', 'FARM-1', 'Ферма 1', 'Europe/Samara');

INSERT INTO houses (id, site_id, code, name, area_m2, capacity_heads)
VALUES ('b0000000-0000-0000-0000-000000000004', 'a0000000-0000-0000-0000-000000000001', '1-04', 'Птичник 4', 1800, 32000);

INSERT INTO zones (id, house_id, code, name, layout) VALUES
    ('c0000000-0000-0000-0000-000000000401', 'b0000000-0000-0000-0000-000000000004', 'Z1', 'Зона 1', '{"x":0,"y":0,"w":20,"h":100}'),
    ('c0000000-0000-0000-0000-000000000402', 'b0000000-0000-0000-0000-000000000004', 'Z2', 'Зона 2', '{"x":20,"y":0,"w":20,"h":100}'),
    ('c0000000-0000-0000-0000-000000000403', 'b0000000-0000-0000-0000-000000000004', 'Z3', 'Зона 3', '{"x":40,"y":0,"w":20,"h":100}'),
    ('c0000000-0000-0000-0000-000000000404', 'b0000000-0000-0000-0000-000000000004', 'Z4', 'Зона 4', '{"x":60,"y":0,"w":20,"h":100}'),
    ('c0000000-0000-0000-0000-000000000405', 'b0000000-0000-0000-0000-000000000004', 'Z5', 'Зона 5', '{"x":80,"y":0,"w":20,"h":100}');

UPDATE sensors SET house_id = 'b0000000-0000-0000-0000-000000000004' WHERE building = 'Птичник 4';

UPDATE sensors s
SET zone_id = z.id
FROM zones z
WHERE s.house_id = z.house_id
  AND lower(s.name) LIKE '%зона ' || substring(z.code FROM 2);

UPDATE incidents SET house_id = 'b0000000-0000-0000-0000-000000000004' WHERE house = 'Птичник 4';
