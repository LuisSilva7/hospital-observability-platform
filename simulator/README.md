# Simulador de serviços hospitalares

Script standalone (Node 20+, sem dependências) que faz o papel dos sistemas
hospitalares: envia logs JSON sintéticos para a plataforma por HTTP com API key.

## Setup (uma vez, com o backend a correr)

```bash
node setup.js
```

Regista os 3 serviços de demonstração na plataforma (Laboratory Integration,
Patient Admission, Notification) e escreve `config.json` com os IDs e API keys.
Se os serviços já existirem, regenera as chaves.

⚠️ `config.json` contém API keys — está no `.gitignore`, não fazer commit.

## Correr

```bash
node index.js                                    # operação normal
node index.js --scenario laboratory=error-spike  # pico de erros no laboratório
node index.js --scenario notifications=silence   # notificações ficam em silêncio
node index.js --scenario laboratory=latency      # respostas lentas do EHR
```

Cenários por perfil (`laboratory`, `admissions`, `notifications`):

| Cenário | Comportamento |
|---|---|
| `normal` | maioritariamente INFO, erros raros (pesos do config) |
| `error-spike` | ~70% ERROR (ex.: `EHR_TIMEOUT`) |
| `latency` | logs WARN com `responseTimeMs` 3000–5200 |
| `silence` | o serviço não envia nada |

Parar com Ctrl+C. Vários cenários podem ser combinados no mesmo comando.

## Controlo em tempo real pela UI

Com o simulador a correr, os cenários podem ser mudados sem reiniciar, na
página **Configuração → Simulador** da plataforma:

- o simulador reporta o seu estado por `POST /api/simulator/status` a cada ~5s
  e recebe na resposta os cenários pedidos na UI (aplica-os no ciclo seguinte);
- `silence` passou a ser dinâmico: o loop continua vivo sem enviar logs, e o
  serviço "volta a falar" quando o cenário mudar de novo;
- se a plataforma estiver em baixo, o simulador mantém os cenários atuais e
  continua a tentar sincronizar.
