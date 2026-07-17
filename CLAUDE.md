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
- [x] **M6 Alertas** — `AlertManager` implementa `RuleTriggerHandler` (RuleEngine chama todos os handlers registados); dedup: disparos com alerta não-resolvido da mesma regra anexam TRIGGER_REPEATED à timeline em vez de criar novo; ciclo OPEN→ACKNOWLEDGED→RESOLVED (409 em transições inválidas); `/api/alerts` + detalhe com timeline e logs associados na UI; overview conta alertas ativos
- [x] **M7 Automações** — `AutomationExecutor` (@Async, consome `AlertCreatedEvent` publicado pelo AlertManager após commit): webhook por automação (config JSONB: url/method/headers/payloadTemplate com placeholders {{title}} etc.), 3 tentativas com backoff 2s, resultado em `action_execution`; **HTTP/1.1 forçado — HTTP/2 dá timeout com o n8n**; `/api/automations` CRUD + `/test` síncrono; UI: página Automações (criar/testar/ativar) + secção "Automações executadas" no detalhe do alerta
- [x] **M8 IA** — interface `LlmProvider` + `AnthropicProvider` (SDK oficial `anthropic-java`, structured outputs garantem o JSON das 4 secções, adaptive thinking, timeout 90s); `AIAnalyzer` junta alerta+regra+serviço+logs (máx. 20, payloads truncados a 1500 chars, campos sensíveis redigidos via `LLM_REDACTED_FIELDS`), persiste em `ai_analysis` (SUCCESS/FAILED com erro guardado); `GET/POST /api/alerts/{id}/analyses`; sem `LLM_API_KEY` → 503 com mensagem clara; UI: secção "Análise com IA" no detalhe do alerta (botão, 4 secções, aviso de sugestão); modelo por defeito `claude-sonnet-5` (env `LLM_MODEL`); **análise automática**: ação de automação `AI_ANALYSIS` (além de `WEBHOOK`) pede a análise ao criar o alerta (`AIAnalyzer.analyzeAutomatically`, ator SYSTEM na auditoria); o `/test` de uma automação IA usa o alerta não-resolvido mais recente da regra
- [x] **M9 Config/auditoria** — `AuditTrail` (@Component; `user(...)`/`system(...)`, falha-segura: nunca parte a operação principal, nunca regista segredos) chamado em serviços/regras/alertas/automações/análises IA; disparos repetidos de alertas não são auditados (já ficam na timeline); tabela `audit_entry` (V8) com actor USER|SYSTEM e details JSONB; `GET /api/audit` (filtro entityType + paginação, formato igual a /api/logs); `GET /api/settings` (estado LLM/n8n sem segredos; `hop.n8n.webhook-base-url` de N8N_WEBHOOK_BASE_URL); UI: página Configuração com cartões de integração + histórico de auditoria filtrável

