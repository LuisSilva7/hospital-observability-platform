-- Baseline do Módulo 0: valida que o Flyway funciona ponta a ponta.
-- As tabelas de domínio (Service, LogEvent, MonitorRule, ...) chegam nos módulos seguintes.
CREATE TABLE app_info (
    id          SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    schema_note TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO app_info (schema_note) VALUES ('Hospital Observability Platform - baseline schema');
