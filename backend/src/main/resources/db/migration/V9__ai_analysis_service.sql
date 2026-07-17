-- Extensão E8: análises de IA ao nível do serviço (sem alerta associado)

ALTER TABLE ai_analysis ALTER COLUMN alert_id DROP NOT NULL;
ALTER TABLE ai_analysis ADD COLUMN service_id UUID REFERENCES service (id) ON DELETE CASCADE;

CREATE INDEX ix_ai_analysis_service ON ai_analysis (service_id, created_at DESC);

-- preencher service_id nas análises de alertas já existentes
UPDATE ai_analysis a
SET service_id = al.service_id
FROM alert al
WHERE a.alert_id = al.id;
