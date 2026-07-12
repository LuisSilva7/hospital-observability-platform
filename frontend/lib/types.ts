export type Environment = "SIMULATION" | "DEVELOPMENT" | "STAGING" | "PRODUCTION";
export type Criticality = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

export type Service = {
  id: string;
  name: string;
  description: string | null;
  environment: Environment;
  criticality: Criticality;
  active: boolean;
  expectedIntervalMinutes: number | null;
  toleranceMinutes: number | null;
  lastSeenAt: string | null;
  createdAt: string;
  status: string;
  ingestEndpoint: string;
  apiKeyPrefix: string | null;
};

export type ApiKey = {
  apiKey: string;
  prefix: string;
  createdAt: string;
};

export type ServiceCreated = {
  service: Service;
  apiKey: ApiKey;
};

export const ENVIRONMENT_LABELS: Record<Environment, string> = {
  SIMULATION: "Simulação",
  DEVELOPMENT: "Desenvolvimento",
  STAGING: "Staging",
  PRODUCTION: "Produção",
};

export const CRITICALITY_LABELS: Record<Criticality, string> = {
  LOW: "Baixa",
  MEDIUM: "Média",
  HIGH: "Alta",
  CRITICAL: "Crítica",
};
