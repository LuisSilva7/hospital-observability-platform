-- Módulo 5: regras configuráveis de monitorização

CREATE TABLE monitor_rule (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_id        UUID NOT NULL REFERENCES service (id) ON DELETE CASCADE,
    name              VARCHAR(160) NOT NULL,
    type              VARCHAR(30) NOT NULL,      -- EVENT_MATCH | NO_ACTIVITY | COUNT_THRESHOLD
    severity          VARCHAR(20) NOT NULL,      -- LOW | MEDIUM | HIGH | CRITICAL
    enabled           BOOLEAN NOT NULL DEFAULT TRUE,
    window_minutes    INT,                       -- NO_ACTIVITY: janela de silêncio; COUNT_THRESHOLD: janela de contagem
    threshold         INT,                       -- COUNT_THRESHOLD: nº de eventos
    cooldown_minutes  INT NOT NULL DEFAULT 10,
    last_triggered_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_rule_window CHECK (window_minutes IS NULL OR window_minutes > 0),
    CONSTRAINT ck_rule_threshold CHECK (threshold IS NULL OR threshold > 0),
    CONSTRAINT ck_rule_cooldown CHECK (cooldown_minutes >= 0)
);

CREATE INDEX ix_rule_service ON monitor_rule (service_id);

CREATE TABLE rule_condition (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id        UUID NOT NULL REFERENCES monitor_rule (id) ON DELETE CASCADE,
    field_path     VARCHAR(200) NOT NULL,        -- ex.: "level", "errorCode", "data.code"
    operator       VARCHAR(30) NOT NULL,         -- EQUALS | NOT_EQUALS | CONTAINS | GREATER_THAN | LESS_THAN
    expected_value VARCHAR(500) NOT NULL
);

CREATE INDEX ix_condition_rule ON rule_condition (rule_id);

-- Registo de cada disparo de regra (o Módulo 6 cria alertas a partir daqui)
CREATE TABLE rule_evaluation (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id      UUID NOT NULL REFERENCES monitor_rule (id) ON DELETE CASCADE,
    service_id   UUID NOT NULL,
    triggered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    log_event_id UUID,                           -- log que causou o disparo (null em NO_ACTIVITY)
    details      TEXT
);

CREATE INDEX ix_evaluation_rule ON rule_evaluation (rule_id, triggered_at DESC);
