"use client";

import { useCallback, useEffect, useState } from "react";
import { apiFetch, extractApiMessage } from "@/lib/api";
import { AIAnalysis } from "@/lib/alerts";
import { useLiveRefresh } from "@/lib/useLiveRefresh";

/**
 * Painel "Análise com IA" reutilizável: lista análises e permite pedir uma
 * nova. Usado no detalhe do alerta (endpoint /api/alerts/{id}/analyses) e no
 * detalhe do serviço (endpoint /api/services/{id}/analyses).
 */
export default function AiAnalysisPanel({
  endpoint,
  emptyHint,
}: {
  endpoint: string;
  emptyHint: string;
}) {
  const [analyses, setAnalyses] = useState<AIAnalysis[]>([]);
  const [analyzing, setAnalyzing] = useState(false);
  const [aiError, setAiError] = useState<string | null>(null);

  const load = useCallback(() => {
    apiFetch<AIAnalysis[]>(endpoint)
      .then(setAnalyses)
      .catch(() => {});
  }, [endpoint]);

  useEffect(() => {
    load();
  }, [load]);
  useLiveRefresh(["analyses"], load);

  const run = async () => {
    setAnalyzing(true);
    setAiError(null);
    try {
      await apiFetch<AIAnalysis>(endpoint, { method: "POST" });
      load();
    } catch (e) {
      setAiError(extractApiMessage(e) ?? "Falha ao gerar a análise com IA.");
    } finally {
      setAnalyzing(false);
    }
  };

  return (
    <div className="mt-6 rounded-lg border border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-900">
      <div className="flex items-center justify-between border-b border-gray-200 px-5 py-3 dark:border-gray-800">
        <h3 className="text-sm font-semibold">
          Análise com IA{analyses.length > 0 && ` (${analyses.length})`}
        </h3>
        <button
          onClick={run}
          disabled={analyzing}
          className="rounded-md bg-violet-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-violet-700 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {analyzing ? "A analisar…" : "✨ Analisar com IA"}
        </button>
      </div>

      {aiError && (
        <p className="mx-5 mt-4 rounded-md bg-red-50 p-3 text-sm text-red-700 dark:bg-red-950 dark:text-red-300">
          {aiError}
        </p>
      )}

      {analyses.length === 0 ? (
        <p className="px-5 py-8 text-center text-sm text-gray-400">
          {analyzing ? "A pedir análise ao modelo — pode demorar alguns segundos…" : emptyHint}
        </p>
      ) : (
        <ul className="divide-y divide-gray-200 dark:divide-gray-800">
          {analyses.map((a) => (
            <li key={a.id} className="px-5 py-4">
              {a.status === "FAILED" ? (
                <p className="rounded-md bg-red-50 p-3 text-sm text-red-700 dark:bg-red-950 dark:text-red-300">
                  ❌ Análise falhou: {a.error ?? "erro desconhecido"}
                </p>
              ) : (
                a.output && (
                  <div className="space-y-3 text-sm">
                    <div>
                      <p className="font-semibold">Resumo</p>
                      <p className="mt-0.5 text-gray-700 dark:text-gray-200">
                        {a.output.summary}
                      </p>
                    </div>
                    <div>
                      <p className="font-semibold">Causa provável</p>
                      <p className="mt-0.5 text-gray-700 dark:text-gray-200">
                        {a.output.probableCause}
                      </p>
                    </div>
                    <div>
                      <p className="font-semibold">Evidências</p>
                      <ul className="mt-0.5 list-disc space-y-0.5 pl-5 text-gray-700 dark:text-gray-200">
                        {a.output.evidence.map((e, i) => (
                          <li key={i}>{e}</li>
                        ))}
                      </ul>
                    </div>
                    <div>
                      <p className="font-semibold">Recomendações</p>
                      <ul className="mt-0.5 list-disc space-y-0.5 pl-5 text-gray-700 dark:text-gray-200">
                        {a.output.recommendations.map((r, i) => (
                          <li key={i}>{r}</li>
                        ))}
                      </ul>
                    </div>
                  </div>
                )
              )}
              <p className="mt-3 text-xs text-gray-400">
                {a.provider}
                {a.model && ` · ${a.model}`} ·{" "}
                {new Date(a.createdAt).toLocaleString("pt-PT")} · Sugestão
                gerada por IA — confirma antes de agir.
              </p>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
