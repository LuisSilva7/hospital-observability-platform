"use client";

import { useCallback, useEffect, useState } from "react";
import { apiFetch } from "@/lib/api";
import { Service } from "@/lib/types";
import { useLiveRefresh } from "@/lib/useLiveRefresh";

type LogEvent = {
  id: string;
  serviceId: string;
  serviceName: string;
  receivedAt: string;
  eventTimestamp: string | null;
  level: string | null;
  message: string | null;
  eventType: string | null;
  payload: string;
};

type LogPage = {
  content: LogEvent[];
  size: number;
  // presentes só na 1.ª página (modo com contagem); páginas seguintes usam cursor keyset
  totalElements?: number;
  totalPages?: number;
  nextCursor: string | null;
};

const LEVELS = ["", "INFO", "WARN", "ERROR", "DEBUG", "TRACE", "FATAL"];

const levelColor = (level: string | null) => {
  switch (level) {
    case "ERROR":
    case "FATAL":
      return "bg-red-100 text-red-700 dark:bg-red-950 dark:text-red-300";
    case "WARN":
      return "bg-amber-100 text-amber-700 dark:bg-amber-950 dark:text-amber-300";
    case "INFO":
      return "bg-blue-100 text-blue-700 dark:bg-blue-950 dark:text-blue-300";
    default:
      return "bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-300";
  }
};

const inputClass =
  "rounded-md border border-gray-300 bg-white px-3 py-2 text-sm dark:border-gray-700 dark:bg-gray-900";

