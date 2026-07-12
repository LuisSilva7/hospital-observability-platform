"use client";

import Link from "next/link";
import { useState } from "react";
import ApiKeyReveal from "@/components/ApiKeyReveal";
import ServiceForm, { toRequestBody } from "@/components/ServiceForm";
import { apiFetch } from "@/lib/api";
import { ServiceCreated } from "@/lib/types";

export default function NewServicePage() {
  const [created, setCreated] = useState<ServiceCreated | null>(null);

  if (created) {
    return (
      <div className="max-w-xl">
        <h2 className="text-2xl font-semibold">Serviço criado ✓</h2>
        <p className="mt-1 text-sm text-gray-500">
          <strong>{created.service.name}</strong> está registado e pronto a
          receber logs.
        </p>
        <div className="mt-6">
          <ApiKeyReveal
            apiKey={created.apiKey}
            ingestEndpoint={created.service.ingestEndpoint}
          />
        </div>
        <div className="mt-6 flex gap-3">
          <Link
            href={`/services/${created.service.id}`}
            className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
          >
            Ver serviço
          </Link>
          <Link
            href="/services"
            className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium hover:bg-gray-100 dark:border-gray-700 dark:hover:bg-gray-800"
          >
            Voltar à lista
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div>
      <h2 className="text-2xl font-semibold">Novo serviço</h2>
      <p className="mt-1 text-sm text-gray-500">
        Regista um serviço para obteres um endpoint de ingestão e uma API key.
      </p>
      <div className="mt-6">
        <ServiceForm
          submitLabel="Criar serviço"
          onSubmit={async (values) => {
            const result = await apiFetch<ServiceCreated>("/api/services", {
              method: "POST",
              body: JSON.stringify(toRequestBody(values)),
            });
            setCreated(result);
          }}
        />
      </div>
    </div>
  );
}
