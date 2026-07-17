"use client";

import Link from "next/link";
import { use, useCallback, useEffect, useState } from "react";
import { apiFetch } from "@/lib/api";
import { useLiveRefresh } from "@/lib/useLiveRefresh";
import {
  Evaluation,
  OPERATOR_LABELS,
  Rule,
  RULE_TYPE_INFO,
  SEVERITY_LABELS,
  severityColor,
} from "@/lib/rules";

export default function RuleDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = use(params);
  const [rule, setRule] = useState<Rule | null>(null);
  const [evaluations, setEvaluations] = useState<Evaluation[]>([]);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    apiFetch<Rule>(`/api/rules/${id}`)
      .then((r) => {
        setRule(r);
        setError(null);
      })
      .catch(() => setError("Regra não encontrada."));
    apiFetch<Evaluation[]>(`/api/rules/${id}/evaluations`)
      .then(setEvaluations)
      .catch(() => {});
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);
  useLiveRefresh(["rules", "alerts"], load);

  if (error) {
    return (
      <div>
        <p className="rounded-md bg-red-50 p-3 text-sm text-red-700 dark:bg-red-950 dark:text-red-300">
          {error}
        </p>
        <Link href="/rules" className="mt-4 inline-block text-sm text-blue-600 hover:underline">
          ← Voltar às regras
        </Link>
      </div>
    );
  }

  if (!rule) return <p className="text-sm text-gray-400">A carregar…</p>;

  return (
    <div className="max-w-3xl">
      <Link href="/rules" className="text-sm text-blue-600 hover:underline">
        ← Regras
      </Link>
      <div className="mt-2 flex items-start justify-between">
        <div>
          <h2 className="text-2xl font-semibold">{rule.name}</h2>
          <p className="mt-1 text-sm text-gray-500">
            {RULE_TYPE_INFO[rule.type].label} ·{" "}
            <Link
              href={`/services/${rule.serviceId}`}
              className="text-blue-600 hover:underline"
            >
              {rule.serviceName}
            </Link>
          </p>
        </div>
        <span
          className={`rounded-full px-3 py-1 text-xs font-medium ${severityColor(rule.severity)}`}
        >
          {SEVERITY_LABELS[rule.severity]}
        </span>
      </div>

      <div className="mt-6 rounded-lg border border-gray-200 bg-white p-6 text-sm dark:border-gray-800 dark:bg-gray-900">
        <h3 className="text-sm font-semibold">Definição</h3>
        <ul className="mt-3 space-y-1 text-gray-600 dark:text-gray-300">
          {rule.type === "NO_ACTIVITY" ? (
            <li>
              Dispara se o serviço não enviar logs durante{" "}
              <strong>{rule.windowMinutes} minutos</strong>.
            </li>
          ) : (
            <>
              {rule.type === "COUNT_THRESHOLD" && (
                <li>
                  Dispara com <strong>{rule.threshold}+ eventos</strong>{" "}
                  correspondentes em <strong>{rule.windowMinutes} minutos</strong>.
                </li>
              )}
              {rule.conditions.map((c, i) => (
                <li key={i}>
                  <code className="rounded bg-gray-100 px-1.5 py-0.5 font-mono text-xs dark:bg-gray-800">
                    {c.fieldPath}
                  </code>{" "}
                  {OPERATOR_LABELS[c.operator]}{" "}
                  <code className="rounded bg-gray-100 px-1.5 py-0.5 font-mono text-xs dark:bg-gray-800">
                    {c.expectedValue}
                  </code>
                </li>
              ))}
            </>
          )}
          <li className="pt-2 text-xs text-gray-400">
            Cooldown de {rule.cooldownMinutes} min entre disparos ·{" "}
            {rule.enabled ? "ativa" : "inativa"} · último disparo:{" "}
            {rule.lastTriggeredAt
              ? new Date(rule.lastTriggeredAt).toLocaleString("pt-PT")
              : "nunca"}
          </li>
        </ul>
      </div>

      <div className="mt-6 rounded-lg border border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-900">
        <div className="border-b border-gray-200 px-6 py-3 dark:border-gray-800">
          <h3 className="text-sm font-semibold">
            Histórico de disparos ({evaluations.length})
          </h3>
        </div>
        {evaluations.length === 0 ? (
          <p className="px-6 py-8 text-center text-sm text-gray-400">
            Esta regra ainda não disparou.
          </p>
        ) : (
          <ul className="divide-y divide-gray-200 dark:divide-gray-800">
            {evaluations.map((e) => (
              <li key={e.id} className="px-6 py-3 text-sm">
                <p className="text-gray-700 dark:text-gray-200">{e.details}</p>
                <p className="mt-0.5 text-xs text-gray-400">
                  {new Date(e.triggeredAt).toLocaleString("pt-PT")}
                  {e.logEventId && <> · log {e.logEventId}</>}
                </p>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
