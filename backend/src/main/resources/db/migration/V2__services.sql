-- Módulo 1: serviços monitorizados e as suas API keys

CREATE TABLE service (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                      VARCHAR(120) NOT NULL,
    description               TEXT,
    environment               VARCHAR(30) NOT NULL,
    criticality               VARCHAR(20) NOT NULL,
    active                    BOOLEAN NOT NULL DEFAULT TRUE,
    expected_interval_minutes INT,
    tolerance_minutes         INT,
    last_seen_at              TIMESTAMPTZ,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_service_name UNIQUE (name),
    CONSTRAINT ck_service_interval CHECK (expected_interval_minutes IS NULL OR expected_interval_minutes > 0),
    CONSTRAINT ck_service_tolerance CHECK (tolerance_minutes IS NULL OR tolerance_minutes >= 0)
);

CREATE TABLE service_api_key (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_id   UUID NOT NULL REFERENCES service (id) ON DELETE CASCADE,
    key_hash     VARCHAR(64) NOT NULL,
    prefix       VARCHAR(16) NOT NULL,
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_api_key_hash ON service_api_key (key_hash);
CREATE INDEX ix_api_key_service ON service_api_key (service_id);
