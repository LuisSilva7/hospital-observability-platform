// Regista os 3 serviços de demonstração na plataforma e escreve config.json
// com os serviceIds e API keys. Idempotente: se o serviço já existir,
// regenera a API key (a antiga deixa de funcionar).
//
// Uso: node setup.js [baseUrl]   (default: http://localhost:8080)

import { writeFileSync } from "node:fs";

const baseUrl = process.argv[2] ?? "http://localhost:8080";

const DEMO_SERVICES = [
  {
    profile: "laboratory",
    name: "Laboratory Integration Service",
    description: "Envia resultados laboratoriais simulados para um sistema clínico.",
    criticality: "HIGH",
    expectedIntervalMinutes: 1,
    toleranceMinutes: 1,
    intervalSeconds: 10,
  },
  {
    profile: "admissions",
    name: "Patient Admission Service",
    description: "Envia eventos de admissão e atualização de episódios.",
    criticality: "MEDIUM",
    expectedIntervalMinutes: 1,
    toleranceMinutes: 2,
    intervalSeconds: 15,
  },
  {
    profile: "notifications",
    name: "Notification Service",
    description: "Envia notificações internas e confirmações de entrega.",
    criticality: "LOW",
    expectedIntervalMinutes: 1,
    toleranceMinutes: 1,
    intervalSeconds: 8,
  },
];

async function api(path, options = {}) {
  const res = await fetch(`${baseUrl}${path}`, {
    ...options,
    headers: { "Content-Type": "application/json", ...options.headers },
  });
  if (!res.ok && res.status !== 409) {
    throw new Error(`${options.method ?? "GET"} ${path} -> ${res.status}: ${await res.text()}`);
  }
  return res;
}

async function main() {
  const services = [];

  const existing = await (await api("/api/services")).json();

  for (const demo of DEMO_SERVICES) {
    const found = existing.find((s) => s.name === demo.name);
    let serviceId, apiKey;

    if (found) {
      serviceId = found.id;
      const key = await (await api(`/api/services/${serviceId}/api-key`, { method: "POST" })).json();
      apiKey = key.apiKey;
      console.log(`• ${demo.name} já existia — API key regenerada`);
    } else {
      const res = await api("/api/services", {
        method: "POST",
        body: JSON.stringify({
          name: demo.name,
          description: demo.description,
          environment: "SIMULATION",
          criticality: demo.criticality,
          expectedIntervalMinutes: demo.expectedIntervalMinutes,
          toleranceMinutes: demo.toleranceMinutes,
        }),
      });
      const created = await res.json();
      serviceId = created.service.id;
      apiKey = created.apiKey.apiKey;
      console.log(`• ${demo.name} criado`);
    }

    services.push({
      profile: demo.profile,
      name: demo.name,
      serviceId,
      apiKey,
      intervalSeconds: demo.intervalSeconds,
      scenario: "normal",
      levelWeights: { INFO: 0.9, WARN: 0.08, ERROR: 0.02 },
    });
  }

  const config = { baseUrl, services };
  writeFileSync(new URL("./config.json", import.meta.url), JSON.stringify(config, null, 2));
  console.log(`\nconfig.json escrito com ${services.length} serviços (contém API keys — não fazer commit).`);
  console.log("Arrancar o simulador: npm start");
}

main().catch((err) => {
  console.error("Erro no setup:", err.message);
  process.exit(1);
});
