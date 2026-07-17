"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { use, useCallback, useEffect, useState } from "react";
import AiAnalysisPanel from "@/components/AiAnalysisPanel";
import ApiKeyReveal from "@/components/ApiKeyReveal";
import ServiceForm, { toRequestBody } from "@/components/ServiceForm";
import { apiFetch } from "@/lib/api";
import {
  ApiKey,
  CRITICALITY_LABELS,
  ENVIRONMENT_LABELS,
  Service,
} from "@/lib/types";

export default function ServiceDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = use(params);
  const router = useRouter();
  const [service, setService] = useState<Service | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [newKey, setNewKey] = useState<ApiKey | null>(null);
  const [copied, setCopied] = useState(false);

  const load = useCallback(() => {
    apiFetch<Service>(`/api/services/${id}`)
      .then((s) => {
        setService(s);
        setError(null);
      })
      .catch(() => setError("Serviço não encontrado."));
  }, [id]);

  useEffect(load, [load]);

  const copyEndpoint = async () => {
    if (!service) return;
    await navigator.clipboard.writeText(service.ingestEndpoint);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const regenerate = async () => {
    if (
      !confirm(
        "Regenerar a API key? A chave atual deixa de funcionar imediatamente."
      )
    )
      return;
    const key = await apiFetch<ApiKey>(`/api/services/${id}/api-key`, {
      method: "POST",
    });
    setNewKey(key);
    load();
  };

  const toggleActive = async () => {
    if (!service) return;
    await apiFetch<Service>(`/api/services/${id}/active`, {
      method: "PATCH",
      body: JSON.stringify({ active: !service.active }),
    });
    load();
  };

  const remove = async () => {
    if (!service) return;
    if (
      !confirm(
        `Eliminar o serviço "${service.name}"? Esta ação não pode ser desfeita.`
      )
    )
      return;
    await apiFetch<void>(`/api/services/${id}`, { method: "DELETE" }).catch(
      () => undefined
    );
    router.push("/services");
  };

  if (error) {
    return (
      <div>
        <p className="rounded-md bg-red-50 p-3 text-sm text-red-700 dark:bg-red-950 dark:text-red-300">
          {error}
        </p>
        <Link
          href="/services"
          className="mt-4 inline-block text-sm text-blue-600 hover:underline"
        >
          ← Voltar aos serviços
        </Link>
      </div>
    );
  }

  if (!service) {
    return <p className="text-sm text-gray-400">A carregar…</p>;
  }

  const curlExample = `curl -X POST '${service.ingestEndpoint}' \\
  -H 'X-API-Key: <a-tua-api-key>' \\
  -H 'Content-Type: application/json' \\
  -d '{
    "timestamp": "${new Date().toISOString()}",
    "level": "INFO",
    "message": "Exemplo de log",
    "eventType": "heartbeat"
  }'`;

  if (editing) {
    return (
      <div>
        <h2 className="text-2xl font-semibold">Editar serviço</h2>
        <div className="mt-6">
          <ServiceForm
            initial={service}
            submitLabel="Guardar alterações"
            onSubmit={async (values) => {
              await apiFetch<Service>(`/api/services/${id}`, {
                method: "PUT",
                body: JSON.stringify(toRequestBody(values)),
              });
              setEditing(false);
              load();
            }}
          />
          <button
            onClick={() => setEditing(false)}
            className="mt-3 text-sm text-gray-500 hover:underline"
          >
            Cancelar
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-3xl">
      <Link href="/services" className="text-sm text-blue-600 hover:underline">
        ← Serviços
      </Link>
      <div className="mt-2 flex items-start justify-between">
        <div>
          <h2 className="text-2xl font-semibold">{service.name}</h2>
          <p className="mt-1 text-sm text-gray-500">
            {service.description || "Sem descrição."}
          </p>
        </div>
        <span className="rounded-full bg-gray-100 px-3 py-1 text-xs font-medium text-gray-600 dark:bg-gray-800 dark:text-gray-300">
          {service.status}
        </span>
      </div>

      <dl className="mt-6 grid grid-cols-2 gap-4 rounded-lg border border-gray-200 bg-white p-6 text-sm dark:border-gray-800 dark:bg-gray-900 sm:grid-cols-4">
        <div>
          <dt className="text-xs uppercase text-gray-500">Ambiente</dt>
          <dd className="mt-1 font-medium">
            {ENVIRONMENT_LABELS[service.environment]}
          </dd>
        </div>
        <div>
          <dt className="text-xs uppercase text-gray-500">Criticidade</dt>
          <dd className="mt-1 font-medium">
            {CRITICALITY_LABELS[service.criticality]}
          </dd>
        </div>
        <div>
          <dt className="text-xs uppercase text-gray-500">Intervalo esperado</dt>
          <dd className="mt-1 font-medium">
            {service.expectedIntervalMinutes
              ? `${service.expectedIntervalMinutes} min (±${service.toleranceMinutes ?? 0})`
              : "—"}
          </dd>
        </div>
        <div>
          <dt className="text-xs uppercase text-gray-500">Último log</dt>
          <dd className="mt-1 font-medium">
            {service.lastSeenAt
              ? new Date(service.lastSeenAt).toLocaleString("pt-PT")
              : "Nunca"}
          </dd>
        </div>
      </dl>

      {newKey && (
        <div className="mt-6">
          <ApiKeyReveal apiKey={newKey} />
        </div>
      )}

      <div className="mt-6 rounded-lg border border-gray-200 bg-white p-6 dark:border-gray-800 dark:bg-gray-900">
        <h3 className="text-sm font-semibold">Integração</h3>
        <p className="mt-1 text-xs text-gray-500">
          Envia logs JSON por POST para o endpoint abaixo, autenticados com o
          header <code className="font-mono">X-API-Key</code>
          {service.apiKeyPrefix && (
            <>
              {" "}
              (chave ativa: <code className="font-mono">{service.apiKeyPrefix}…</code>)
            </>
          )}
          .
        </p>
        <div className="mt-3 flex items-center gap-2">
          <code className="flex-1 break-all rounded bg-gray-100 px-3 py-2 font-mono text-xs dark:bg-gray-800">
            POST {service.ingestEndpoint}
          </code>
          <button
            onClick={copyEndpoint}
            className="shrink-0 rounded-md border border-gray-300 px-3 py-2 text-xs font-medium hover:bg-gray-100 dark:border-gray-700 dark:hover:bg-gray-800"
          >
            {copied ? "Copiado ✓" : "Copiar"}
          </button>
        </div>
        <pre className="mt-3 overflow-x-auto rounded bg-gray-100 p-3 font-mono text-xs leading-relaxed dark:bg-gray-800">
          {curlExample}
        </pre>
        <p className="mt-2 text-xs text-gray-400">
          A resposta devolve o ID do evento criado. Os logs recebidos ficam
          pesquisáveis no Log Explorer (Módulo 4).
        </p>
      </div>

      <div className="mt-6 flex flex-wrap gap-3">
        <button
          onClick={() => setEditing(true)}
          className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
        >
          Editar
        </button>
        <button
          onClick={regenerate}
          className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium hover:bg-gray-100 dark:border-gray-700 dark:hover:bg-gray-800"
        >
          Regenerar API key
        </button>
        <button
          onClick={toggleActive}
          className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium hover:bg-gray-100 dark:border-gray-700 dark:hover:bg-gray-800"
        >
          {service.active ? "Desativar" : "Ativar"}
        </button>
        <button
          onClick={remove}
          className="rounded-md border border-red-300 px-4 py-2 text-sm font-medium text-red-600 hover:bg-red-50 dark:border-red-800 dark:hover:bg-red-950"
        >
          Eliminar
        </button>
      </div>

      <AiAnalysisPanel
        endpoint={`/api/services/${id}/analyses`}
        emptyHint="Ainda sem análises. Usa o botão para a IA resumir o estado operacional recente deste serviço a partir dos últimos logs — sem precisar de um alerta."
      />
    </div>
  );
}
