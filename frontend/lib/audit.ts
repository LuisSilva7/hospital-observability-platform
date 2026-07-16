export type AuditActor = "USER" | "SYSTEM";

export type AuditEntry = {
  id: string;
  action: string;
  entityType: string;
  entityId: string | null;
  actor: AuditActor;
  details: Record<string, unknown> | null;
  createdAt: string;
};

export type AuditPage = {
  content: AuditEntry[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type Settings = {
  llm: {
    configured: boolean;
    provider: string;
    model: string;
    redactedFields: string[];
  };
  n8n: {
    configured: boolean;
    webhookBaseUrl: string | null;
  };
};

export const ENTITY_LABELS: Record<string, string> = {
  SERVICE: "Serviço",
  RULE: "Regra",
  ALERT: "Alerta",
  AUTOMATION: "Automação",
  AI_ANALYSIS: "Análise IA",
};

export const ACTION_LABELS: Record<string, string> = {
  SERVICE_CREATED: "Serviço criado",
  SERVICE_UPDATED: "Serviço atualizado",
  SERVICE_ACTIVATED: "Serviço ativado",
  SERVICE_DEACTIVATED: "Serviço desativado",
  SERVICE_DELETED: "Serviço eliminado",
  SERVICE_KEY_REGENERATED: "API key regenerada",
  RULE_CREATED: "Regra criada",
  RULE_UPDATED: "Regra atualizada",
  RULE_ENABLED: "Regra ativada",
  RULE_DISABLED: "Regra desativada",
  RULE_DELETED: "Regra eliminada",
  ALERT_CREATED: "Alerta criado",
  ALERT_ACKNOWLEDGED: "Alerta reconhecido",
  ALERT_RESOLVED: "Alerta resolvido",
  AUTOMATION_CREATED: "Automação criada",
  AUTOMATION_UPDATED: "Automação atualizada",
  AUTOMATION_ENABLED: "Automação ativada",
  AUTOMATION_DISABLED: "Automação desativada",
  AUTOMATION_DELETED: "Automação eliminada",
  AUTOMATION_TESTED: "Automação testada",
  ACTION_EXECUTED: "Webhook executado",
  AI_ANALYSIS_REQUESTED: "Análise de IA pedida",
};