Extensões pós-MVP (lista de tarefas acordada com o utilizador; fazer uma de cada vez):
- [x] **E1 Análise IA automática** — ação de automação `AI_ANALYSIS` (detalhes anotados no M8)
- [x] **E12 Multi-canal de notificação** — novos tipos de ação `EMAIL` (SMTP via spring-boot-starter-mail; envs `SMTP_HOST/PORT/USERNAME/PASSWORD/FROM`, sem host o canal falha com mensagem clara; config JSONB {to, subjectTemplate}) e `TEAMS` (incoming webhook, MessageCard com facts serviço/severidade/estado, themeColor por severidade; reutiliza o envio HTTP com 3 tentativas+backoff extraído para `sendWithRetries`); `/api/settings` expõe estado do email; UI: seletor de 4 tipos no formulário, badges por tipo, cartão "Email (SMTP)" na Configuração
- [x] **E11 Controlo do simulador pela UI** — `SimulatorController` (`/api/simulator`): o simulador faz `POST /status` a cada ~5s (reporta perfis+cenários, recebe cenários pendentes pedidos na UI; pendente é limpo quando o simulador reporta o cenário aplicado); `GET /api/simulator` (estado, ligado = reporte há <15s); `PUT /scenarios/{profile}` (validação de cenário, 404 para perfil não reportado; auditado como SIMULATOR_SCENARIO_CHANGED); estado em memória (ferramenta de demo, fora do domínio); simulador: cenários mutáveis em runtime (silence continua o loop sem enviar) + loop `syncWithPlatform`; UI: cartão "Simulador" na Configuração (ligado/desligado, seletor de cenário por perfil, "a aplicar…" enquanto pendente)
- [x] **E10 Gráficos no dashboard** — `GET /api/stats/logs?hours=N` (JdbcTemplate, GROUP BY date_trunc; horas vazias preenchidas a zero): volume por hora com erros/avisos/restantes, top 6 mensagens de erro, distribuição por serviço; UI: componente `LogCharts` (SVG próprio, sem dependências — barras empilhadas com folgas de 2px, tooltip por hora, legenda, grelha recessiva; listas de barras rotuladas para top erros e serviços); paleta validada para CVD/contraste em claro+escuro (#ef4444/#d97706/#3b82f6); atualiza por SSE (tópico logs)
- [x] **E9 Deteção estatística (z-score)** — novo tipo de regra `ANOMALY`: job agendado (`hop.rules.anomaly-check-seconds`, 60s) conta logs ERROR/FATAL na janela atual (`windowMinutes`) vs. 12 janelas anteriores (1 query de timestamps, buckets em memória); dispara quando z = (atual−média)/max(desvio,1) ≥ `threshold` (default 3) e atual ≥ 3 eventos (`AnomalyDetector`, classe pura testada); sem condições; validação exige windowMinutes; wizard com a opção "Anomalia estatística (z-score)" e o RuleSuggester conhece o tipo
- [x] **E8 Insights por serviço** — `GET/POST /api/services/{id}/analyses`: análise IA do estado operacional recente (últimos 20 logs) sem depender de um alerta; migração V9 (`ai_analysis.alert_id` passou a nullable + coluna `service_id` preenchida retroativamente); `AIAnalyzer.analyzeService` com system prompt próprio e helper `complete()` comum aos dois caminhos; UI: componente partilhado `AiAnalysisPanel` (usado no detalhe do alerta e do serviço)
- [x] **E7 Regras por linguagem natural** — `POST /api/rules/suggest` ({prompt}) → `RuleSuggester` chama o LLM (structured outputs com o schema `RuleSuggestionResult`: serviceName/type/severity/janela/threshold/cooldown/condições/explicação); o LLM só pode escolher serviços da lista fornecida (nome resolvido para id com match exato→parcial; null se ambíguo); **a IA só sugere — nada é gravado sem confirmação humana** (pré-preenche o wizard); auditado como RULE_SUGGESTED; `LlmProvider` generalizado para `<T> generate(system, user, Class<T>)` (o AIAnalyzer usa o mesmo método); sem key → 503; UI: caixa "Descrever em linguagem natural" no topo do wizard
- [x] **E6 Rate limiting + keyset** — ingestão limitada por serviço (`IngestRateLimiter`, token bucket em memória, `INGEST_RATE_LIMIT_PER_MINUTE` default 300, 0 = off; 429 com `Retry-After` e código `RATE_LIMITED`, verificado após a autenticação da key); `GET /api/logs` aceita `cursor` (`<receivedAt ISO>_<uuid>`) para paginação keyset sem COUNT/OFFSET (ordenação estável receivedAt desc, id desc; resposta com `nextCursor`); 1.ª página continua a devolver totais; UI do Log Explorer navega com pilha de cursores (Anterior/Seguinte)
- [x] **E5 Docker completo** — `backend/Dockerfile` (multi-stage Maven→JRE 21) e `frontend/Dockerfile` (Next `output: "standalone"`, Node 22-alpine); serviços `backend`/`frontend` no compose sob o perfil `full` (`docker compose --profile full up -d --build`); `docker compose up -d` continua a arrancar só Postgres+n8n para dev; dentro do compose o backend fala com o n8n via `http://n8n:5678` (webhooks de automações devem usar esse host quando o backend corre em Docker)
- [x] **E4 Retenção de logs** — `LogRetentionJob` (@Scheduled de hora a hora, arranque +60s): apaga logs com mais de `hop.retention.log-days` dias (env `LOG_RETENTION_DAYS`, default 30, 0 = desativado), **exceto os associados a alertas** (evidência preservada; delete JPQL com `not in AlertLogLink`); regista `LOGS_PURGED` (SYSTEM) na auditoria com contagem+cutoff; estado exposto em `/api/settings` e cartão na página Configuração
- [x] **E3 SSE em tempo real** — `SseHub` (@Component) + `GET /api/events/stream`; eventos são só sinais "algo mudou" por tópico (logs/alerts/executions/analyses/services/rules — nunca transportam dados), publicados nos pontos de mutação; heartbeat 25s mantém ligações vivas; frontend: hook `useLiveRefresh(topics, onChange)` (debounce 400ms, fallback para polling 5s se o SSE falhar, poll de segurança 60s quando ligado; carga inicial fica na página para filtros refazerem fetch) — substituiu o setInterval em dashboard/logs/alertas/detalhe/serviços/regras
- [x] **E2 Métricas de avaliação** — `GET /api/metrics?days=N` (0/omisso = tudo): deteção (1.º log associado → openedAt), notificação (openedAt → 1.ª action_execution), MTTA, MTTR — cada um com count/avg/p50/p95/max em ms — + contagens (alertas por estado, logs, ações, análises IA); secção "Métricas de avaliação" no dashboard com seletor de janela (24h/7d/30d/tudo); sem estado próprio, tudo derivado dos timestamps existentes

Modelo de dados alvo (doc de visão §7): Service, ServiceApiKey, LogEvent, MonitorRule, RuleCondition, Alert, AlertEvent, AlertLogLink, Automation, AutomationAction, ActionExecution, AIAnalysis, AuditEntry.

## n8n (dev local)

- Conta de dono criada: `admin@hop.local` / `HopDev2026!` (só dev local, UI em http://localhost:5678).
- Workflow "Alerta Hospitalar" (id `ZgYYhFbKPOHRVEKr`), ativo, com nó Webhook POST em `http://localhost:5678/webhook/alerta-hospitalar` — é este URL que as automações usam. Execuções visíveis na UI do n8n em "Executions".

## Decisões tomadas

- Monorepo; Spring Boot 3.5 (não 4.x — springdoc ainda não suporta); Next.js 16; PostgreSQL 16.
- Frontend atualiza por **polling**; SSE é evolução.
- LLM: Anthropic Claude atrás de interface abstrata; `LLM_API_KEY` em env var.
- Simulador: script standalone configurado por ficheiro (UI opcional depois).
- UI em **português**; código/identificadores em inglês.
- Entidade `Service` chama-se `MonitoredService` em Java (evita colisão com `@Service` do Spring); tabela é `service`.
- Testes de integração correm contra o Postgres de dev e podem deixar registos `svc-*` — limpar ou migrar para BD de teste dedicada (melhoria futura); asserções que dependiam de estado limpo (ex.: `overviewCountsServices`) passaram a validar a forma, não contagens absolutas.
- **Passe de revisão (code-review xhigh) após as extensões E1–E12**: corrigidos, entre outros — `AuditTrail` em transação própria (`TransactionTemplate` REQUIRES_NEW, falha de auditoria nunca parte a operação); `SseHub` publica só após commit e despacha os `send` num executor próprio (não bloqueia ingestão/scheduler); tipo da ação persistido em `action_execution.action_type` (V10) para o histórico não perder o canal quando a automação é editada (EMAIL/TEAMS deixaram de aparecer como "Webhook"); `AnomalyDetector` exige linha de base real (≥3 janelas ativas) para não disparar em serviços novos; pesquisa de logs escapa `%`/`_`; `MetricsController` sem N+1 (janela em SQL + lotes); `useLiveRefresh` mantém o polling de recurso durante quedas de SSE; z-score do wizard arredondado a inteiro; `fetch` do simulador com timeout.
- Camada de negócio usa `@Component` (`ServiceManager`) em vez de `@Service` pelo mesmo motivo de colisão.

**Atualizar este ficheiro (estado dos módulos e decisões) no fim de cada módulo.**
