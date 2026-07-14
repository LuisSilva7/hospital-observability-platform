import { Severity } from "@/lib/rules";

export type AlertStatus = "OPEN" | "ACKNOWLEDGED" | "RESOLVED";

export type Alert = {
  id: string;
  serviceId: string;
  serviceName: string;
  ruleId: string | null;
  title: string;
  severity: Severity;
  status: AlertStatus;
  openedAt: string;
  acknowledgedAt: string | null;
  resolvedAt: string | null;
};

export type TimelineEvent = {
  id: string;
  type: "CREATED" | "TRIGGER_REPEATED" | "ACKNOWLEDGED" | "RESOLVED";
  description: string | null;
  createdAt: string;
};

export type LinkedLog = {
  id: string;
  receivedAt: string;
  level: string | null;
  message: string | null;
  payload: string;
};

export type ExecutionSummary = {
  id: string;
  status: "SUCCESS" | "FAILED";
  attempts: number;
  responseCode: number | null;
  error: string | null;
  executedAt: string;
};

export type AlertDetail = {
  alert: Alert;
  timeline: TimelineEvent[];
  logs: LinkedLog[];
  executions: ExecutionSummary[];
};

export const STATUS_LABELS: Record<AlertStatus, string> = {
  OPEN: "Aberto",
  ACKNOWLEDGED: "Reconhecido",
  RESOLVED: "Resolvido",
};

export const statusColor = (s: AlertStatus) =>
  ({
    OPEN: "bg-red-100 text-red-700 dark:bg-red-950 dark:text-red-300",
    ACKNOWLEDGED:
      "bg-amber-100 text-amber-700 dark:bg-amber-950 dark:text-amber-300",
    RESOLVED:
      "bg-green-100 text-green-700 dark:bg-green-950 dark:text-green-300",
  })[s];

export const TIMELINE_ICONS: Record<TimelineEvent["type"], string> = {
  CREATED: "🔔",
  TRIGGER_REPEATED: "🔁",
  ACKNOWLEDGED: "👁",
  RESOLVED: "✅",
};
