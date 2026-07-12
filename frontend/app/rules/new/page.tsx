"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { apiFetch } from "@/lib/api";
import { Service } from "@/lib/types";
import {
  Condition,
  Operator,
  OPERATOR_LABELS,
  Rule,
  RULE_TYPE_INFO,
  RuleType,
  Severity,
  SEVERITY_LABELS,
} from "@/lib/rules";

const inputClass =
  "rounded-md border border-gray-300 bg-white px-3 py-2 text-sm dark:border-gray-700 dark:bg-gray-900";

const STEPS = ["Serviço", "Tipo de regra", "Condições", "Severidade e nome"];

export default function NewRulePage() {
  const router = useRouter();
  const [services, setServices] = useState<Service[]>([]);
  const [step, setStep] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const [serviceId, setServiceId] = useState("");
  const [type, setType] = useState<RuleType>("EVENT_MATCH");
  const [conditions, setConditions] = useState<Condition[]>([
    { fieldPath: "level", operator: "EQUALS", expectedValue: "ERROR" },
  ]);
  const [windowMinutes, setWindowMinutes] = useState("10");
  const [threshold, setThreshold] = useState("5");
  const [name, setName] = useState("");
  const [severity, setSeverity] = useState<Severity>("HIGH");
  const [cooldownMinutes, setCooldownMinutes] = useState("10");

  useEffect(() => {
    apiFetch<Service[]>("/api/services").then((s) => {
      setServices(s);
      if (s.length > 0) setServiceId((prev) => prev || s[0].id);
    });
  }, []);

  const canNext = () => {
    if (step === 0) return !!serviceId;
    if (step === 2) {
      if (type === "NO_ACTIVITY") return Number(windowMinutes) > 0;
      const condsOk =
        conditions.length > 0 &&
        conditions.every((c) => c.fieldPath.trim() && c.expectedValue.trim());
      if (type === "COUNT_THRESHOLD")
        return condsOk && Number(windowMinutes) > 0 && Number(threshold) > 0;
      return condsOk;
    }
    if (step === 3) return !!name.trim();
    return true;
  };

  const setCondition = (i: number, field: keyof Condition, value: string) =>
    setConditions((prev) =>
      prev.map((c, idx) => (idx === i ? { ...c, [field]: value } : c))
    );

  const submit = async () => {
    setSaving(true);
    setError(null);
    try {
      await apiFetch<Rule>("/api/rules", {
        method: "POST",
        body: JSON.stringify({
          serviceId,
          name,
          type,
          severity,
          cooldownMinutes: Number(cooldownMinutes),
          windowMinutes:
            type === "EVENT_MATCH" ? null : Number(windowMinutes) || null,
          threshold: type === "COUNT_THRESHOLD" ? Number(threshold) : null,
          conditions: type === "NO_ACTIVITY" ? [] : conditions,
        }),
      });
      router.push("/rules");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Erro ao criar a regra");
      setSaving(false);
    }
  };

  const suggestedName = () => {
    const svc = services.find((s) => s.id === serviceId)?.name ?? "";
    if (type === "NO_ACTIVITY") return `${svc}: sem logs por ${windowMinutes} min`;
    if (type === "COUNT_THRESHOLD")
      return `${svc}: ${threshold}+ eventos em ${windowMinutes} min`;
    const c = conditions[0];
    return c ? `${svc}: ${c.fieldPath} ${OPERATOR_LABELS[c.operator]} ${c.expectedValue}` : svc;
  };

  return (
    <div className="max-w-2xl">
      <Link href="/rules" className="text-sm text-blue-600 hover:underline">
        ← Regras
      </Link>
      <h2 className="mt-2 text-2xl font-semibold">Nova regra</h2>

      <ol className="mt-4 flex gap-2 text-xs">
        {STEPS.map((label, i) => (
          <li
            key={label}
            className={`rounded-full px-3 py-1 ${
              i === step
                ? "bg-blue-600 font-medium text-white"
                : i < step
                  ? "bg-blue-100 text-blue-700 dark:bg-blue-950 dark:text-blue-300"
                  : "bg-gray-100 text-gray-400 dark:bg-gray-800"
            }`}
          >
            {i + 1}. {label}
          </li>
        ))}
      </ol>

      <div className="mt-6 rounded-lg border border-gray-200 bg-white p-6 dark:border-gray-800 dark:bg-gray-900">
        {step === 0 && (
          <div>
            <label className="text-sm font-medium">
              Que serviço queres monitorizar?
            </label>
            <select
              value={serviceId}
              onChange={(e) => setServiceId(e.target.value)}
              className={`${inputClass} mt-2 w-full`}
            >
              {services.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>
            {services.length === 0 && (
              <p className="mt-3 text-sm text-gray-500">
                Não existem serviços.{" "}
                <Link href="/services/new" className="text-blue-600 hover:underline">
                  Cria um primeiro
                </Link>
                .
              </p>
            )}
          </div>
        )}

        {step === 1 && (
          <div className="space-y-3">
            {(Object.keys(RULE_TYPE_INFO) as RuleType[]).map((t) => (
              <label
                key={t}
                className={`flex cursor-pointer items-start gap-3 rounded-lg border p-4 ${
                  type === t
                    ? "border-blue-500 bg-blue-50 dark:bg-blue-950"
                    : "border-gray-200 dark:border-gray-700"
                }`}
              >
                <input
                  type="radio"
                  checked={type === t}
                  onChange={() => setType(t)}
                  className="mt-1"
                />
                <span>
                  <span className="block text-sm font-medium">
                    {RULE_TYPE_INFO[t].label}
                  </span>
                  <span className="block text-xs text-gray-500">
                    {RULE_TYPE_INFO[t].description}
                  </span>
                </span>
              </label>
            ))}
          </div>
        )}

        {step === 2 && (
          <div className="space-y-4">
            {type !== "NO_ACTIVITY" && (
              <div>
                <p className="text-sm font-medium">
                  Condições (todas têm de se verificar)
                </p>
                <p className="text-xs text-gray-500">
                  O campo pode ser normalizado (level, message, eventType) ou
                  qualquer campo do payload JSON (ex.: errorCode,
                  responseTimeMs, data.code).
                </p>
                {conditions.map((c, i) => (
                  <div key={i} className="mt-2 flex items-center gap-2">
                    <input
                      value={c.fieldPath}
                      onChange={(e) => setCondition(i, "fieldPath", e.target.value)}
                      placeholder="campo"
                      className={`${inputClass} w-40`}
                    />
                    <select
                      value={c.operator}
                      onChange={(e) =>
                        setCondition(i, "operator", e.target.value as Operator)
                      }
                      className={inputClass}
                    >
                      {(Object.keys(OPERATOR_LABELS) as Operator[]).map((op) => (
                        <option key={op} value={op}>
                          {OPERATOR_LABELS[op]}
                        </option>
                      ))}
                    </select>
                    <input
                      value={c.expectedValue}
                      onChange={(e) =>
                        setCondition(i, "expectedValue", e.target.value)
                      }
                      placeholder="valor"
                      className={`${inputClass} flex-1`}
                    />
                    {conditions.length > 1 && (
                      <button
                        onClick={() =>
                          setConditions((prev) => prev.filter((_, idx) => idx !== i))
                        }
                        className="text-gray-400 hover:text-red-500"
                        title="Remover condição"
                      >
                        ✕
                      </button>
                    )}
                  </div>
                ))}
                <button
                  onClick={() =>
                    setConditions((prev) => [
                      ...prev,
                      { fieldPath: "", operator: "EQUALS", expectedValue: "" },
                    ])
                  }
                  className="mt-2 text-sm text-blue-600 hover:underline"
                >
                  + Adicionar condição
                </button>
              </div>
            )}

            {type !== "EVENT_MATCH" && (
              <div className="flex gap-4">
                <div>
                  <label className="text-sm font-medium">
                    {type === "NO_ACTIVITY"
                      ? "Minutos sem logs"
                      : "Janela (minutos)"}
                  </label>
                  <input
                    type="number"
                    min={1}
                    value={windowMinutes}
                    onChange={(e) => setWindowMinutes(e.target.value)}
                    className={`${inputClass} mt-1 w-32`}
                  />
                </div>
                {type === "COUNT_THRESHOLD" && (
                  <div>
                    <label className="text-sm font-medium">Nº de eventos (≥)</label>
                    <input
                      type="number"
                      min={1}
                      value={threshold}
                      onChange={(e) => setThreshold(e.target.value)}
                      className={`${inputClass} mt-1 w-32`}
                    />
                  </div>
                )}
              </div>
            )}
          </div>
        )}

        {step === 3 && (
          <div className="space-y-4">
            <div>
              <label className="text-sm font-medium">Nome da regra</label>
              <div className="mt-1 flex gap-2">
                <input
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  maxLength={160}
                  className={`${inputClass} flex-1`}
                  placeholder={suggestedName()}
                />
                <button
                  onClick={() => setName(suggestedName())}
                  className="rounded-md border border-gray-300 px-3 text-xs hover:bg-gray-100 dark:border-gray-700 dark:hover:bg-gray-800"
                >
                  Sugerir
                </button>
              </div>
            </div>
            <div className="flex gap-4">
              <div>
                <label className="text-sm font-medium">Severidade</label>
                <select
                  value={severity}
                  onChange={(e) => setSeverity(e.target.value as Severity)}
                  className={`${inputClass} mt-1 block`}
                >
                  {(Object.keys(SEVERITY_LABELS) as Severity[]).map((s) => (
                    <option key={s} value={s}>
                      {SEVERITY_LABELS[s]}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="text-sm font-medium">Cooldown (minutos)</label>
                <input
                  type="number"
                  min={0}
                  value={cooldownMinutes}
                  onChange={(e) => setCooldownMinutes(e.target.value)}
                  className={`${inputClass} mt-1 w-32`}
                />
                <p className="mt-1 text-xs text-gray-500">
                  Tempo mínimo entre disparos, para evitar alertas repetidos.
                </p>
              </div>
            </div>
          </div>
        )}
      </div>

      {error && (
        <p className="mt-4 rounded-md bg-red-50 p-3 text-sm text-red-700 dark:bg-red-950 dark:text-red-300">
          {error}
        </p>
      )}

      <div className="mt-6 flex justify-between">
        <button
          onClick={() => setStep((s) => s - 1)}
          disabled={step === 0}
          className="rounded-md border border-gray-300 px-4 py-2 text-sm disabled:opacity-40 dark:border-gray-700"
        >
          ← Anterior
        </button>
        {step < STEPS.length - 1 ? (
          <button
            onClick={() => setStep((s) => s + 1)}
            disabled={!canNext()}
            className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
          >
            Seguinte →
          </button>
        ) : (
          <button
            onClick={submit}
            disabled={!canNext() || saving}
            className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
          >
            {saving ? "A criar…" : "Criar regra"}
          </button>
        )}
      </div>
    </div>
  );
}
