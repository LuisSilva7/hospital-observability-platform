"use client";

import { useCallback, useEffect, useState } from "react";
import { apiFetch } from "@/lib/api";
import { Rule } from "@/lib/rules";

type Webhook = {
  url: string;
  method: string | null;
  headers: Record<string, string>;
  payloadTemplate: string | null;
};

type Automation = {
  id: string;
  ruleId: string;
  ruleName: string;
  name: string;
  enabled: boolean;
  createdAt: string;
  webhook: Webhook | null;
  actionId: string | null;
};

type Execution = {
  id: string;
  status: "SUCCESS" | "FAILED";
  attempts: number;
  responseCode: number | null;
  responseBody: string | null;
  error: string | null;
  executedAt: string;
};

const inputClass =
  "mt-1 w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm dark:border-gray-700 dark:bg-gray-900";

export default function AutomationsPage() {
  const [automations, setAutomations] = useState<Automation[] | null>(null);
  const [rules, setRules] = useState<Rule[]>([]);
  const [showForm, setShowForm] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [testResults, setTestResults] = useState<Record<string, Execution>>({});
  const [testing, setTesting] = useState<string | null>(null);

  const [form, setForm] = useState({ name: "", ruleId: "", url: "" });
  const [saving, setSaving] = useState(false);

  const load = useCallback(() => {
    apiFetch<Automation[]>("/api/automations")
      .then((a) => {
        setAutomations(a);
        setError(null);
      })
      .catch(() => setError("Não foi possível carregar as automações."));
    apiFetch<Rule[]>("/api/rules").then(setRules).catch(() => {});
  }, []);

  useEffect(load, [load]);

  const create = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      await apiFetch("/api/automations", {
        method: "POST",
        body: JSON.stringify({
          name: form.name,
          ruleId: form.ruleId,
          webhook: { url: form.url },
        }),
      });
      setShowForm(false);
      setForm({ name: "", ruleId: "", url: "" });
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Erro ao criar");
    } finally {
      setSaving(false);
    }
  };

  const test = async (a: Automation) => {
    setTesting(a.id);
    try {
      const result = await apiFetch<Execution>(`/api/automations/${a.id}/test`, {
        method: "POST",
      });
      setTestResults((prev) => ({ ...prev, [a.id]: result }));
    } finally {
      setTesting(null);
    }
  };

  const toggle = async (a: Automation) => {
    await apiFetch(`/api/automations/${a.id}/enabled`, {
      method: "PATCH",
      body: JSON.stringify({ enabled: !a.enabled }),
    });
    load();
  };

  const remove = async (a: Automation) => {
    if (!confirm(`Eliminar a automação "${a.name}"?`)) return;
    await apiFetch<void>(`/api/automations/${a.id}`, { method: "DELETE" }).catch(
      () => undefined
    );
    load();
  };

  return (
    <div>
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-semibold">Automações</h2>
          <p className="mt-1 text-sm text-gray-500">
            Webhooks executados quando uma regra cria um alerta — por exemplo,
            um workflow do n8n que envia email ou mensagem.
          </p>
        </div>
        <button
          onClick={() => setShowForm((s) => !s)}
          className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
        >
          {showForm ? "Cancelar" : "+ Nova automação"}
        </button>
      </div>

      {error && (
        <p className="mt-4 rounded-md bg-red-50 p-3 text-sm text-red-700 dark:bg-red-950 dark:text-red-300">
          {error}
        </p>
      )}

      {showForm && (
        <form
          onSubmit={create}
          className="mt-6 max-w-xl space-y-4 rounded-lg border border-gray-200 bg-white p-6 dark:border-gray-800 dark:bg-gray-900"
        >
          <div>
            <label className="text-sm font-medium">Nome *</label>
            <input
              required
              value={form.name}
              onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
              className={inputClass}
              placeholder="Ex.: Notificar equipa via n8n"
            />
          </div>
          <div>
            <label className="text-sm font-medium">Regra *</label>
            <select
              required
              value={form.ruleId}
              onChange={(e) => setForm((f) => ({ ...f, ruleId: e.target.value }))}
              className={inputClass}
            >
              <option value="">Escolhe a regra…</option>
              {rules.map((r) => (
                <option key={r.id} value={r.id}>
                  {r.name}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="text-sm font-medium">URL do webhook *</label>
            <input
              required
              type="url"
              value={form.url}
              onChange={(e) => setForm((f) => ({ ...f, url: e.target.value }))}
              className={inputClass}
              placeholder="http://localhost:5678/webhook/alerta-hospitalar"
            />
            <p className="mt-1 text-xs text-gray-500">
              O alerta é enviado por POST em JSON (alertId, title, severity,
              serviceName, status, openedAt).
            </p>
          </div>
          <button
            type="submit"
            disabled={saving}
            className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
          >
            {saving ? "A criar…" : "Criar automação"}
          </button>
        </form>
      )}

      {automations && automations.length === 0 && !showForm && (
        <div className="mt-6 rounded-lg border border-dashed border-gray-300 p-8 text-center text-sm text-gray-500 dark:border-gray-700">
          Sem automações. Cria uma para chamar um webhook (ex.: n8n) quando uma
          regra disparar.
        </div>
      )}

      <div className="mt-6 space-y-4">
        {automations?.map((a) => {
          const result = testResults[a.id];
          return (
            <div
              key={a.id}
              className="rounded-lg border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-gray-900"
            >
              <div className="flex items-start justify-between gap-4">
                <div className="min-w-0">
                  <p className="font-medium">{a.name}</p>
                  <p className="mt-0.5 text-xs text-gray-500">
                    Regra: {a.ruleName}
                  </p>
                  <p className="mt-1 truncate font-mono text-xs text-gray-400">
                    POST {a.webhook?.url}
                  </p>
                </div>
                <div className="flex shrink-0 items-center gap-2">
                  <button
                    onClick={() => toggle(a)}
                    className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                      a.enabled
                        ? "bg-green-100 text-green-700 dark:bg-green-950 dark:text-green-300"
                        : "bg-gray-100 text-gray-500 dark:bg-gray-800"
                    }`}
                  >
                    {a.enabled ? "Ativa" : "Inativa"}
                  </button>
                  <button
                    onClick={() => test(a)}
                    disabled={testing === a.id}
                    className="rounded-md border border-gray-300 px-3 py-1.5 text-xs font-medium hover:bg-gray-100 disabled:opacity-50 dark:border-gray-700 dark:hover:bg-gray-800"
                  >
                    {testing === a.id ? "A testar…" : "Testar webhook"}
                  </button>
                  <button
                    onClick={() => remove(a)}
                    className="text-xs text-red-500 hover:underline"
                  >
                    Eliminar
                  </button>
                </div>
              </div>
              {result && (
                <div
                  className={`mt-3 rounded-md p-3 text-xs ${
                    result.status === "SUCCESS"
                      ? "bg-green-50 text-green-700 dark:bg-green-950 dark:text-green-300"
                      : "bg-red-50 text-red-700 dark:bg-red-950 dark:text-red-300"
                  }`}
                >
                  {result.status === "SUCCESS" ? "✓ Sucesso" : "✗ Falhou"} ·{" "}
                  {result.attempts} tentativa(s)
                  {result.responseCode != null && ` · HTTP ${result.responseCode}`}
                  {result.error && ` · ${result.error}`}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
