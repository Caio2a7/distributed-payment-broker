# distributed-payment-broker — ROADMAP

> Sistema distribuído de pagamentos baseado no PIX. Construído com Java + Spring ao longo da disciplina de Programação Distribuída.

---

## Serviços do sistema

| Serviço | Responsabilidade |
|---|---|
| `api-gateway` | Ponto único de entrada, JWT, rate limiting |
| `account-service` | Contas, usuários, saldo, débito/crédito |
| `key-service` | Registro e resolução de chaves PIX (equivalente ao DICT) |
| `payment-orchestrator` | Orquestra o fluxo Saga de cada transação |
| `ledger-service` | Log imutável de todas as operações financeiras |
| `notification-service` | Eventos de transação para o usuário |
| `fraud-service` | Detecção de anomalias + Spring AI |
| `config-server` | Configurações centralizadas por ambiente |
| `discovery-service` | Eureka — registro e descoberta de serviços |

---

## Fase 1 — Fundação & Arquitetura
_Ementa: Apresentação, Conceitos de SD, Sockets_

### Setup do Monorepo
- [ ] Criar estrutura de módulos Maven (parent pom + módulo por serviço)
- [ ] Setup `docker-compose.yml` base: PostgreSQL, Redis, RabbitMQ

### Documentação de Arquitetura
- [ ] **ADR-001:** Spring Boot como framework principal
- [ ] **ADR-002:** Database per Service — banco isolado por serviço
- [ ] **ADR-003:** RabbitMQ para comunicação assíncrona entre serviços
- [ ] **ADR-004:** Saga Orquestrada (payment-orchestrator como coordenador central)
- [ ] Sequence diagram: fluxo completo de uma transação PIX (ponta a ponta)
- [ ] Definir bounded context e responsabilidade de cada serviço
- [ ] Modelagem de dados inicial por serviço

### Experimentos de Socket (aulas práticas — fora do projeto principal)
- [ ] **UDP:** broadcast de heartbeat entre nodes simulando health check de peers
- [ ] **TCP:** protocolo binário raw de mensagem financeira (header + payload)
- [ ] **HTTP:** servidor HTTP raw de consulta de saldo sem nenhum framework

---

## Fase 2 — Serviços Core & gRPC
_Ementa: Cloud, AWS Básico, Middleware, gRPC Conceitos, Unary, Stream_

### Infraestrutura Cloud (AWS Básico)
- [ ] Provisionar EC2 para cada serviço
- [ ] Provisionar RDS PostgreSQL (um por serviço)
- [ ] Provisionar ElastiCache (Redis)
- [ ] Configurar profiles Spring: `dev`, `prod`

### Proto & gRPC
- [ ] Configurar `protobuf-maven-plugin` no parent pom
- [ ] `account.proto` — Unary: buscar conta por ID e por número
- [ ] `key.proto` — Unary: resolver chave PIX → dados da conta vinculada
- [ ] `transaction.proto` — Server Streaming: cliente recebe status em tempo real
- [ ] Gerar stubs e testar com `grpcurl`

### account-service
- [ ] Bootstrap: Spring Boot + JPA + PostgreSQL
- [ ] Entidades: `User`, `Account` (ISPB simulado, agência, número, tipo), `Balance`
- [ ] Lógica de débito com **optimistic locking** — prevenir race condition em saldo
- [ ] Lógica de crédito com optimistic locking
- [ ] gRPC server: buscar conta por ID, buscar por número de conta
- [ ] Validações de domínio: saldo insuficiente, conta inativa, limite diário excedido

### key-service (equivalente ao DICT do BACEN)
- [ ] Bootstrap: Spring Boot + JPA + Redis
- [ ] Entidade: `PixKey` — tipo (CPF, CNPJ, telefone, email, aleatória), valor, accountId, status
- [ ] Validação: unicidade de chave — uma chave não pode estar em duas contas
- [ ] Regra PIX real: máximo 5 chaves por CPF
- [ ] gRPC Unary: resolver chave → retornar dados da conta vinculada
- [ ] Cache Redis: resultado de resolução com TTL de 5 minutos

### ledger-service
- [ ] Bootstrap: Spring Boot + JPA + PostgreSQL
- [ ] Entidade: `LedgerEntry` — **imutável, append-only** (zero UPDATE, zero DELETE)
- [ ] Hash de integridade por entrada (SHA-256 do payload)
- [ ] gRPC Unary: registrar entrada, buscar por `transactionId`

### notification-service
- [ ] Bootstrap: Spring Boot + RabbitMQ consumer
- [ ] Consumir filas: `payment.completed`, `payment.failed`, `payment.refunded`
- [ ] gRPC Server Streaming: cliente se conecta e recebe eventos em tempo real
- [ ] Log estruturado com detalhes completos da notificação

---

## Fase 3 — APIs REST & GraphQL
_Ementa: Spring Framework, APIs REST, Spring for GraphQL_

