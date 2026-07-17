"use client";

import { useCallback, useEffect, useState } from "react";
import { apiFetch } from "@/lib/api";
import { useLiveRefresh } from "@/lib/useLiveRefresh";

type HourBucket = { hour: string; total: number; errors: number; warns: number };
type TopError = { message: string; count: number };
type ServiceCount = {
  serviceId: string;
  serviceName: string;
  total: number;
  errors: number;
};
type LogStats = {
  hours: number;
  volumePerHour: HourBucket[];
  topErrors: TopError[];
  byService: ServiceCount[];
};

// Paleta validada (CVD + contraste) para superfícies clara e escura:
const COLOR_ERROR = "#ef4444"; // erros (ERROR/FATAL)
const COLOR_WARN = "#d97706"; // avisos (WARN)
const COLOR_OTHER = "#3b82f6"; // restantes

const CHART_W = 720;
const CHART_H = 150;
const MARGIN = { top: 8, right: 4, bottom: 20, left: 34 };

export default function LogCharts() {
  const [stats, setStats] = useState<LogStats | null>(null);
  const [hovered, setHovered] = useState<number | null>(null);

  const load = useCallback(() => {
    apiFetch<LogStats>("/api/stats/logs?hours=24")
      .then(setStats)
      .catch(() => {});
  }, []);

  useEffect(() => {
    load();
  }, [load]);
  useLiveRefresh(["logs"], load);

  if (!stats) return null;

  const buckets = stats.volumePerHour;
  const maxTotal = Math.max(1, ...buckets.map((b) => b.total));
  const plotW = CHART_W - MARGIN.left - MARGIN.right;
  const plotH = CHART_H - MARGIN.top - MARGIN.bottom;
  const barSlot = plotW / buckets.length;
  const barW = Math.max(4, barSlot - 2); // 2px de intervalo entre barras
  const y = (v: number) => MARGIN.top + plotH - (v / maxTotal) * plotH;
  const h = (v: number) => (v / maxTotal) * plotH;
  const hourLabel = (iso: string) =>
    new Date(iso).toLocaleTimeString("pt-PT", { hour: "2-digit", minute: "2-digit" });

  const hoveredBucket = hovered != null ? buckets[hovered] : null;

  return (
    <div className="mt-6 rounded-lg border border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-900">
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-gray-200 px-5 py-3 dark:border-gray-800">
        <h3 className="text-sm font-semibold">Atividade nas últimas 24h</h3>
        <div className="flex items-center gap-4 text-xs text-gray-500">
          <span className="flex items-center gap-1.5">
            <span className="h-2.5 w-2.5 rounded-sm" style={{ background: COLOR_ERROR }} />
            Erros
          </span>
          <span className="flex items-center gap-1.5">
            <span className="h-2.5 w-2.5 rounded-sm" style={{ background: COLOR_WARN }} />
            Avisos
          </span>
          <span className="flex items-center gap-1.5">
            <span className="h-2.5 w-2.5 rounded-sm" style={{ background: COLOR_OTHER }} />
            Restantes
          </span>
        </div>
      </div>

      <div className="relative px-5 pt-4">
        <svg
          viewBox={`0 0 ${CHART_W} ${CHART_H}`}
          className="w-full"
          role="img"
          aria-label="Volume de logs por hora nas últimas 24 horas"
        >
          {/* grelha recessiva + rótulos do eixo Y */}
          {[0.5, 1].map((f) => (
            <g key={f}>
              <line
                x1={MARGIN.left}
                x2={CHART_W - MARGIN.right}
                y1={y(maxTotal * f)}
                y2={y(maxTotal * f)}
                className="stroke-gray-200 dark:stroke-gray-800"
                strokeWidth={1}
              />
              <text
                x={MARGIN.left - 6}
                y={y(maxTotal * f) + 3}
                textAnchor="end"
                className="fill-gray-400 text-[10px]"
              >
                {Math.round(maxTotal * f)}
              </text>
            </g>
          ))}
          <line
            x1={MARGIN.left}
            x2={CHART_W - MARGIN.right}
            y1={y(0)}
            y2={y(0)}
            className="stroke-gray-300 dark:stroke-gray-700"
            strokeWidth={1}
          />

          {buckets.map((b, i) => {
            const x = MARGIN.left + i * barSlot + (barSlot - barW) / 2;
            const others = b.total - b.errors - b.warns;
            // empilhado de baixo para cima: restantes, avisos, erros (2px de folga entre segmentos)
            const parts = [
              { v: others, color: COLOR_OTHER },
              { v: b.warns, color: COLOR_WARN },
              { v: b.errors, color: COLOR_ERROR },
            ].filter((p) => p.v > 0);
            // pré-calcula os retângulos empilhados; a folga só se aplica quando
            // o segmento é mais alto do que ela, para nunca sair da sua banda
            let bottom = y(0);
            const segments = parts.map((p, j) => {
              const bandH = h(p.v);
              const bandTop = bottom - bandH;
              bottom = bandTop;
              const gap = j > 0 && bandH > 2 ? 2 : 0;
              return {
                y: bandTop + gap,
                height: bandH - gap,
                color: p.color,
                isTop: j === parts.length - 1,
              };
            });
            return (
              <g key={b.hour} opacity={hovered == null || hovered === i ? 1 : 0.45}>
                {segments.map((s, j) => (
                  <rect
                    key={j}
                    x={x}
                    y={s.y}
                    width={barW}
                    height={s.height}
                    rx={s.isTop ? 2 : 0}
                    fill={s.color}
                  />
                ))}
                {/* alvo de hover maior do que a marca */}
                <rect
                  x={MARGIN.left + i * barSlot}
                  y={MARGIN.top}
                  width={barSlot}
                  height={plotH}
                  fill="transparent"
                  onMouseEnter={() => setHovered(i)}
                  onMouseLeave={() => setHovered(null)}
                />
                {i % 4 === 0 && (
                  <text
                    x={x + barW / 2}
                    y={CHART_H - 6}
                    textAnchor="middle"
                    className="fill-gray-400 text-[10px]"
                  >
                    {hourLabel(b.hour)}
                  </text>
                )}
              </g>
            );
          })}
        </svg>

        {hoveredBucket && hovered != null && (
          <div
            className="pointer-events-none absolute top-2 z-10 rounded-md border border-gray-200 bg-white px-3 py-2 text-xs shadow-lg dark:border-gray-700 dark:bg-gray-800"
            style={{
              left: `calc(${((MARGIN.left + hovered * barSlot) / CHART_W) * 100}% ${
                hovered > buckets.length / 2 ? "- 11rem" : "+ 1rem"
              })`,
            }}
          >
            <p className="font-medium">{hourLabel(hoveredBucket.hour)}</p>
            <p className="mt-1 text-gray-500">
              {hoveredBucket.total} logs · {hoveredBucket.errors} erros ·{" "}
              {hoveredBucket.warns} avisos
            </p>
          </div>
        )}
      </div>

      <div className="grid gap-6 p-5 md:grid-cols-2">
        <BarList
          title="Erros mais frequentes"
          color={COLOR_ERROR}
          empty="Sem erros nas últimas 24h. 🎉"
          rows={stats.topErrors.map((e) => ({
            key: e.message,
            label: e.message,
            value: e.count,
            hint: null,
          }))}
        />
        <BarList
          title="Logs por serviço"
          color={COLOR_OTHER}
          empty="Sem logs nas últimas 24h."
          rows={stats.byService.map((s) => ({
            key: s.serviceId,
            label: s.serviceName,
            value: s.total,
            hint: s.errors > 0 ? `${s.errors} erros` : null,
          }))}
        />
      </div>
    </div>
  );
}

function BarList({
  title,
  color,
  empty,
  rows,
}: {
  title: string;
  color: string;
  empty: string;
  rows: { key: string; label: string; value: number; hint: string | null }[];
}) {
  const max = Math.max(1, ...rows.map((r) => r.value));
  return (
    <div>
      <h4 className="text-xs font-semibold uppercase text-gray-500">{title}</h4>
      {rows.length === 0 ? (
        <p className="mt-3 text-sm text-gray-400">{empty}</p>
      ) : (
        <ul className="mt-3 space-y-2">
          {rows.map((r) => (
            <li key={r.key} className="text-sm">
              <div className="flex items-baseline justify-between gap-3">
                <span className="min-w-0 truncate" title={r.label}>
                  {r.label}
                </span>
                <span className="shrink-0 font-medium tabular-nums">
                  {r.value}
                  {r.hint && (
                    <span className="ml-2 text-xs font-normal text-red-500">
                      {r.hint}
                    </span>
                  )}
                </span>
              </div>
              <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-gray-100 dark:bg-gray-800">
                <div
                  className="h-full rounded-full"
                  style={{ width: `${(r.value / max) * 100}%`, background: color }}
                />
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