export default function LogsPage() {
  const [services, setServices] = useState<Service[]>([]);
  const [filters, setFilters] = useState({
    serviceId: "",
    level: "",
    text: "",
    from: "",
    to: "",
  });
  // pilha de cursores keyset: vazia = 1.ª página; cada "Seguinte" empilha um cursor
  const [cursorStack, setCursorStack] = useState<string[]>([]);
  const [data, setData] = useState<LogPage | null>(null);
  const [selected, setSelected] = useState<LogEvent | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    apiFetch<Service[]>("/api/services").then(setServices).catch(() => {});
  }, []);

  const load = useCallback(() => {
    const params = new URLSearchParams();
    if (filters.serviceId) params.set("serviceId", filters.serviceId);
    if (filters.level) params.set("level", filters.level);
    if (filters.text) params.set("text", filters.text);
    if (filters.from) params.set("from", new Date(filters.from).toISOString());
    if (filters.to) params.set("to", new Date(filters.to).toISOString());
    const cursor = cursorStack[cursorStack.length - 1];
    if (cursor) params.set("cursor", cursor);
    params.set("size", "25");
    apiFetch<LogPage>(`/api/logs?${params}`)
      .then((d) => {
        setData(d);
        setError(null);
      })
      .catch(() => setError("Não foi possível carregar os logs."));
  }, [filters, cursorStack]);

  useEffect(() => {
    load();
  }, [load]);
  useLiveRefresh(["logs"], load);

  const setFilter = (field: string, value: string) => {
    setFilters((f) => ({ ...f, [field]: value }));
    setCursorStack([]);
  };

  return (
    <div>
      <h2 className="text-2xl font-semibold">Logs</h2>
      <p className="mt-1 text-sm text-gray-500">
        Eventos recebidos de todos os serviços. Atualiza a cada 5s.
      </p>

      <div className="mt-4 flex flex-wrap items-center gap-2">
        <select
          value={filters.serviceId}
          onChange={(e) => setFilter("serviceId", e.target.value)}
          className={inputClass}
        >
          <option value="">Todos os serviços</option>
          {services.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </select>
        <select
          value={filters.level}
          onChange={(e) => setFilter("level", e.target.value)}
          className={inputClass}
        >
          {LEVELS.map((l) => (
            <option key={l} value={l}>
              {l || "Todos os níveis"}
            </option>
          ))}
        </select>
        <input
          type="datetime-local"
          value={filters.from}
          onChange={(e) => setFilter("from", e.target.value)}
          className={inputClass}
          title="Desde"
        />
        <input
          type="datetime-local"
          value={filters.to}
          onChange={(e) => setFilter("to", e.target.value)}
          className={inputClass}
          title="Até"
        />
        <input
          type="search"
          placeholder="Pesquisar na mensagem ou payload…"
          value={filters.text}
          onChange={(e) => setFilter("text", e.target.value)}
          className={`${inputClass} min-w-64 flex-1`}
        />
      </div>

      {error && (
        <p className="mt-4 rounded-md bg-red-50 p-3 text-sm text-red-700 dark:bg-red-950 dark:text-red-300">
          {error}
        </p>
      )}

      {data && (
        <>
          <div className="mt-4 overflow-x-auto rounded-lg border border-gray-200 dark:border-gray-800">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-left text-xs uppercase text-gray-500 dark:bg-gray-900">
                <tr>
                  <th className="px-4 py-3">Recebido</th>
                  <th className="px-4 py-3">Serviço</th>
                  <th className="px-4 py-3">Nível</th>
                  <th className="px-4 py-3">Tipo de evento</th>
                  <th className="px-4 py-3">Mensagem</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200 bg-white dark:divide-gray-800 dark:bg-gray-950">
                {data.content.length === 0 && (
                  <tr>
                    <td colSpan={5} className="px-4 py-8 text-center text-gray-400">
                      Sem logs para os filtros escolhidos.
                    </td>
                  </tr>
                )}
                {data.content.map((log) => (
                  <tr
                    key={log.id}
                    onClick={() => setSelected(log)}
                    className="cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-900"
                  >
                    <td className="whitespace-nowrap px-4 py-2 text-gray-500">
                      {new Date(log.receivedAt).toLocaleString("pt-PT")}
                    </td>
                    <td className="whitespace-nowrap px-4 py-2">{log.serviceName}</td>
                    <td className="px-4 py-2">
                      {log.level && (
                        <span
                          className={`rounded-full px-2 py-0.5 text-xs font-medium ${levelColor(log.level)}`}
                        >
                          {log.level}
                        </span>
                      )}
                    </td>
                    <td className="whitespace-nowrap px-4 py-2 text-gray-500">
                      {log.eventType ?? "—"}
                    </td>
                    <td className="max-w-md truncate px-4 py-2">{log.message ?? "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="mt-4 flex items-center justify-between text-sm text-gray-500">
            <span>
              {cursorStack.length === 0 && data.totalElements != null
                ? `${data.totalElements} logs · página 1`
                : `página ${cursorStack.length + 1}`}
            </span>
            <div className="flex gap-2">
              <button
                disabled={cursorStack.length === 0}
                onClick={() => setCursorStack((s) => s.slice(0, -1))}
                className="rounded-md border border-gray-300 px-3 py-1.5 disabled:opacity-40 dark:border-gray-700"
              >
                ← Anterior
              </button>
              <button
                disabled={!data.nextCursor}
                onClick={() =>
                  data.nextCursor &&
                  setCursorStack((s) => [...s, data.nextCursor as string])
                }
                className="rounded-md border border-gray-300 px-3 py-1.5 disabled:opacity-40 dark:border-gray-700"
              >
                Seguinte →
              </button>
            </div>
          </div>
        </>
      )}

      {selected && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-6"
          onClick={() => setSelected(null)}
        >
          <div
            className="max-h-[80vh] w-full max-w-2xl overflow-auto rounded-lg bg-white p-6 dark:bg-gray-900"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-start justify-between">
              <div>
                <h3 className="font-semibold">{selected.serviceName}</h3>
                <p className="text-xs text-gray-500">
                  Recebido {new Date(selected.receivedAt).toLocaleString("pt-PT")} ·
                  ID {selected.id}
                </p>
              </div>
              <button
                onClick={() => setSelected(null)}
                className="text-gray-400 hover:text-gray-600"
              >
                ✕
              </button>
            </div>
            <div className="mt-3 flex gap-2">
              {selected.level && (
                <span
                  className={`rounded-full px-2 py-0.5 text-xs font-medium ${levelColor(selected.level)}`}
                >
                  {selected.level}
                </span>
              )}
              {selected.eventType && (
                <span className="rounded-full bg-gray-100 px-2 py-0.5 text-xs dark:bg-gray-800">
                  {selected.eventType}
                </span>
              )}
            </div>
            <h4 className="mt-4 text-xs font-semibold uppercase text-gray-500">
              Payload original
            </h4>
            <pre className="mt-2 overflow-x-auto rounded bg-gray-100 p-3 font-mono text-xs leading-relaxed dark:bg-gray-800">
              {(() => {
                try {
                  return JSON.stringify(JSON.parse(selected.payload), null, 2);
                } catch {
                  return selected.payload;
                }
              })()}
            </pre>
          </div>
        </div>
      )}
    </div>
  );
}
