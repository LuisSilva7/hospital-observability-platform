-- Módulo 2: eventos de log ingeridos (payload original preservado em JSONB)

CREATE TABLE log_event (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_id      UUID NOT NULL REFERENCES service (id) ON DELETE CASCADE,
    received_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    event_timestamp TIMESTAMPTZ,
    level           VARCHAR(20),
    message         TEXT,
    event_type      VARCHAR(120),
    payload         JSONB NOT NULL
);

CREATE INDEX ix_log_event_service_received ON log_event (service_id, received_at DESC);
CREATE INDEX ix_log_event_level ON log_event (level);
CREATE INDEX ix_log_event_payload ON log_event USING GIN (payload);
