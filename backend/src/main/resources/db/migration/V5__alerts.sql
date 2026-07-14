-- Módulo 6: alertas/incidentes com ciclo de vida e timeline

CREATE TABLE alert (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_id      UUID NOT NULL REFERENCES service (id) ON DELETE CASCADE,
    rule_id         UUID REFERENCES monitor_rule (id) ON DELETE SET NULL,
    title           VARCHAR(200) NOT NULL,
    severity        VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'OPEN',   -- OPEN | ACKNOWLEDGED | RESOLVED
    opened_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    acknowledged_at TIMESTAMPTZ,
    resolved_at     TIMESTAMPTZ
);

CREATE INDEX ix_alert_status ON alert (status, opened_at DESC);
CREATE INDEX ix_alert_service ON alert (service_id);
CREATE INDEX ix_alert_rule ON alert (rule_id);

CREATE TABLE alert_event (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_id    UUID NOT NULL REFERENCES alert (id) ON DELETE CASCADE,
    type        VARCHAR(30) NOT NULL,   -- CREATED | TRIGGER_REPEATED | ACKNOWLEDGED | RESOLVED
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_alert_event_alert ON alert_event (alert_id, created_at);

CREATE TABLE alert_log_link (
    alert_id     UUID NOT NULL REFERENCES alert (id) ON DELETE CASCADE,
    log_event_id UUID NOT NULL REFERENCES log_event (id) ON DELETE CASCADE,
    PRIMARY KEY (alert_id, log_event_id)
);
