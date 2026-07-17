"use client";

import { useEffect, useRef } from "react";
import { API_BASE_URL } from "@/lib/api";

/**
 * Atualização em tempo real via SSE (GET /api/events/stream), com fallback
 * para polling quando a ligação falha. Os eventos são só sinais "algo mudou"
 * — este hook chama onChange e a página volta a pedir os dados via REST.
 * A carga inicial fica a cargo da página (useEffect próprio), para que
 * mudanças de filtros/janelas continuem a refazer o pedido de imediato.
 *
 * - Eventos coalescidos (400ms) para não martelar a API em rajadas de logs.
 * - Com SSE ligado mantém um polling lento de segurança (60s).
 * - Se o SSE falhar, volta ao polling clássico (fallbackMs) enquanto o
 *   EventSource tenta religar sozinho.
 */
export function useLiveRefresh(
  topics: string[],
  onChange: () => void,
  fallbackMs = 5_000
) {
  const callbackRef = useRef(onChange);
  callbackRef.current = onChange;
  const topicsKey = topics.join(",");

  useEffect(() => {
    let pollTimer: ReturnType<typeof setInterval> | null = null;
    let pollMs = 0;
    let debounceTimer: ReturnType<typeof setTimeout> | null = null;

    const refresh = () => callbackRef.current();

    const trigger = () => {
      if (debounceTimer) return;
      // pequena espera: coalesce rajadas e dá tempo ao commit no backend
      debounceTimer = setTimeout(() => {
        debounceTimer = null;
        refresh();
      }, 400);
    };

    // Só (re)cria o intervalo se a cadência mudar. O EventSource dispara
    // 'error' a cada tentativa de reconexão (~3s), mais frequente do que o
    // fallback (5s); sem esta guarda o timer seria reposto antes de disparar
    // e o polling de recurso nunca chegaria a correr durante uma queda.
    const startPolling = (ms: number) => {
      if (pollTimer && pollMs === ms) return;
      if (pollTimer) clearInterval(pollTimer);
      pollMs = ms;
      pollTimer = setInterval(refresh, ms);
    };

    const source = new EventSource(`${API_BASE_URL}/api/events/stream`);
    for (const topic of topicsKey.split(",").filter(Boolean)) {
      source.addEventListener(topic, trigger);
    }
    source.onopen = () => startPolling(60_000);
    source.onerror = () => startPolling(fallbackMs);
    startPolling(fallbackMs); // até o SSE abrir

    return () => {
      source.close();
      if (pollTimer) clearInterval(pollTimer);
      if (debounceTimer) clearTimeout(debounceTimer);
    };
  }, [topicsKey, fallbackMs]);
}
