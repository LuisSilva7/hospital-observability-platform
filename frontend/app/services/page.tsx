"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { apiFetch } from "@/lib/api";
import {
  CRITICALITY_LABELS,
  ENVIRONMENT_LABELS,
  Service,
} from "@/lib/types";

export default function ServicesPage() {
  const [services, setServices] = useState<Service[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    apiFetch<Service[]>("/api/services")
      .then((s) => {
        setServices(s);
        setError(null);
      })
      .catch(() => setError("Não foi possível carregar os serviços."));
  }, []);

  useEffect(() => {
    load();
    const interval = setInterval(load, 10_000);
    return () => clearInterval(interval);
  }, [load]);

  return (
    <div>
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-semibold">Serviços</h2>
          <p className="mt-1 text-sm text-gray-500">
            Serviços monitorizados pela plataforma.
          </p>
        </div>
        <Link
          href="/services/new"
          className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
        >
          + Novo serviço
        </Link>
      </div>

      {error && (
        <p className="mt-6 rounded-md bg-red-50 p-3 text-sm text-red-700 dark:bg-red-950 dark:text-red-300">
          {error}
        </p>
      )}

      {services && services.length === 0 && (
        <div className="mt-6 rounded-lg border border-dashed border-gray-300 p-8 text-center text-sm text-gray-500 dark:border-gray-700">
          Ainda não existem serviços. Cria o primeiro para obteres um endpoint
          de ingestão e uma API key.
        </div>
      )}

      {services && services.length > 0 && (
        <div className="mt-6 overflow-x-auto rounded-lg border border-gray-200 dark:border-gray-800">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-left text-xs uppercase text-gray-500 dark:bg-gray-900">
              <tr>
                <th className="px-4 py-3">Nome</th>
                <th className="px-4 py-3">Ambiente</th>
                <th className="px-4 py-3">Criticidade</th>
                <th className="px-4 py-3">Estado</th>
                <th className="px-4 py-3">Último log</th>
                <th className="px-4 py-3">Ativo</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200 bg-white dark:divide-gray-800 dark:bg-gray-950">
              {services.map((s) => (
                <tr key={s.id} className="hover:bg-gray-50 dark:hover:bg-gray-900">
                  <td className="px-4 py-3">
                    <Link
                      href={`/services/${s.id}`}
                      className="font-medium text-blue-600 hover:underline dark:text-blue-400"
                    >
                      {s.name}
                    </Link>
                  </td>
                  <td className="px-4 py-3">{ENVIRONMENT_LABELS[s.environment]}</td>
                  <td className="px-4 py-3">{CRITICALITY_LABELS[s.criticality]}</td>
                  <td className="px-4 py-3">
                    <span className="rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-600 dark:bg-gray-800 dark:text-gray-300">
                      {s.status}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-gray-500">
                    {s.lastSeenAt
                      ? new Date(s.lastSeenAt).toLocaleString("pt-PT")
                      : "—"}
                  </td>
                  <td className="px-4 py-3">{s.active ? "✓" : "✗"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
