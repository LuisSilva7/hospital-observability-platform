"use client";

import { useState } from "react";
import { ApiKey } from "@/lib/types";

export default function ApiKeyReveal({
  apiKey,
  ingestEndpoint,
}: {
  apiKey: ApiKey;
  ingestEndpoint?: string;
}) {
  const [copied, setCopied] = useState(false);

  const copy = async () => {
    await navigator.clipboard.writeText(apiKey.apiKey);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="rounded-lg border border-amber-300 bg-amber-50 p-4 dark:border-amber-700 dark:bg-amber-950">
      <p className="text-sm font-semibold text-amber-800 dark:text-amber-200">
        ⚠️ Guarda esta API key agora — não volta a ser mostrada.
      </p>
      <div className="mt-3 flex items-center gap-2">
        <code className="flex-1 break-all rounded bg-white px-3 py-2 font-mono text-sm dark:bg-gray-900">
          {apiKey.apiKey}
        </code>
        <button
          onClick={copy}
          className="shrink-0 rounded-md bg-amber-600 px-3 py-2 text-sm font-medium text-white hover:bg-amber-700"
        >
          {copied ? "Copiado ✓" : "Copiar"}
        </button>
      </div>
      {ingestEndpoint && (
        <p className="mt-3 text-xs text-amber-700 dark:text-amber-300">
          Endpoint de ingestão: <code className="font-mono">{ingestEndpoint}</code>
        </p>
      )}
    </div>
  );
}
