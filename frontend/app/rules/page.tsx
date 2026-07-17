"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { apiFetch } from "@/lib/api";
import { useLiveRefresh } from "@/lib/useLiveRefresh";
import {
  Rule,
  RULE_TYPE_INFO,
  SEVERITY_LABELS,
  severityColor,
} from "@/lib/rules";

export default function RulesPage() {
  const [rules, setRules] = useState<Rule[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    apiFetch<Rule[]>("/api/rules")
      .then((r) => {
        setRules(r);
        setError(null);
      })
      .catch(() => setError("Não foi possível carregar as regras."));
  }, []);

  useEffect(() => {
    load();
  }, [load]);
  useLiveRefresh(["rules", "alerts"], load);

  const toggle = async (rule: Rule) => {
    await apiFetch(`/api/rules/${rule.id}/enabled`, {
      method: "PATCH",
      body: JSON.stringify({ enabled: !rule.enabled }),
    });
    load();
  };

  const remove = async (rule: Rule) => {
    if (!confirm(`Eliminar a regra "${rule.name}"?`)) return;
    await apiFetch<void>(`/api/rules/${rule.id}`, { method: "DELETE" }).catch(
      () => undefined
    );
    load();
  };

  return (
    <div>
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-semibold">Regras</h2>
          <p className="mt-1 text-sm text-gray-500">
            Condições que definem o que é um problema. Quando uma regra dispara,
            é registada (e a partir do Módulo 6, cria um alerta).
          </p>
        </div>
        <Link
          href="/rules/new"
          className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
        >
          + Nova regra
        </Link>
      </div>

      {error && (
        <p className="mt-6 rounded-md bg-red-50 p-3 text-sm text-red-700 dark:bg-red-950 dark:text-red-300">
          {error}
        </p>
      )}

      {rules && rules.length === 0 && (
        <div className="mt-6 rounded-lg border border-dashed border-gray-300 p-8 text-center text-sm text-gray-500 dark:border-gray-700">
          Ainda não existem regras. Cria a primeira — por exemplo, “avisar
          quando chegar um log com level ERROR”.
        </div>
      )}

      {rules && rules.length > 0 && (
        <div className="mt-6 overflow-x-auto rounded-lg border border-gray-200 dark:border-gray-800">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-left text-xs uppercase text-gray-500 dark:bg-gray-900">
              <tr>
                <th className="px-4 py-3">Nome</th>
                <th className="px-4 py-3">Serviço</th>
                <th className="px-4 py-3">Tipo</th>
                <th className="px-4 py-3">Severidade</th>
                <th className="px-4 py-3">Último disparo</th>
                <th className="px-4 py-3">Ativa</th>
                <th className="px-4 py-3"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200 bg-white dark:divide-gray-800 dark:bg-gray-950">
              {rules.map((r) => (
                <tr key={r.id} className="hover:bg-gray-50 dark:hover:bg-gray-900">
                  <td className="px-4 py-3">
                    <Link
                      href={`/rules/${r.id}`}
                      className="font-medium text-blue-600 hover:underline dark:text-blue-400"
                    >
                      {r.name}
                    </Link>
                  </td>
                  <td className="px-4 py-3">{r.serviceName}</td>
                  <td className="px-4 py-3 text-gray-500">
                    {RULE_TYPE_INFO[r.type].label}
                  </td>
                  <td className="px-4 py-3">
                    <span
                      className={`rounded-full px-2 py-0.5 text-xs font-medium ${severityColor(r.severity)}`}
                    >
                      {SEVERITY_LABELS[r.severity]}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-gray-500">
                    {r.lastTriggeredAt
                      ? new Date(r.lastTriggeredAt).toLocaleString("pt-PT")
                      : "Nunca"}
                  </td>
                  <td className="px-4 py-3">
                    <button
                      onClick={() => toggle(r)}
                      className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                        r.enabled
                          ? "bg-green-100 text-green-700 dark:bg-green-950 dark:text-green-300"
                          : "bg-gray-100 text-gray-500 dark:bg-gray-800"
                      }`}
                    >
                      {r.enabled ? "Ativa" : "Inativa"}
                    </button>
                  </td>
                  <td className="px-4 py-3 text-right">
                    <button
                      onClick={() => remove(r)}
                      className="text-xs text-red-500 hover:underline"
                    >
                      Eliminar
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