### payment-orchestrator — Saga Orquestrada (coração do sistema)
- [ ] Bootstrap: Spring Boot
- [ ] Implementar **Saga Pattern** em 6 steps com compensação:
  - [ ] **Step 1 — Validar:** checar limites, saldo suficiente, status das contas
  - [ ] **Step 2 — Resolver chave:** gRPC → key-service (com fallback de cache)
  - [ ] **Step 3 — Debitar pagador:** gRPC → account-service
  - [ ] **Step 4 — Creditar recebedor:** gRPC → account-service
  - [ ] **Step 5 — Registrar ledger:** gRPC → ledger-service
  - [ ] **Step 6 — Publicar evento:** RabbitMQ → notification-service
  - [ ] **Compensação Step 4:** se crédito falhar, reverter débito do Step 3
  - [ ] **Compensação Step 5:** se ledger falhar, reverter Steps 3 e 4
- [ ] **Idempotência:** Redis com chave idempotente válida por 24h — prevenir pagamento duplicado
- [ ] **Máquina de estados:** `CREATED → PROCESSING → COMPLETED | FAILED | REFUNDED`
- [ ] `POST /v1/payments` — iniciar pagamento
- [ ] `GET /v1/payments/{id}` — consultar status
- [ ] `POST /v1/payments/{id}/refund` — solicitar estorno

### account-service — REST Layer
- [ ] `POST /v1/accounts` — criar conta
- [ ] `GET /v1/accounts/{id}/balance` — saldo atual
- [ ] `GET /v1/accounts/{id}/statement?page=&size=` — extrato paginado

### key-service — REST Layer
- [ ] `POST /v1/keys` — registrar nova chave PIX
- [ ] `GET /v1/keys/{key}` — lookup de chave (pública)
- [ ] `DELETE /v1/keys/{key}` — remover chave
- [ ] `GET /v1/keys?accountId=` — listar todas as chaves de uma conta

### GraphQL
- [ ] Schema: `transactionHistory` — filtros por date range, tipo (`pix_in`, `pix_out`), status, valor mínimo/máximo
- [ ] Schema: `accountDashboard` — saldo + últimas N transações + chaves cadastradas + resumo mensal
- [ ] Resolver: `transactionHistory` paginado e ordenado
- [ ] Resolver: `accountDashboard` agregando dados de account-service + payment-orchestrator
- [ ] **Bônus:** GraphQL Subscription — receber evento de nova transação em tempo real

---

## Fase 4 — Cloud Native
_Ementa: Config Server, Secrets, Discovery, Gateway, Resiliência_

### config-server
- [ ] Bootstrap Spring Cloud Config Server
- [ ] Repositório Git de configurações (separado do monorepo)
- [ ] Externalizar: timeouts por serviço, limites de transação, retry policy, TTL de cache
- [ ] Profiles: `default`, `dev`, `staging`, `prod`
- [ ] `@RefreshScope` nos beans críticos — atualização sem restart

### Gerenciamento de Segredos
- [ ] Integrar **AWS Secrets Manager**
- [ ] Mover para secrets: credenciais de banco, senha RabbitMQ, JWT secret, API keys
- [ ] Remover toda credencial hardcoded ou em `application.properties` commitado
- [ ] Validar rotação de secret sem downtime de serviço

### discovery-service
- [ ] Bootstrap Eureka Server
- [ ] Registrar todos os serviços no Eureka
- [ ] Configurar health check via `/actuator/health` por serviço
- [ ] Subir múltiplas instâncias do `payment-orchestrator` e validar load balance

### api-gateway
- [ ] Bootstrap Spring Cloud Gateway
- [ ] Roteamento de todas as rotas via Eureka (sem URL hardcoded)
- [ ] Filtro global: validação de JWT antes de rotear qualquer request
- [ ] **Rate limiting por usuário:** Redis token bucket — 100 req/min geral
- [ ] **Rate limiting agressivo:** `POST /v1/payments` — 10 pagamentos/min por usuário
- [ ] Filtro: injetar `X-Correlation-Id` em todo request de entrada
- [ ] Filtro: logging estruturado de request e response

### Resiliência (Resilience4j)
- [ ] Circuit breaker: `payment-orchestrator → key-service`
- [ ] Circuit breaker: `payment-orchestrator → account-service` (**crítico — sem fallback, Saga compensa**)
- [ ] Circuit breaker: `payment-orchestrator → ledger-service`
- [ ] Retry com **backoff exponencial** em todas as chamadas gRPC
- [ ] Fallback `key-service`: retornar resolução cacheada do Redis se o serviço cair
- [ ] Timeout configurável por operação — definido no config-server
- [ ] **Bulkhead:** isolar thread pool das operações de débito e crédito
- [ ] **Teste de resiliência:** derrubar account-service no meio de um pagamento e verificar compensação Saga executando corretamente

---

## Fase 5 — AWS Avançado & Serverless
_Ementa: AWS Avançado I e II, Serverless Computing_

### AWS Avançado
- [ ] ECS: containerizar e deployar todos os serviços
- [ ] Application Load Balancer na frente do api-gateway
- [ ] SQS como fila de eventos (substituir ou complementar RabbitMQ)
- [ ] S3: armazenar extratos e relatórios gerados
- [ ] IAM: role por serviço com princípio do mínimo privilégio

