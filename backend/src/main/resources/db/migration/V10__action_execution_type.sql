-- Passe de correções: guardar o tipo da ação na própria execução, para o
-- histórico do alerta não perder o canal quando a automação é editada/removida
-- (action_id é ON DELETE SET NULL e uma edição recria a AutomationAction).

ALTER TABLE action_execution ADD COLUMN action_type VARCHAR(30);

-- retroativo: preenche a partir da ação ainda existente; o resto fica WEBHOOK
-- (era o único tipo até esta série de extensões)
UPDATE action_execution e
SET action_type = a.type
FROM automation_action a
WHERE e.action_id = a.id;

UPDATE action_execution SET action_type = 'WEBHOOK' WHERE action_type IS NULL;

ALTER TABLE action_execution ALTER COLUMN action_type SET NOT NULL;
