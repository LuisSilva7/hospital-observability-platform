# Hospital Observability Platform

Protótipo da dissertação de mestrado (UMinho, MEGSI) "Monitorização Inteligente de Fluxos e Processos de Dados em Meio Hospitalar" — Luís Silva, orientação Prof. Tiago Guimarães. Prazo: relatório final ~julho 2026.

**Documentos de referência (ler quando faltar contexto):**
- `docs/documento-visao.txt` — visão, escopo do MVP, 10 módulos, modelo de dados, contratos de API, cenário de demonstração, plano de 7 fases. **É a fonte de verdade do que construir.**
- `docs/tgv-relatorio-intermedio.txt` — enquadramento académico, objetivos de investigação, método DSR, métricas de avaliação.

## O que é a plataforma

Plataforma local e configurável de observabilidade: serviços hospitalares **simulados** enviam logs JSON por HTTP (com API key); o utilizador define regras (via UI, sem código) que geram alertas, disparam webhooks/n8n e análises por LLM. Foco técnico-operacional — **sem dados clínicos reais nem lógica específica de um serviço no backend**.

Ciclo a demonstrar: serviço simulado → POST log → regra → alerta → webhook n8n → mensagem → insight de IA.

## Stack e estrutura

| Pasta | Tech | Notas |
|---|---|---|
| `backend/` | Java 21, Spring Boot 3.5, Maven | camadas controller/service/repository/domain; Flyway em `src/main/resources/db/migration`; OpenAPI (springdoc) em `/swagger-ui` |
| `frontend/` | Next.js 16, TypeScript, Tailwind 4, App Router | **Atenção: Next 16 tem breaking changes — ver `frontend/AGENTS.md`**; helper API em `lib/api.ts`; polling (SSE só mais tarde) |
| `docker-compose.yml` | PostgreSQL 16 (:5432) + n8n (:5678) | dados em volumes |
| `simulator/` | Node 20+ standalone, sem deps | `setup.js` (regista serviços, escreve config.json) + `index.js` (loop de envio); ver `simulator/README.md` |

## Comandos (macOS)

```bash
docker compose up -d                       # Postgres + n8n
cd backend && ./mvnw spring-boot:run       # backend em :8080
cd frontend && npm run dev                 # frontend em :3000
cd backend && ./mvnw test                  # testes (precisam do Postgres a correr)
```

Health: `GET http://localhost:8080/api/health`. Config por env vars — ver `.env.example`. Defaults dev: db/user/pass `hop`.

## Regras de trabalho (do documento de visão, secção 13)

1. Implementar **por módulos, pela ordem** — não avançar sem o atual estar funcional (demo + testes + README).
2. Nada fora do MVP sem identificar como opcional. Excluído do MVP: multi-tenancy, editor visual de workflows, dashboards drag-and-drop, ML próprio, dados clínicos reais.
3. Payload de logs sempre preservado em **JSONB**; normalizar só timestamp/level/message/eventType.
4. API keys guardadas **com hash** (mostrar em claro só na criação); segredos só em env vars, nunca na BD nem no repo.
5. Regras com **cooldown e deduplicação**; IA é assistiva (sugere, não executa ações críticas) e só referencia os logs que lhe foram enviados.
6. Backend genérico — sem nomes/regras fixas de laboratório/consultas/faturação.
7. No fim de cada módulo: ficheiros criados, como executar, como testar, próximos passos.

## Estado dos módulos

- [x] **M0 Fundação** — compose, Spring Boot + Flyway + health + CORS + erros globais + swagger, Next.js com sidebar e páginas placeholder, health card ponta a ponta
- [x] **M1 Serviços** — CRUD Service (`/api/services`), API keys SHA-256 com prefixo visível, regeneração, ativar/desativar, UI lista/criar/detalhe com key mostrada 1x e exemplo curl
- [x] **M2 Ingestão** — `POST /api/v1/ingest/{serviceId}/logs` com X-API-Key (401 se inválida/em falta/de outro serviço/serviço inativo), payload JSONB preservado + campos normalizados (`LogNormalizer`: timestamp/level/message/eventType), atualiza lastSeenAt e lastUsedAt
- [x] **M3 Simulador** — `simulator/` Node sem deps: `node setup.js` regista os 3 serviços demo e escreve `config.json` (gitignored, tem keys); `node index.js --scenario <perfil>=<normal|error-spike|latency|silence>`; perfis: laboratory/admissions/notifications
- [x] **M4 Exploração** — `GET /api/logs` (filtros serviceId/level/text/from/to + paginação; text pesquisa message e payload via `@Formula payload::text`), `GET /api/overview`; estado derivado (`ServiceManager.deriveStatus`: INACTIVE/UNKNOWN/SILENT/HEALTHY); UI: Log Explorer com modal de payload + dashboard com cartões e lista de estado
- [x] **M5 Regras** — `RuleEngine` (EVENT_MATCH/COUNT_THRESHOLD avaliadas na ingestão; NO_ACTIVITY via `@Scheduled` 30s) + `ConditionMatcher` (EQUALS/NOT_EQUALS/CONTAINS/GT/LT, fieldPath aninhado com "."); cooldown por regra; disparos registados em `rule_evaluation`; interface `RuleTriggerHandler` é o hook para M6/M7; CRUD `/api/rules` + wizard 4 passos na UI + detalhe com histórico
- [ ] **M6 Alertas** — OPEN/ACKNOWLEDGED/RESOLVED, timeline, logs associados
- [ ] **M7 Automações** — webhook + n8n, executor assíncrono, retries, histórico
- [ ] **M8 IA** — "Analisar com IA": resumo, causa provável, evidências, recomendações (interface abstrata de LLM; provider Anthropic)
- [ ] **M9 Config/auditoria** — settings LLM/n8n, AuditEntry

Modelo de dados alvo (doc de visão §7): Service, ServiceApiKey, LogEvent, MonitorRule, RuleCondition, Alert, AlertEvent, AlertLogLink, Automation, AutomationAction, ActionExecution, AIAnalysis, AuditEntry.

## Decisões tomadas

- Monorepo; Spring Boot 3.5 (não 4.x — springdoc ainda não suporta); Next.js 16; PostgreSQL 16.
- Frontend atualiza por **polling**; SSE é evolução.
- LLM: Anthropic Claude atrás de interface abstrata; `LLM_API_KEY` em env var.
- Simulador: script standalone configurado por ficheiro (UI opcional depois).
- UI em **português**; código/identificadores em inglês.
- Entidade `Service` chama-se `MonitoredService` em Java (evita colisão com `@Service` do Spring); tabela é `service`.
- Testes de integração correm contra o Postgres de dev e podem deixar registos `svc-*` — limpar ou migrar para BD de teste dedicada (melhoria futura).
- Camada de negócio usa `@Component` (`ServiceManager`) em vez de `@Service` pelo mesmo motivo de colisão.

**Atualizar este ficheiro (estado dos módulos e decisões) no fim de cada módulo.**
