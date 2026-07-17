"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { apiFetch } from "@/lib/api";
import { Alert, AlertStatus, STATUS_LABELS, statusColor } from "@/lib/alerts";
import { SEVERITY_LABELS, severityColor } from "@/lib/rules";
import { useLiveRefresh } from "@/lib/useLiveRefresh";

const FILTERS: (AlertStatus | "")[] = ["", "OPEN", "ACKNOWLEDGED", "RESOLVED"];

export default function AlertsPage() {
  const [statusFilter, setStatusFilter] = useState<AlertStatus | "">("");
  const [alerts, setAlerts] = useState<Alert[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    const qs = statusFilter ? `?status=${statusFilter}` : "";
    apiFetch<Alert[]>(`/api/alerts${qs}`)
      .then((a) => {
        setAlerts(a);
        setError(null);
      })
      .catch(() => setError("Não foi possível carregar os alertas."));
  }, [statusFilter]);

  useEffect(() => {
    load();
  }, [load]);
  useLiveRefresh(["alerts"], load);

  return (
    <div>
      <h2 className="text-2xl font-semibold">Alertas</h2>
      <p className="mt-1 text-sm text-gray-500">
        Incidentes criados pelas regras. Atualiza a cada 5s.
      </p>

      <div className="mt-4 flex gap-2">
        {FILTERS.map((f) => (
          <button
            key={f || "all"}
            onClick={() => setStatusFilter(f)}
            className={`rounded-full px-3 py-1 text-xs font-medium ${
              statusFilter === f
                ? "bg-blue-600 text-white"
                : "bg-gray-100 text-gray-600 hover:bg-gray-200 dark:bg-gray-800 dark:text-gray-300"
            }`}
          >
            {f ? STATUS_LABELS[f] : "Todos"}
          </button>
        ))}
      </div>

      {error && (
        <p className="mt-4 rounded-md bg-red-50 p-3 text-sm text-red-700 dark:bg-red-950 dark:text-red-300">
          {error}
        </p>
      )}

      {alerts && alerts.length === 0 && (
        <div className="mt-6 rounded-lg border border-dashed border-gray-300 p-8 text-center text-sm text-gray-500 dark:border-gray-700">
          Sem alertas{statusFilter ? ` no estado ${STATUS_LABELS[statusFilter]}` : ""}.
        </div>
      )}

      {alerts && alerts.length > 0 && (
        <div className="mt-4 overflow-x-auto rounded-lg border border-gray-200 dark:border-gray-800">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-left text-xs uppercase text-gray-500 dark:bg-gray-900">
              <tr>
                <th className="px-4 py-3">Alerta</th>
                <th className="px-4 py-3">Serviço</th>
                <th className="px-4 py-3">Severidade</th>
                <th className="px-4 py-3">Estado</th>
                <th className="px-4 py-3">Aberto</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200 bg-white dark:divide-gray-800 dark:bg-gray-950">
              {alerts.map((a) => (
                <tr key={a.id} className="hover:bg-gray-50 dark:hover:bg-gray-900">
                  <td className="px-4 py-3">
                    <Link
                      href={`/alerts/${a.id}`}
                      className="font-medium text-blue-600 hover:underline dark:text-blue-400"
                    >
                      {a.title}
                    </Link>
                  </td>
                  <td className="px-4 py-3">{a.serviceName}</td>
                  <td className="px-4 py-3">
                    <span
                      className={`rounded-full px-2 py-0.5 text-xs font-medium ${severityColor(a.severity)}`}
                    >
                      {SEVERITY_LABELS[a.severity]}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <span
                      className={`rounded-full px-2 py-0.5 text-xs font-medium ${statusColor(a.status)}`}
                    >
                      {STATUS_LABELS[a.status]}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-gray-500">
                    {new Date(a.openedAt).toLocaleString("pt-PT")}
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
