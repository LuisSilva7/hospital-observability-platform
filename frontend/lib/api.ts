export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export async function apiFetch<T>(
  path: string,
  init?: RequestInit
): Promise<T> {
  const res = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: { "Content-Type": "application/json", ...init?.headers },
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error(`API ${res.status}: ${await res.text()}`);
  }
  return res.json() as Promise<T>;
}

/**
 * Extrai a mensagem do backend de um erro lançado por apiFetch
 * (formato "API <status>: <corpo JSON>"). Devolve null se não for possível.
 */
export function extractApiMessage(e: unknown): string | null {
  if (!(e instanceof Error)) return null;
  const idx = e.message.indexOf(": ");
  if (idx === -1) return null;
  try {
    return JSON.parse(e.message.slice(idx + 2)).message ?? null;
  } catch {
    return null;
  }
}
