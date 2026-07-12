"use client";

import { useState } from "react";
import {
  Criticality,
  CRITICALITY_LABELS,
  Environment,
  ENVIRONMENT_LABELS,
  Service,
} from "@/lib/types";

export type ServiceFormValues = {
  name: string;
  description: string;
  environment: Environment;
  criticality: Criticality;
  expectedIntervalMinutes: string;
  toleranceMinutes: string;
};

const inputClass =
  "mt-1 w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm dark:border-gray-700 dark:bg-gray-900";

export default function ServiceForm({
  initial,
  submitLabel,
  onSubmit,
}: {
  initial?: Service;
  submitLabel: string;
  onSubmit: (values: ServiceFormValues) => Promise<void>;
}) {
  const [values, setValues] = useState<ServiceFormValues>({
    name: initial?.name ?? "",
    description: initial?.description ?? "",
    environment: initial?.environment ?? "SIMULATION",
    criticality: initial?.criticality ?? "MEDIUM",
    expectedIntervalMinutes: initial?.expectedIntervalMinutes?.toString() ?? "",
    toleranceMinutes: initial?.toleranceMinutes?.toString() ?? "",
  });
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const set = (field: keyof ServiceFormValues, v: string) =>
    setValues((prev) => ({ ...prev, [field]: v }));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      await onSubmit(values);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Erro ao guardar");
      setSaving(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="max-w-xl space-y-4">
      <div>
        <label className="text-sm font-medium">Nome *</label>
        <input
          required
          maxLength={120}
          value={values.name}
          onChange={(e) => set("name", e.target.value)}
          className={inputClass}
          placeholder="Ex.: Laboratory Integration Service"
        />
      </div>
      <div>
        <label className="text-sm font-medium">Descrição</label>
        <textarea
          value={values.description}
          onChange={(e) => set("description", e.target.value)}
          className={inputClass}
          rows={3}
        />
      </div>
      <div className="grid grid-cols-2 gap-4">
        <div>
          <label className="text-sm font-medium">Ambiente *</label>
          <select
            value={values.environment}
            onChange={(e) => set("environment", e.target.value)}
            className={inputClass}
          >
            {Object.entries(ENVIRONMENT_LABELS).map(([v, label]) => (
              <option key={v} value={v}>
                {label}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label className="text-sm font-medium">Criticidade *</label>
          <select
            value={values.criticality}
            onChange={(e) => set("criticality", e.target.value)}
            className={inputClass}
          >
            {Object.entries(CRITICALITY_LABELS).map(([v, label]) => (
              <option key={v} value={v}>
                {label}
              </option>
            ))}
          </select>
        </div>
      </div>
      <div className="grid grid-cols-2 gap-4">
        <div>
          <label className="text-sm font-medium">
            Intervalo esperado de logs (min)
          </label>
          <input
            type="number"
            min={1}
            value={values.expectedIntervalMinutes}
            onChange={(e) => set("expectedIntervalMinutes", e.target.value)}
            className={inputClass}
            placeholder="Ex.: 5"
          />
        </div>
        <div>
          <label className="text-sm font-medium">Tolerância (min)</label>
          <input
            type="number"
            min={0}
            value={values.toleranceMinutes}
            onChange={(e) => set("toleranceMinutes", e.target.value)}
            className={inputClass}
            placeholder="Ex.: 2"
          />
        </div>
      </div>
      {error && (
        <p className="rounded-md bg-red-50 p-3 text-sm text-red-700 dark:bg-red-950 dark:text-red-300">
          {error}
        </p>
      )}
      <button
        type="submit"
        disabled={saving}
        className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
      >
        {saving ? "A guardar…" : submitLabel}
      </button>
    </form>
  );
}

export function toRequestBody(values: ServiceFormValues) {
  return {
    name: values.name,
    description: values.description || null,
    environment: values.environment,
    criticality: values.criticality,
    expectedIntervalMinutes: values.expectedIntervalMinutes
      ? Number(values.expectedIntervalMinutes)
      : null,
    toleranceMinutes: values.toleranceMinutes
      ? Number(values.toleranceMinutes)
      : null,
  };
}
