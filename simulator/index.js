// Simulador de serviços hospitalares — envia logs JSON sintéticos para a
// plataforma por HTTP, como fariam os sistemas reais.
//
// Uso:
//   node index.js                                    # cenários do config.json
//   node index.js --scenario laboratory=error-spike  # override por perfil
//   node index.js --scenario notifications=silence --scenario laboratory=latency
//
// Cenários: normal | error-spike | silence | latency

import { readFileSync } from "node:fs";

const config = JSON.parse(readFileSync(new URL("./config.json", import.meta.url), "utf8"));

// --- overrides de cenário por CLI ---
const overrides = {};
for (let i = 2; i < process.argv.length; i++) {
  if (process.argv[i] === "--scenario" && process.argv[i + 1]?.includes("=")) {
    const [profile, scenario] = process.argv[++i].split("=");
    overrides[profile] = scenario;
  }
}

const rand = (min, max) => Math.round(min + Math.random() * (max - min));
const pick = (arr) => arr[Math.floor(Math.random() * arr.length)];

function pickLevel(weights) {
  const r = Math.random();
  let acc = 0;
  for (const [level, w] of Object.entries(weights)) {
    acc += w;
    if (r <= acc) return level;
  }
  return "INFO";
}

// --- geradores de payload por perfil (dados 100% sintéticos) ---
const PROFILES = {
  laboratory: (level, { latency }) => {
    const responseTimeMs = latency ? rand(3000, 5200) : rand(120, 900);
    const base = {
      eventType: level === "ERROR" ? "lab_result_failed" : "lab_result_sent",
      testType: pick(["hemogram", "biochemistry", "microbiology", "coagulation"]),
      responseTimeMs,
      correlationId: `LAB-${rand(10000, 99999)}`,
    };
    if (level === "ERROR") {
      return { ...base, message: "Failed to send laboratory result", errorCode: "EHR_TIMEOUT" };
    }
    if (level === "WARN" || latency) {
      return { ...base, level: latency ? "WARN" : level, message: `Slow response from EHR (${responseTimeMs}ms)` };
    }
    return { ...base, message: "Laboratory result sent to EHR" };
  },

  admissions: (level) => {
    const base = {
      eventType: pick(["patient_admitted", "episode_updated", "patient_discharged"]),
      department: pick(["emergency", "cardiology", "orthopedics", "pediatrics"]),
      episodeId: `EP-${rand(100000, 999999)}`,
    };
    if (level === "ERROR") {
      return { ...base, eventType: "admission_failed", message: "Episode update rejected by HIS", errorCode: pick(["HIS_VALIDATION", "HIS_UNAVAILABLE"]) };
    }
    if (level === "WARN") {
      return { ...base, message: "Episode update retried", retryCount: rand(1, 3) };
    }
    return { ...base, message: "Episode event processed" };
  },

  notifications: (level) => {
    const base = {
      eventType: "notification_dispatched",
      channel: pick(["email", "sms", "internal"]),
      recipientRole: pick(["physician", "nurse", "admin"]),
      deliveryMs: rand(50, 400),
    };
    if (level === "ERROR") {
      return { ...base, eventType: "notification_failed", message: "Delivery failed", errorCode: "SMTP_ERROR" };
    }
    if (level === "WARN") {
      return { ...base, message: "Delivery delayed", deliveryMs: rand(2000, 8000) };
    }
    return { ...base, message: "Notification delivered" };
  },
};

// --- ajuste de comportamento por cenário ---
function effectiveWeights(scenario, weights) {
  switch (scenario) {
    case "error-spike": return { INFO: 0.15, WARN: 0.15, ERROR: 0.7 };
    case "latency": return { INFO: 0.3, WARN: 0.65, ERROR: 0.05 };
    default: return weights;
  }
}

async function sendLog(service, scenario) {
  const weights = effectiveWeights(scenario, service.levelWeights);
  const level = pickLevel(weights);
  const generator = PROFILES[service.profile] ?? PROFILES.notifications;
  const payload = {
    timestamp: new Date().toISOString(),
    level,
    ...generator(level, { latency: scenario === "latency" }),
  };

  try {
    const res = await fetch(`${config.baseUrl}/api/v1/ingest/${service.serviceId}/logs`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-API-Key": service.apiKey },
      body: JSON.stringify(payload),
    });
    const status = res.ok ? "✓" : `HTTP ${res.status}`;
    console.log(`[${new Date().toLocaleTimeString()}] ${service.profile.padEnd(13)} ${level.padEnd(5)} ${payload.eventType.padEnd(22)} ${status}`);
  } catch (err) {
    console.log(`[${new Date().toLocaleTimeString()}] ${service.profile.padEnd(13)} ERRO DE REDE: ${err.message}`);
  }
}

function startService(service) {
  const scenario = overrides[service.profile] ?? service.scenario ?? "normal";
  if (scenario === "silence") {
    console.log(`● ${service.name} — cenário SILENCE: não envia logs.`);
    return;
  }
  console.log(`● ${service.name} — cenário ${scenario}, a cada ~${service.intervalSeconds}s`);
  const tick = () => {
    sendLog(service, scenario);
    // jitter de ±20% para não parecer um metrónomo
    const next = service.intervalSeconds * 1000 * (0.8 + Math.random() * 0.4);
    setTimeout(tick, next);
  };
  setTimeout(tick, Math.random() * 2000);
}

console.log(`Simulador HOP → ${config.baseUrl}  (Ctrl+C para parar)\n`);
config.services.forEach(startService);
