-- Módulo 7: automações (ações executadas quando uma regra cria um alerta)

CREATE TABLE automation (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id    UUID NOT NULL REFERENCES monitor_rule (id) ON DELETE CASCADE,
    name       VARCHAR(160) NOT NULL,
    enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_automation_rule ON automation (rule_id);

CREATE TABLE automation_action (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    automation_id UUID NOT NULL REFERENCES automation (id) ON DELETE CASCADE,
    type          VARCHAR(30) NOT NULL,          -- WEBHOOK (AI_ANALYSIS no M8)
    config        JSONB NOT NULL,                -- url, method, headers, payloadTemplate
    order_index   INT NOT NULL DEFAULT 0
);

CREATE INDEX ix_action_automation ON automation_action (automation_id);

CREATE TABLE action_execution (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    action_id     UUID REFERENCES automation_action (id) ON DELETE SET NULL,
    alert_id      UUID REFERENCES alert (id) ON DELETE CASCADE,
    status        VARCHAR(20) NOT NULL,          -- SUCCESS | FAILED
    attempts      INT NOT NULL DEFAULT 1,
    response_code INT,
    response_body TEXT,
    error         TEXT,
    executed_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_execution_alert ON action_execution (alert_id, executed_at DESC);
