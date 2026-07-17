export type Stat = {
  count: number;
  avgMs: number | null;
  p50Ms: number | null;
  p95Ms: number | null;
  maxMs: number | null;
};

export type Metrics = {
  windowDays: number | null;
  counts: {
    alerts: number;
    openAlerts: number;
    acknowledgedAlerts: number;
    resolvedAlerts: number;
    logs: number;
    actionExecutions: number;
    aiAnalyses: number;
  };
  detection: Stat;
  notification: Stat;
  mtta: Stat;
  mttr: Stat;
};

/** Formata milissegundos para leitura humana (pt-PT). */
export function formatMs(ms: number | null): string {
  if (ms == null) return "—";
  if (ms < 1000) return `${ms} ms`;
  if (ms < 60_000) return `${(ms / 1000).toLocaleString("pt-PT", { maximumFractionDigits: 1 })} s`;
  if (ms < 3_600_000) return `${(ms / 60_000).toLocaleString("pt-PT", { maximumFractionDigits: 1 })} min`;
  return `${(ms / 3_600_000).toLocaleString("pt-PT", { maximumFractionDigits: 1 })} h`;
}
