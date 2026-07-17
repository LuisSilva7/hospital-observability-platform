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

type ActionType = "WEBHOOK" | "AI_ANALYSIS" | "EMAIL" | "TEAMS";

type Automation = {
  id: string;
  ruleId: string;
  ruleName: string;
  name: string;
  enabled: boolean;
  createdAt: string;
  type: ActionType;
  webhook: Webhook | null;
  email: { to: string; subjectTemplate: string | null } | null;
  teams: { url: string } | null;
  actionId: string | null;
};

const TYPE_INFO: Record<ActionType, { label: string; badge: string }> = {
  WEBHOOK: {
    label: "Webhook",
    badge: "bg-blue-100 text-blue-700 dark:bg-blue-950 dark:text-blue-300",
  },
  AI_ANALYSIS: {
    label: "✨ Análise IA",
    badge: "bg-violet-100 text-violet-700 dark:bg-violet-950 dark:text-violet-300",
  },
  EMAIL: {
    label: "✉️ Email",
    badge: "bg-emerald-100 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300",
  },
  TEAMS: {
    label: "Teams",
    badge: "bg-sky-100 text-sky-700 dark:bg-sky-950 dark:text-sky-300",
  },
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

  const [form, setForm] = useState({
    name: "",
    ruleId: "",
    url: "",
    to: "",
    teamsUrl: "",
    type: "WEBHOOK" as ActionType,
  });
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
          type: form.type,
          webhook: form.type === "WEBHOOK" ? { url: form.url } : null,
          email: form.type === "EMAIL" ? { to: form.to } : null,
          teams: form.type === "TEAMS" ? { url: form.teamsUrl } : null,
        }),
      });
      setShowForm(false);
      setForm({ name: "", ruleId: "", url: "", to: "", teamsUrl: "", type: "WEBHOOK" });
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
            Ações executadas quando uma regra cria um alerta: webhook (ex.:
            n8n), email, cartão no Teams ou análise automática com IA.
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
            <label className="text-sm font-medium">Ação *</label>
            <select
              value={form.type}
              onChange={(e) =>
                setForm((f) => ({ ...f, type: e.target.value as ActionType }))
              }
              className={inputClass}
            >
              <option value="WEBHOOK">Webhook (ex.: n8n)</option>
              <option value="EMAIL">Email (SMTP)</option>
              <option value="TEAMS">Microsoft Teams</option>
              <option value="AI_ANALYSIS">Análise com IA</option>
            </select>
          </div>
          {form.type === "WEBHOOK" && (
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
          )}
          {form.type === "EMAIL" && (
            <div>
              <label className="text-sm font-medium">
                Destinatários (separados por vírgula) *
              </label>
              <input
                required
                value={form.to}
                onChange={(e) => setForm((f) => ({ ...f, to: e.target.value }))}
                className={inputClass}
                placeholder="equipa@hospital.pt, chefe.turno@hospital.pt"
              />
              <p className="mt-1 text-xs text-gray-500">
                Requer SMTP configurado no backend (<code>SMTP_HOST</code>,{" "}
                <code>SMTP_USERNAME</code>, <code>SMTP_PASSWORD</code>,{" "}
                <code>SMTP_FROM</code> — ver .env.example).
              </p>
            </div>
          )}
          {form.type === "TEAMS" && (
            <div>
              <label className="text-sm font-medium">
                URL do incoming webhook do Teams *
              </label>
              <input
                required
                type="url"
                value={form.teamsUrl}
                onChange={(e) =>
                  setForm((f) => ({ ...f, teamsUrl: e.target.value }))
                }
                className={inputClass}
                placeholder="https://outlook.office.com/webhook/…"
              />
              <p className="mt-1 text-xs text-gray-500">
                Envia um cartão com título, serviço, severidade e estado do
                alerta para o canal do Teams.
              </p>
            </div>
          )}
          {form.type === "AI_ANALYSIS" && (
            <p className="rounded-md bg-violet-50 p-3 text-xs text-violet-700 dark:bg-violet-950 dark:text-violet-300">
              Quando a regra criar um alerta, é gerada automaticamente uma
              análise de IA (resumo, causa provável, evidências e
              recomendações), visível no detalhe do alerta. Requer{" "}
              <code className="font-mono">LLM_API_KEY</code> configurada no
              backend.
            </p>
          )}
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
                  <p className="font-medium">
                    {a.name}{" "}
                    <span
                      className={`ml-1 rounded-full px-2 py-0.5 text-xs font-medium ${TYPE_INFO[a.type].badge}`}
                    >
                      {TYPE_INFO[a.type].label}
                    </span>
                  </p>
                  <p className="mt-0.5 text-xs text-gray-500">
                    Regra: {a.ruleName}
                  </p>
                  {a.type === "WEBHOOK" && (
                    <p className="mt-1 truncate font-mono text-xs text-gray-400">
                      POST {a.webhook?.url}
                    </p>
                  )}
                  {a.type === "EMAIL" && (
                    <p className="mt-1 truncate text-xs text-gray-400">
                      Para: {a.email?.to}
                    </p>
                  )}
                  {a.type === "TEAMS" && (
                    <p className="mt-1 truncate font-mono text-xs text-gray-400">
                      {a.teams?.url}
                    </p>
                  )}
                  {a.type === "AI_ANALYSIS" && (
                    <p className="mt-1 text-xs text-gray-400">
                      Gera uma análise de IA quando o alerta é criado
                    </p>
                  )}
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
                    {testing === a.id ? "A testar…" : "Testar"}
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
