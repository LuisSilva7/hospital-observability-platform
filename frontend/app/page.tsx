"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import HealthCard from "@/components/HealthCard";
import { apiFetch } from "@/lib/api";
import { CRITICALITY_LABELS, Criticality } from "@/lib/types";

type ServiceSummary = {
  id: string;
  name: string;
  criticality: Criticality;
  status: string;
  lastSeenAt: string | null;
  active: boolean;
};

type Overview = {
  totalServices: number;
  byStatus: Record<string, number>;
  services: ServiceSummary[];
  activeAlerts: number;
};

const STATUS_STYLES: Record<string, { label: string; dot: string; text: string }> = {
  HEALTHY: { label: "Saudável", dot: "bg-green-500", text: "text-green-600" },
  SILENT: { label: "Silencioso", dot: "bg-red-500", text: "text-red-600" },
  UNKNOWN: { label: "Desconhecido", dot: "bg-gray-400", text: "text-gray-500" },
  INACTIVE: { label: "Inativo", dot: "bg-gray-300", text: "text-gray-400" },
};

export default function DashboardPage() {
  const [overview, setOverview] = useState<Overview | null>(null);

  const load = useCallback(() => {
    apiFetch<Overview>("/api/overview").then(setOverview).catch(() => {});
  }, []);

  useEffect(() => {
    load();
    const interval = setInterval(load, 5_000);
    return () => clearInterval(interval);
  }, [load]);

  return (
    <div>
      <h2 className="text-2xl font-semibold">Dashboard</h2>
      <p className="mt-1 text-sm text-gray-500">
        Visão geral do estado dos serviços monitorizados e alertas ativos.
      </p>

      <div className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <div className="rounded-lg border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-gray-900">
          <p className="text-xs uppercase text-gray-500">Serviços</p>
          <p className="mt-1 text-3xl font-semibold">
            {overview?.totalServices ?? "—"}
          </p>
        </div>
        <div className="rounded-lg border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-gray-900">
          <p className="text-xs uppercase text-gray-500">Saudáveis</p>
          <p className="mt-1 text-3xl font-semibold text-green-600">
            {overview?.byStatus?.HEALTHY ?? "—"}
          </p>
        </div>
        <div className="rounded-lg border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-gray-900">
          <p className="text-xs uppercase text-gray-500">Silenciosos</p>
          <p className="mt-1 text-3xl font-semibold text-red-600">
            {overview?.byStatus?.SILENT ?? "—"}
          </p>
        </div>
        <div className="rounded-lg border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-gray-900">
          <p className="text-xs uppercase text-gray-500">Alertas ativos</p>
          <p className="mt-1 text-3xl font-semibold">
            {overview?.activeAlerts ?? "—"}
          </p>
          <p className="mt-1 text-xs text-gray-400">Módulo 6</p>
        </div>
      </div>

      <div className="mt-6 grid gap-4 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <div className="rounded-lg border border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-900">
            <div className="border-b border-gray-200 px-5 py-3 dark:border-gray-800">
              <h3 className="text-sm font-semibold">Estado dos serviços</h3>
            </div>
            {!overview || overview.services.length === 0 ? (
              <p className="px-5 py-8 text-center text-sm text-gray-400">
                Sem serviços registados.{" "}
                <Link href="/services/new" className="text-blue-600 hover:underline">
                  Criar o primeiro
                </Link>
              </p>
            ) : (
              <ul className="divide-y divide-gray-200 dark:divide-gray-800">
                {overview.services.map((s) => {
                  const style = STATUS_STYLES[s.status] ?? STATUS_STYLES.UNKNOWN;
                  return (
                    <li key={s.id}>
                      <Link
                        href={`/services/${s.id}`}
                        className="flex items-center justify-between px-5 py-3 hover:bg-gray-50 dark:hover:bg-gray-800"
                      >
                        <div className="flex items-center gap-3">
                          <span className={`h-2.5 w-2.5 rounded-full ${style.dot}`} />
                          <div>
                            <p className="text-sm font-medium">{s.name}</p>
                            <p className="text-xs text-gray-500">
                              Criticidade {CRITICALITY_LABELS[s.criticality]} ·{" "}
                              {s.lastSeenAt
                                ? `último log ${new Date(s.lastSeenAt).toLocaleTimeString("pt-PT")}`
                                : "sem logs"}
                            </p>
                          </div>
                        </div>
                        <span className={`text-xs font-medium ${style.text}`}>
                          {style.label}
                        </span>
                      </Link>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>
        </div>
        <HealthCard />
      </div>
    </div>
  );
}
