"use client";

import Link from "next/link";
import { use, useCallback, useEffect, useState } from "react";
import { apiFetch } from "@/lib/api";
import AiAnalysisPanel from "@/components/AiAnalysisPanel";
import {
  AlertDetail,
  EXECUTION_LABELS,
  LinkedLog,
  STATUS_LABELS,
  statusColor,
  TIMELINE_ICONS,
} from "@/lib/alerts";
import { SEVERITY_LABELS, severityColor } from "@/lib/rules";
import { useLiveRefresh } from "@/lib/useLiveRefresh";

export default function AlertDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = use(params);
  const [detail, setDetail] = useState<AlertDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [selectedLog, setSelectedLog] = useState<LinkedLog | null>(null);

  const load = useCallback(() => {
    apiFetch<AlertDetail>(`/api/alerts/${id}`)
      .then((d) => {
        setDetail(d);
        setError(null);
      })
      .catch(() => setError("Alerta não encontrado."));
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);
  useLiveRefresh(["alerts", "executions"], load);

  const act = async (action: "acknowledge" | "resolve") => {
    await apiFetch(`/api/alerts/${id}/${action}`, { method: "POST" });
    load();
  };

  if (error) {
    return (
      <div>
        <p className="rounded-md bg-red-50 p-3 text-sm text-red-700 dark:bg-red-950 dark:text-red-300">
          {error}
        </p>
        <Link href="/alerts" className="mt-4 inline-block text-sm text-blue-600 hover:underline">
          ← Voltar aos alertas
        </Link>
      </div>
    );
  }

  if (!detail) return <p className="text-sm text-gray-400">A carregar…</p>;

  const { alert, timeline, logs, executions } = detail;

  return (
    <div className="max-w-3xl">
      <Link href="/alerts" className="text-sm text-blue-600 hover:underline">
        ← Alertas
      </Link>
      <div className="mt-2 flex items-start justify-between gap-4">
        <div>
          <h2 className="text-2xl font-semibold">{alert.title}</h2>
          <p className="mt-1 text-sm text-gray-500">
            <Link
              href={`/services/${alert.serviceId}`}
              className="text-blue-600 hover:underline"
            >
              {alert.serviceName}
            </Link>
            {alert.ruleId && (
              <>
                {" "}
                · regra{" "}
                <Link
                  href={`/rules/${alert.ruleId}`}
                  className="text-blue-600 hover:underline"
                >
                  ver definição
                </Link>
              </>
            )}
          </p>
        </div>
        <div className="flex shrink-0 gap-2">
          <span
            className={`rounded-full px-3 py-1 text-xs font-medium ${severityColor(alert.severity)}`}
          >
            {SEVERITY_LABELS[alert.severity]}
          </span>
          <span
            className={`rounded-full px-3 py-1 text-xs font-medium ${statusColor(alert.status)}`}
          >
            {STATUS_LABELS[alert.status]}
          </span>
        </div>
      </div>

      <div className="mt-4 flex gap-3">
        {alert.status === "OPEN" && (
          <button
            onClick={() => act("acknowledge")}
            className="rounded-md bg-amber-600 px-4 py-2 text-sm font-medium text-white hover:bg-amber-700"
          >
            Reconhecer
          </button>
        )}
        {alert.status !== "RESOLVED" && (
          <button
            onClick={() => act("resolve")}
            className="rounded-md bg-green-600 px-4 py-2 text-sm font-medium text-white hover:bg-green-700"
          >
            Resolver
          </button>
        )}
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-2">
        <div className="rounded-lg border border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-900">
          <div className="border-b border-gray-200 px-5 py-3 dark:border-gray-800">
            <h3 className="text-sm font-semibold">Timeline</h3>
          </div>
          <ul className="p-5">
            {timeline.map((e, i) => (
              <li key={e.id} className="relative flex gap-3 pb-5 last:pb-0">
                {i < timeline.length - 1 && (
                  <span className="absolute left-[11px] top-6 h-full w-px bg-gray-200 dark:bg-gray-700" />
                )}
                <span className="z-10 text-lg leading-6">
                  {TIMELINE_ICONS[e.type]}
                </span>
                <div>
                  <p className="text-sm text-gray-700 dark:text-gray-200">
                    {e.description}
                  </p>
                  <p className="mt-0.5 text-xs text-gray-400">
                    {new Date(e.createdAt).toLocaleString("pt-PT")}
                  </p>
                </div>
              </li>
            ))}
          </ul>
        </div>

        <div className="rounded-lg border border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-900">
          <div className="border-b border-gray-200 px-5 py-3 dark:border-gray-800">
            <h3 className="text-sm font-semibold">
              Logs associados ({logs.length})
            </h3>
          </div>
          {logs.length === 0 ? (
            <p className="px-5 py-8 text-center text-sm text-gray-400">
              Sem logs associados (regra de ausência de atividade).
            </p>
          ) : (
            <ul className="divide-y divide-gray-200 dark:divide-gray-800">
              {logs.map((l) => (
                <li
                  key={l.id}
                  onClick={() => setSelectedLog(l)}
                  className="cursor-pointer px-5 py-3 hover:bg-gray-50 dark:hover:bg-gray-800"
                >
                  <p className="truncate text-sm">{l.message ?? "(sem mensagem)"}</p>
                  <p className="mt-0.5 text-xs text-gray-400">
                    {l.level} · {new Date(l.receivedAt).toLocaleString("pt-PT")}
                  </p>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>

      {executions.length > 0 && (
        <div className="mt-6 rounded-lg border border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-900">
          <div className="border-b border-gray-200 px-5 py-3 dark:border-gray-800">
            <h3 className="text-sm font-semibold">
              Automações executadas ({executions.length})
            </h3>
          </div>
          <ul className="divide-y divide-gray-200 dark:divide-gray-800">
            {executions.map((e) => (
              <li key={e.id} className="flex items-center justify-between px-5 py-3 text-sm">
                <div>
                  <p>
                    {e.status === "SUCCESS"
                      ? EXECUTION_LABELS[e.actionType].ok
                      : EXECUTION_LABELS[e.actionType].fail}
                    {e.responseCode != null && (
                      <span className="text-gray-500"> · HTTP {e.responseCode}</span>
                    )}
                    {e.error && <span className="text-red-500"> · {e.error}</span>}
                  </p>
                  <p className="mt-0.5 text-xs text-gray-400">
                    {e.attempts} tentativa(s) ·{" "}
                    {new Date(e.executedAt).toLocaleString("pt-PT")}
                  </p>
                </div>
              </li>
            ))}
          </ul>
        </div>
      )}

      <AiAnalysisPanel
        endpoint={`/api/alerts/${id}/analyses`}
        emptyHint="Ainda sem análises. Usa o botão para gerar um resumo, causa provável, evidências e recomendações a partir dos logs deste alerta."
      />

      {selectedLog && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-6"
          onClick={() => setSelectedLog(null)}
        >
          <div
            className="max-h-[80vh] w-full max-w-2xl overflow-auto rounded-lg bg-white p-6 dark:bg-gray-900"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-start justify-between">
              <p className="text-xs text-gray-500">
                {new Date(selectedLog.receivedAt).toLocaleString("pt-PT")} · ID{" "}
                {selectedLog.id}
              </p>
              <button
                onClick={() => setSelectedLog(null)}
                className="text-gray-400 hover:text-gray-600"
              >
                ✕
              </button>
            </div>
            <pre className="mt-3 overflow-x-auto rounded bg-gray-100 p-3 font-mono text-xs leading-relaxed dark:bg-gray-800">
              {(() => {
                try {
                  return JSON.stringify(JSON.parse(selectedLog.payload), null, 2);
                } catch {
                  return selectedLog.payload;
                }
              })()}
            </pre>
          </div>
        </div>
      )}
    </div>
  );
}
