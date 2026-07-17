"use client";

import { useCallback, useEffect, useState } from "react";
import { apiFetch } from "@/lib/api";
import {
  ACTION_LABELS,
  AuditEntry,
  AuditPage,
  ENTITY_LABELS,
  Settings,
} from "@/lib/audit";

const ENTITY_FILTERS = [
  "",
  "SERVICE",
  "RULE",
  "ALERT",
  "AUTOMATION",
  "AI_ANALYSIS",
  "LOG",
  "SIMULATOR",
];

type SimulatorProfile = {
  profile: string;
  serviceName: string | null;
  serviceId: string | null;
  scenario: string;
  intervalSeconds: number | null;
  pendingScenario: string | null;
};

type SimulatorState = {
  connected: boolean;
  lastSeenAt: string | null;
  profiles: SimulatorProfile[];
};

const SCENARIO_LABELS: Record<string, string> = {
  normal: "Normal",
  "error-spike": "Pico de erros",
  latency: "Latência alta",
  silence: "Silêncio",
};

export default function Page() {
  const [settings, setSettings] = useState<Settings | null>(null);
  const [audit, setAudit] = useState<AuditPage | null>(null);
  const [entityType, setEntityType] = useState("");
  const [page, setPage] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [simulator, setSimulator] = useState<SimulatorState | null>(null);

  useEffect(() => {
    apiFetch<Settings>("/api/settings")
      .then(setSettings)
      .catch(() => setError("Não foi possível contactar o backend."));
  }, []);

  useEffect(() => {
    const loadSimulator = () =>
      apiFetch<SimulatorState>("/api/simulator").then(setSimulator).catch(() => {});
    loadSimulator();
    // o estado "ligado" muda quando o simulador deixa de reportar — polling curto
    const interval = setInterval(loadSimulator, 5_000);
    return () => clearInterval(interval);
  }, []);

  const setScenario = async (profile: string, scenario: string) => {
    const state = await apiFetch<SimulatorState>(
      `/api/simulator/scenarios/${encodeURIComponent(profile)}`,
      { method: "PUT", body: JSON.stringify({ scenario }) }
    ).catch(() => null);
    if (state) setSimulator(state);
  };

  const loadAudit = useCallback(() => {
    const params = new URLSearchParams({ page: String(page), size: "25" });
    if (entityType) params.set("entityType", entityType);
    apiFetch<AuditPage>(`/api/audit?${params}`)
      .then(setAudit)
      .catch(() => {});
  }, [page, entityType]);

  useEffect(() => {
    loadAudit();
  }, [loadAudit]);

  return (
    <div className="max-w-4xl">
      <h2 className="text-2xl font-semibold">Configuração</h2>
      <p className="mt-1 text-sm text-gray-500">
        Estado das integrações e histórico de auditoria.
      </p>

      {error && (
        <p className="mt-4 rounded-md bg-red-50 p-3 text-sm text-red-700 dark:bg-red-950 dark:text-red-300">
          {error}
        </p>
      )}

      <div className="mt-6 grid gap-6 md:grid-cols-2">
        <div className="rounded-lg border border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-900">
          <div className="flex items-center justify-between border-b border-gray-200 px-5 py-3 dark:border-gray-800">
            <h3 className="text-sm font-semibold">LLM (análises de IA)</h3>
            {settings && <StatusBadge ok={settings.llm.configured} />}
          </div>
          <dl className="space-y-2 p-5 text-sm">
            <Row label="Fornecedor" value={settings?.llm.provider ?? "—"} />
            <Row label="Modelo" value={settings?.llm.model ?? "—"} />
            <Row
              label="Campos redigidos"
              value={
                settings && settings.llm.redactedFields.length > 0
                  ? settings.llm.redactedFields.join(", ")
                  : "(nenhum)"
              }
            />
          </dl>
          {settings && !settings.llm.configured && (
            <p className="mx-5 mb-5 rounded-md bg-amber-50 p-3 text-xs text-amber-800 dark:bg-amber-950 dark:text-amber-300">
              Não configurado — define a variável de ambiente{" "}
              <code className="font-mono">LLM_API_KEY</code> no backend (ver{" "}
              <code className="font-mono">.env.example</code>) e reinicia-o. A
              chave nunca é guardada na base de dados.
            </p>
          )}
        </div>

        <div className="rounded-lg border border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-900">
          <div className="flex items-center justify-between border-b border-gray-200 px-5 py-3 dark:border-gray-800">
            <h3 className="text-sm font-semibold">n8n (automações)</h3>
            {settings && <StatusBadge ok={settings.n8n.configured} />}
          </div>
          <dl className="space-y-2 p-5 text-sm">
            <Row
              label="Base URL de webhooks"
              value={settings?.n8n.webhookBaseUrl ?? "(não definido)"}
            />
          </dl>
          <p className="mx-5 mb-5 text-xs text-gray-500">
            Os webhooks são configurados por automação (página Automações). A
            interface do n8n está em{" "}
            <a
              href={settings?.n8n.webhookBaseUrl ?? "http://localhost:5678"}
              target="_blank"
              rel="noreferrer"
              className="text-blue-600 hover:underline"
            >
              {settings?.n8n.webhookBaseUrl ?? "http://localhost:5678"}
            </a>
            .
          </p>
        </div>

        <div className="rounded-lg border border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-900">
          <div className="flex items-center justify-between border-b border-gray-200 px-5 py-3 dark:border-gray-800">
            <h3 className="text-sm font-semibold">Retenção de logs</h3>
            {settings && <StatusBadge ok={settings.retention.enabled} />}
          </div>
          <dl className="space-y-2 p-5 text-sm">
            <Row
              label="Logs mantidos por"
              value={
                settings
                  ? settings.retention.enabled
                    ? `${settings.retention.logDays} dia(s)`
                    : "sem limite (limpeza desativada)"
                  : "—"
              }
            />
          </dl>
          <p className="mx-5 mb-5 text-xs text-gray-500">
            Limpeza automática de hora a hora. Logs associados a alertas nunca
            são apagados (ficam como evidência). Configurável via{" "}
            <code className="font-mono">LOG_RETENTION_DAYS</code> (0 desativa).
          </p>
        </div>

        <div className="rounded-lg border border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-900">
          <div className="flex items-center justify-between border-b border-gray-200 px-5 py-3 dark:border-gray-800">
            <h3 className="text-sm font-semibold">Email (SMTP)</h3>
            {settings && <StatusBadge ok={settings.email.configured} />}
          </div>
          <dl className="space-y-2 p-5 text-sm">
            <Row label="Servidor" value={settings?.email.host ?? "(não definido)"} />
            <Row label="Remetente" value={settings?.email.from ?? "—"} />
          </dl>
          <p className="mx-5 mb-5 text-xs text-gray-500">
            Usado pelas automações do tipo Email. Configura{" "}
            <code className="font-mono">SMTP_HOST</code>,{" "}
            <code className="font-mono">SMTP_USERNAME</code>,{" "}
            <code className="font-mono">SMTP_PASSWORD</code> e{" "}
            <code className="font-mono">SMTP_FROM</code> no backend.
          </p>
        </div>

        <div className="rounded-lg border border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-900">
          <div className="flex items-center justify-between border-b border-gray-200 px-5 py-3 dark:border-gray-800">
            <h3 className="text-sm font-semibold">Simulador</h3>
            {simulator && (
              <span
                className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${
                  simulator.connected
                    ? "bg-green-100 text-green-700 dark:bg-green-950 dark:text-green-300"
                    : "bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400"
                }`}
              >
                {simulator.connected ? "● Ligado" : "○ Desligado"}
              </span>
            )}
          </div>
          {!simulator?.connected ? (
            <p className="p-5 text-xs text-gray-500">
              O simulador não está a reportar. Arranca-o com{" "}
              <code className="font-mono">cd simulator && node index.js</code> —
              os cenários passam a poder ser mudados aqui em tempo real.
            </p>
          ) : (
            <ul className="divide-y divide-gray-200 dark:divide-gray-800">
              {simulator.profiles.map((p) => (
                <li
                  key={p.profile}
                  className="flex items-center justify-between gap-3 px-5 py-3 text-sm"
                >
                  <div className="min-w-0">
                    <p className="truncate font-medium">
                      {p.serviceName ?? p.profile}
                    </p>
                    <p className="text-xs text-gray-500">
                      {p.profile}
                      {p.intervalSeconds != null && ` · ~${p.intervalSeconds}s`}
                      {p.pendingScenario && (
                        <span className="ml-1 text-amber-600 dark:text-amber-400">
                          · a aplicar…
                        </span>
                      )}
                    </p>
                  </div>
                  <select
                    value={p.pendingScenario ?? p.scenario}
                    onChange={(e) => setScenario(p.profile, e.target.value)}
                    className="shrink-0 rounded-md border border-gray-300 bg-white px-2 py-1 text-xs dark:border-gray-700 dark:bg-gray-800"
                  >
                    {Object.entries(SCENARIO_LABELS).map(([value, label]) => (
                      <option key={value} value={value}>
                        {label}
                      </option>
                    ))}
                  </select>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>

      <div className="mt-6 rounded-lg border border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-900">
        <div className="flex items-center justify-between border-b border-gray-200 px-5 py-3 dark:border-gray-800">
          <h3 className="text-sm font-semibold">
            Auditoria{audit && ` (${audit.totalElements})`}
          </h3>
          <select
            value={entityType}
            onChange={(e) => {
              setEntityType(e.target.value);
              setPage(0);
            }}
            className="rounded-md border border-gray-300 bg-white px-2 py-1 text-xs dark:border-gray-700 dark:bg-gray-800"
          >
            {ENTITY_FILTERS.map((t) => (
              <option key={t} value={t}>
                {t === "" ? "Todas as entidades" : (ENTITY_LABELS[t] ?? t)}
              </option>
            ))}
          </select>
        </div>

        {!audit || audit.content.length === 0 ? (
          <p className="px-5 py-8 text-center text-sm text-gray-400">
            Sem registos de auditoria.
          </p>
        ) : (
          <ul className="divide-y divide-gray-200 dark:divide-gray-800">
            {audit.content.map((entry) => (
              <AuditRow key={entry.id} entry={entry} />
            ))}
          </ul>
        )}

        {audit && audit.totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-gray-200 px-5 py-3 text-xs dark:border-gray-800">
            <button
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className="rounded-md border border-gray-300 px-3 py-1 disabled:opacity-40 dark:border-gray-700"
            >
              ← Anterior
            </button>
            <span className="text-gray-500">
              Página {audit.page + 1} de {audit.totalPages}
            </span>
            <button
              onClick={() => setPage((p) => p + 1)}
              disabled={audit.page + 1 >= audit.totalPages}
              className="rounded-md border border-gray-300 px-3 py-1 disabled:opacity-40 dark:border-gray-700"
            >
              Próxima →
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

function StatusBadge({ ok }: { ok: boolean }) {
  return (
    <span
      className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${
        ok
          ? "bg-green-100 text-green-700 dark:bg-green-950 dark:text-green-300"
          : "bg-amber-100 text-amber-700 dark:bg-amber-950 dark:text-amber-300"
      }`}
    >
      {ok ? "✓ Configurado" : "✗ Não configurado"}
    </span>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-4">
      <dt className="text-gray-500">{label}</dt>
      <dd className="text-right font-medium">{value}</dd>
    </div>
  );
}

function AuditRow({ entry }: { entry: AuditEntry }) {
  const details = entry.details
    ? Object.entries(entry.details)
        .map(([k, v]) => `${k}: ${String(v)}`)
        .join(" · ")
    : null;
  return (
    <li className="flex items-start justify-between gap-4 px-5 py-3 text-sm">
      <div className="min-w-0">
        <p>
          <span className="font-medium">
            {ACTION_LABELS[entry.action] ?? entry.action}
          </span>{" "}
          <span className="text-xs text-gray-400">
            {ENTITY_LABELS[entry.entityType] ?? entry.entityType}
          </span>
        </p>
        {details && (
          <p className="mt-0.5 truncate text-xs text-gray-500">{details}</p>
        )}
      </div>
      <div className="shrink-0 text-right">
        <span
          className={`rounded-full px-2 py-0.5 text-xs ${
            entry.actor === "USER"
              ? "bg-blue-100 text-blue-700 dark:bg-blue-950 dark:text-blue-300"
              : "bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-300"
          }`}
        >
          {entry.actor === "USER" ? "Operador" : "Sistema"}
        </span>
        <p className="mt-1 text-xs text-gray-400">
          {new Date(entry.createdAt).toLocaleString("pt-PT")}
        </p>
      </div>
    </li>
  );
}
