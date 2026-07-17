# Hospital Observability Platform

Plataforma configurável de monitorização, alertas, automações e insights de IA para fluxos de dados em meio hospitalar. Protótipo desenvolvido no âmbito da dissertação de mestrado *Monitorização Inteligente de Fluxos e Processos de Dados em Meio Hospitalar* (Universidade do Minho).

Serviços hospitalares simulados enviam logs JSON por HTTP; a plataforma armazena-os, avalia regras definidas pelo utilizador e desencadeia alertas, webhooks (n8n) e análises por IA.

## Estrutura

```
backend/     Spring Boot 3.5 (Java 21) — API REST, ingestão, regras, alertas
frontend/    Next.js 16 + TypeScript — interface web
simulator/   Node — simulador de serviços hospitalares (envia logs sintéticos)
docs/        Documento de visão e relatório intermédio da dissertação
docker-compose.yml   PostgreSQL 16 + n8n
```

## Pré-requisitos (macOS)

- Java 21 (`brew install --cask temurin@21`)
- Node.js 20+ (`brew install node`)
- Docker Desktop

## Arranque

```bash
# 1. Infraestrutura (PostgreSQL :5432 e n8n :5678)
docker compose up -d

# 2. Backend (:8080)
cd backend
./mvnw spring-boot:run

# 3. Frontend (:3000) — noutro terminal
cd frontend
npm install   # primeira vez
npm run dev
```

Abrir http://localhost:3000 — o dashboard mostra o estado da plataforma e da base de dados.

### Alternativa: tudo em Docker (um comando)

```bash
docker compose --profile full up -d --build
```

Arranca PostgreSQL, n8n, backend e frontend em containers. Notas:
- O primeiro build demora alguns minutos (Maven + Next.js).
- Com o backend em Docker, os webhooks das automações devem usar `http://n8n:5678/...` (o backend fala com o n8n pela rede interna do compose, não por `localhost`).
- Para voltar ao modo dev: `docker compose --profile full down` e arrancar backend/frontend localmente como acima.

| URL | O quê |
|---|---|
| http://localhost:3000 | Interface web |
| http://localhost:8080/api/health | Health check (API + BD) |
| http://localhost:8080/swagger-ui/index.html | Documentação OpenAPI |
| http://localhost:5678 | n8n |

## Configuração

Copiar `.env.example` e ajustar se necessário. Em desenvolvimento os defaults funcionam sem configuração (BD `hop`/`hop`/`hop`). Segredos (API keys de LLM, etc.) apenas por variáveis de ambiente — nunca no repositório.

## Testes

```bash
cd backend && ./mvnw test   # requer o PostgreSQL do docker compose a correr
```

## Estado

- ✅ Módulo 0 — Fundação (infra, health check ponta a ponta, navegação)
- ✅ Módulo 1 — Gestão de serviços monitorizados (CRUD, API keys com hash, endpoint de ingestão copiável)
- ✅ Módulo 2 — Ingestão e normalização de logs (X-API-Key, payload JSONB preservado)
- ✅ Módulo 3 — Simulador de serviços (3 perfis, cenários normal/error-spike/latency/silence — ver `simulator/README.md`)
- ✅ Módulo 4 — Log Explorer (filtros, pesquisa, paginação, payload original) e dashboard com estado dos serviços
- ✅ Módulo 5 — Regras configuráveis (EVENT_MATCH, NO_ACTIVITY, COUNT_THRESHOLD) com wizard, cooldown e histórico de disparos
- ✅ Módulo 6 — Alertas com ciclo de vida (OPEN → ACKNOWLEDGED → RESOLVED), timeline, deduplicação e logs associados
- ✅ Módulo 7 — Automações: webhooks (n8n) executados na criação de alertas, com retries e histórico de execuções
- ✅ Módulo 8 — Insights de IA: "Analisar com IA" no detalhe do alerta (resumo, causa provável, evidências, recomendações) via API da Anthropic; requer `LLM_API_KEY`
- ✅ Módulo 9 — Configuração e auditoria: página com estado das integrações (LLM/n8n, sem expor segredos) e histórico de quem/que processo criou ou alterou serviços, regras, alertas e automações

### Extensões pós-MVP

- ✅ Análise IA automática ao criar alerta (ação de automação `AI_ANALYSIS`)
- ✅ Métricas de avaliação (`/api/metrics`, deteção/notificação/MTTA/MTTR) no dashboard
- ✅ Atualização em tempo real via SSE (substituiu o polling)
- ✅ Retenção de logs configurável, preservando evidência de alertas
- ✅ Docker completo (`docker compose --profile full up -d --build`)
- ✅ Rate limiting na ingestão e paginação por keyset em `/api/logs`
- ✅ Criação de regras por linguagem natural (assistida por IA, revisão humana obrigatória)
- ✅ Insights de IA por serviço, sem depender de um alerta
- ✅ Deteção estatística (z-score da taxa de erros) como novo tipo de regra
- ✅ Gráficos no dashboard (volume por hora, top erros, por serviço)
- ✅ Controlo do simulador pela UI (mudança de cenário em tempo real)
- ✅ Notificações multi-canal (webhook, email, Microsoft Teams, além do n8n)

Roadmap completo em [CLAUDE.md](CLAUDE.md) e em `docs/documento-visao.txt`.
