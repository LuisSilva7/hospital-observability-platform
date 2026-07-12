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
