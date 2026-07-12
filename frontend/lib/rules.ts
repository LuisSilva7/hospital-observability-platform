export type RuleType = "EVENT_MATCH" | "NO_ACTIVITY" | "COUNT_THRESHOLD";
export type Severity = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
export type Operator =
  | "EQUALS"
  | "NOT_EQUALS"
  | "CONTAINS"
  | "GREATER_THAN"
  | "LESS_THAN";

export type Condition = {
  fieldPath: string;
  operator: Operator;
  expectedValue: string;
};

export type Rule = {
  id: string;
  serviceId: string;
  serviceName: string;
  name: string;
  type: RuleType;
  severity: Severity;
  enabled: boolean;
  windowMinutes: number | null;
  threshold: number | null;
  cooldownMinutes: number;
  lastTriggeredAt: string | null;
  createdAt: string;
  conditions: Condition[];
};

export type Evaluation = {
  id: string;
  triggeredAt: string;
  logEventId: string | null;
  details: string;
};

export const RULE_TYPE_INFO: Record<
  RuleType,
  { label: string; description: string }
> = {
  EVENT_MATCH: {
    label: "Correspondência de evento",
    description:
      "Dispara quando chega um log que satisfaz todas as condições (ex.: level = ERROR).",
  },
  NO_ACTIVITY: {
    label: "Ausência de atividade",
    description:
      "Dispara quando o serviço não envia qualquer log durante X minutos.",
  },
  COUNT_THRESHOLD: {
    label: "Contagem acima do limite",
    description:
      "Dispara quando chegam N ou mais logs correspondentes numa janela de tempo.",
  },
};

export const SEVERITY_LABELS: Record<Severity, string> = {
  LOW: "Baixa",
  MEDIUM: "Média",
  HIGH: "Alta",
  CRITICAL: "Crítica",
};

export const OPERATOR_LABELS: Record<Operator, string> = {
  EQUALS: "é igual a",
  NOT_EQUALS: "é diferente de",
  CONTAINS: "contém",
  GREATER_THAN: "é maior que",
  LESS_THAN: "é menor que",
};

export const severityColor = (s: Severity) =>
  ({
    LOW: "bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-300",
    MEDIUM: "bg-blue-100 text-blue-700 dark:bg-blue-950 dark:text-blue-300",
    HIGH: "bg-amber-100 text-amber-700 dark:bg-amber-950 dark:text-amber-300",
    CRITICAL: "bg-red-100 text-red-700 dark:bg-red-950 dark:text-red-300",
  })[s];
