"use client";

import { useEffect, useState } from "react";
import { apiFetch } from "@/lib/api";

type Health = {
  status: string;
  database: string;
  timestamp: string;
};

export default function HealthCard() {
  const [health, setHealth] = useState<Health | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    const load = () =>
      apiFetch<Health>("/api/health")
        .then((h) => {
          if (!cancelled) {
            setHealth(h);
            setError(null);
          }
        })
        .catch(() => {
          if (!cancelled) setError("Backend inacessível");
        });
    load();
    const interval = setInterval(load, 10_000);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, []);

  const up = health?.status === "UP";

  return (
    <div className="rounded-lg border border-gray-200 bg-white p-6 dark:border-gray-800 dark:bg-gray-900">
      <h3 className="text-sm font-medium text-gray-500">Estado da plataforma</h3>
      {error ? (
        <p className="mt-2 flex items-center gap-2 text-lg font-semibold text-red-600">
          <span className="h-2.5 w-2.5 rounded-full bg-red-500" /> {error}
        </p>
      ) : health ? (
        <div className="mt-2">
          <p
            className={`flex items-center gap-2 text-lg font-semibold ${
              up ? "text-green-600" : "text-amber-600"
            }`}
          >
            <span
              className={`h-2.5 w-2.5 rounded-full ${
                up ? "bg-green-500" : "bg-amber-500"
              }`}
            />
            {health.status}
          </p>
          <p className="mt-1 text-xs text-gray-500">
            Base de dados: {health.database} ·{" "}
            {new Date(health.timestamp).toLocaleTimeString("pt-PT")}
          </p>
        </div>
      ) : (
        <p className="mt-2 text-sm text-gray-400">A verificar…</p>
      )}
    </div>
  );
}