### Lambda — Serverless
- [ ] Lambda: gerar extrato mensal em PDF → salvar em S3 → publicar evento de notificação
- [ ] Lambda: **reconciliação noturna** — cruzar ledger vs saldos e reportar inconsistências
- [ ] Lambda: calcular score de risco de fraude em batch para transações do dia
- [ ] Lambda: expirar chaves PIX inativas há mais de 60 dias (regra BACEN real)
- [ ] EventBridge: agendar todos os Lambdas com cron

---

## Fase 6 — Observabilidade
_Ementa: Observabilidade_

- [ ] **Micrometer + Prometheus** em todos os serviços
- [ ] Dashboard Grafana: taxa de transações/min, sucesso vs falha, volume por tipo de chave
- [ ] Dashboard Grafana: latência **p50 / p95 / p99** por endpoint
- [ ] Dashboard Grafana: estado dos circuit breakers em tempo real (aberto / fechado / half-open)
- [ ] Dashboard Grafana: profundidade e taxa de consumo das filas RabbitMQ/SQS
- [ ] **Distributed tracing com Zipkin:** trace completo de uma transação PIX do gateway até o ledger
- [ ] Structured logging JSON com `correlationId` e `transactionId` em todos os serviços
- [ ] Alertas definidos:
  - [ ] Circuit breaker aberto por mais de 30s
  - [ ] Error rate acima de 1%
  - [ ] Latência p95 acima de 500ms
  - [ ] Fila de mensagens acima de 1.000 itens pendentes

---

## Fase 7 — Spring AI
_Ementa: Spring AI Conceitos, RAG, Tools/MCP, Agentes_

### fraud-service — Detecção de Anomalias
- [ ] Bootstrap Spring AI
- [ ] **Detector de velocidade:** muitas transações em janela de 10 minutos
- [ ] **Detector de valor anômalo:** transação muito acima da média histórica do usuário
- [ ] **Detector de horário:** transação acima do limite noturno do PIX (regra BACEN: R$1.000 entre 20h–6h)
- [ ] Integrar com payment-orchestrator: bloquear transação **antes do Step 3** (antes de qualquer débito)

### RAG com Regulações PIX
- [ ] Indexar documentos: Resolução BCB nº 1, manual operacional do PIX, tabela de limites por horário
- [ ] `POST /fraud/explain` — dado um `transactionId` bloqueado, explicar em PT-BR o motivo
- [ ] `GET /compliance/query?q=` — responder perguntas sobre regras PIX (ex: "qual o limite noturno?")
- [ ] Memória conversacional: contexto de sessão persiste durante a conversa do usuário

### Tools & MCP
- [ ] AI Tool: consultar saldo de uma conta pelo ID
- [ ] AI Tool: verificar status de uma transação
- [ ] AI Tool: listar chaves PIX cadastradas de uma conta
- [ ] AI Tool: buscar histórico de transações com filtros de data e valor
- [ ] Expor as tools acima via **Model Context Protocol (MCP)**

### Agente de Investigação de Fraude (demo final)
- [ ] Agente recebe: `transactionId` bloqueado pelo fraud-service
- [ ] Agente busca autonomamente: histórico do pagador, padrão de horário, valor médio, frequência
- [ ] Agente analisa: avalia nível de risco com base nos dados coletados pelas tools
- [ ] Agente emite: relatório estruturado com recomendação — `APROVAR`, `BLOQUEAR` ou `INVESTIGAR`
- [ ] **Demo de apresentação:** pagamento bloqueado automaticamente → agente investiga → relatório renderizado em tela em tempo real

---

## Estrutura do Monorepo

```
distributed-payment-broker/
├── pom.xml                          # Parent Maven
├── docs
├── services/
│   ├── api-gateway/
│   ├── config-server/
│   ├── discovery-service/
│   ├── account-service/
│   ├── key-service/
│   ├── payment-orchestrator/
│   ├── ledger-service/
│   ├── notification-service/
│   └── fraud-service/
├── proto/
│   ├── account.proto
│   ├── key.proto
│   └── transaction.proto
├── infra/
│   ├── docker-compose.yml
│   ├── observability/
│   │   ├── prometheus.yml
│   │   └── grafana/dashboards/
│   └── aws/
│       └── lambda/
└── scripts/
    ├── start-local.sh
    └── seed-data.sh
```

---

## Stack

| Camada | Tecnologia |
|---|---|
| Framework | Spring Boot 3.x |
| Comunicação síncrona | gRPC (Protobuf) |
| Comunicação assíncrona | RabbitMQ / Amazon SQS |
| API pública | Spring MVC (REST) + Spring GraphQL |
| Config | Spring Cloud Config Server |
| Discovery | Eureka (Spring Cloud Netflix) |
| Gateway | Spring Cloud Gateway |
| Resiliência | Resilience4j |
| Cache | Redis (ElastiCache) |
| Banco de dados | PostgreSQL (RDS) |
| Observabilidade | Micrometer + Prometheus + Grafana + Zipkin |
| Serverless | AWS Lambda + EventBridge |
| IA | Spring AI + RAG + MCP |
| Auth | JWT |
