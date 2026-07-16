-- Módulo 8: análises de IA associadas a alertas

CREATE TABLE ai_analysis (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_id       UUID NOT NULL REFERENCES alert (id) ON DELETE CASCADE,
    status         VARCHAR(20) NOT NULL,           -- SUCCESS | FAILED
    provider       VARCHAR(40) NOT NULL,           -- anthropic
    model          VARCHAR(80),
    prompt_version VARCHAR(20) NOT NULL,
    input_log_ids  JSONB NOT NULL DEFAULT '[]',    -- ids dos logs enviados ao modelo
    output         JSONB,                          -- summary, probableCause, evidence[], recommendations[]
    error          TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_ai_analysis_alert ON ai_analysis (alert_id, created_at DESC);
