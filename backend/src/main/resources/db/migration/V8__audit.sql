-- Módulo 9: auditoria mínima (quem/que processo fez o quê e quando)

CREATE TABLE audit_entry (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    action      VARCHAR(60) NOT NULL,     -- ex.: RULE_CREATED, ALERT_RESOLVED, ACTION_EXECUTED
    entity_type VARCHAR(40) NOT NULL,     -- SERVICE | RULE | ALERT | AUTOMATION | AI_ANALYSIS
    entity_id   UUID,
    actor       VARCHAR(20) NOT NULL,     -- USER (via API/UI) | SYSTEM (motor de regras, executor)
    details     JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_audit_created ON audit_entry (created_at DESC);
CREATE INDEX ix_audit_entity ON audit_entry (entity_type, entity_id);
