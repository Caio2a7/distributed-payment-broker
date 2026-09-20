# Especificação da Arquitetura em Nuvem (AWS) - Distributed Payment Broker

Este documento descreve a topologia de rede, controle de portas via Network ACL (NACL), computação e os padrões distribuídos implementados no ambiente de nuvem na AWS para a execução de testes de carga e capacidade com o JMeter.

---

## 1. Topologia de Rede e Padrão de Auto-Registro (Self-Registration)

A infraestrutura é provisionada em uma **VPC dedicada** com roteamento interno e padrão **Self-Registration (Auto-Registro)**:
* O **`dpb-gateway`** é a âncora fixa do cluster: possui **Elastic IP público fixo** e **IP privado estático `10.0.1.10`**.
* O **`dpb-ledger`** e as instâncias do **`dpb-authorizer`** recebem IPs privados dinâmicos via DHCP da VPC e se conectam proativamente ao Gateway em `10.0.1.10:9090`, registrando-se via socket TCP permanente (**Single-Socket Channel**).

```text
                                  INTERNET (JMeter / Clientes)
                                                │
                          ┌─────────────────────┼─────────────────────┐
                          │                     │ (Portas Públicas:   │
                     SSH (Porta 22)        9090, 8080, 50051)    SSH (Porta 22)
                          │                     │                     │
                          ▼                     ▼                     ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ VPC: 10.0.0.0/16  |  Subnet Pública: 10.0.1.0/24 (us-east-1a)                      │
│ Internet Gateway: dpb-igw                                                          │
│                                                                                     │
│ CONTROLE DE ACESSO: VPC Network ACL (NACL - 100% Gratuito, a nível de Subnet)       │
│  - Regra 100: SSH (22)                                                              │
│  - Regra 110: HTTP POST (8080)                                                      │
│  - Regra 120: TCP (9090)                                                            │
│  - Regra 130: UDP (9090)                                                            │
│  - Regra 140: gRPC Stream (50051)                                                   │
│  - Regra 150: Tráfego Interno VPC (10.0.0.0/16)                                     │
│  - Regras 160/170: Portas Efêmeras de Retorno (1024-65535)                          │
│                                                                                     │
│  ┌───────────────────────────────────────────────────────────────────────────────┐  │
│  │ [EC2: dpb-gateway] (t3.micro - Amazon Linux 2023)                            │  │
│  │ - IP Público Fixo: AWS Elastic IP (EIP)                                      │  │
│  │ - IP Privado Fixo: 10.0.1.10 (Âncora do Cluster)                              │  │
│  │ - Portas Públicas: 22 (SSH), 9090 (UDP/TCP), 8080 (HTTP), 50051 (gRPC)        │  │
│  └──────────────────────────────────────┬────────────────────────────────────────┘  │
│                                         │                                           │
│                                         │ Rede Interna (VPC 10.0.1.0/24)            │
│                                         │ (Single-Socket Channel TCP 9090)          │
│                       ┌─────────────────┼─────────────────┐                         │
│                       ▼                 ▼                 ▼                         │
│  ┌───────────────────────────┐ ┌────────────────┐ ┌───────────────────────────────┐ │
│  │ [EC2: dpb-authorizer-1]   │ │[authorizer-N]  │ │ [EC2: dpb-ledger]             │ │
│  │ - Stateless (Instância 1) │ │- Stateless (N) │ │ - Stateful                    │ │
│  │ - IP Privado: Dinâmico    │ │- IP: Dinâmico  │ │ - IP Privado: Dinâmico        │ │
│  │ - Self-Registration TCP   │ │- Self-Registr. │ │ - Self-Registration TCP       │ │
│  │ - Portas Externas: 22     │ │- Portas: 22    │ │ - Portas Externas: 22         │ │
│  └───────────────────────────┘ └────────────────┘ └───────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Matriz de Instâncias e Especificações de Hardware

Todas as instâncias utilizam **`t3.micro`** com virtualização HVM e sistema operacional **Amazon Linux 2023**:

| Componente | Papel | Estado | Tipo EC2 | IP Privado | IP Público | Portas de Entrada |
| :--- | :--- | :---: | :---: | :---: | :---: | :--- |
| **`dpb-gateway`** | API Gateway & Load Balancer | Stateless | `t3.micro` | `10.0.1.10` (Fixo) | **Elastic IP** (Fixo) | `22`, `9090` (UDP/TCP), `8080`, `50051` |
| **`dpb-ledger`** | Banco & Livro-Razão Contábil | **Stateful** | `t3.micro` | Dinâmico (DHCP) | Dinâmico (SSH) | `22` (SSH) + VPC Interna (`10.0.0.0/16`) |
| **`dpb-authorizer`** | Autorizador de Cartões (count = N) | **Stateless** | `t3.micro` | Dinâmico (DHCP) | Dinâmico (SSH) | `22` (SSH) + VPC Interna (`10.0.0.0/16`) |

* **Escalabilidade:** A quantidade de instâncias do `authorizer` é controlada pela variável `authorizer_count` no Terraform (padrão: 2).
* **Runtime:** Amazon Corretto Java 17 Headless (instalado automaticamente via Cloud-Init).
* **Armazenamento:** 8 GB EBS gp3 (SSD).

---

## 3. Padrões Distribuídos Aplicados na Nuvem

1. **Single-Socket Channel (*Patterns of Distributed Systems*):**
   * Os nós internos conectam no Gateway em `10.0.1.10:9090` e mantêm uma única conexão TCP aberta continuamente.
   * Elimina o custo de conexão/desconexão e previne esgotamento de portas no kernel sob alta carga.
   * Possui timeout de proteção configurado como $10 \times \text{heartbeatInterval}$ ($30\text{ segundos}$).

2. **Singular Update Queue (*Patterns of Distributed Systems*):**
   * Implementado no `dpb-ledger`. O recebimento de rede enfileira as requisições em uma `BlockingQueue` concorrente e uma única thread dedicada processa as alterações de saldo de forma estritamente sequencial, eliminando qualquer condição de corrida no saldo das lojas.

3. **Self-Registration & Service Discovery:**
   * Tanto o Ledger quanto os Authorizers enviam mensagem de `REGISTER` ao subir. O Gateway adiciona os sockets na sua tabela `ServiceRegistry`.

4. **Heartbeat & Resiliência:**
   * O Gateway pinga os nós periodicamente a cada 3 segundos. Se um nó falhar ou for interrompido, o canal é fechado e removido do registro imediatamente.

5. **Load Balancing Round-Robin (GoF Strategy):**
   * As requisições externas são distribuídas equitativamente entre todas as instâncias ativas do Authorizer.

---

## 4. Segurança e Liberação de Portas via VPC Network ACL (100% Gratuito)

Para respeitar os limites da conta gratuita da AWS e **evitar qualquer custo de serviços gerenciados pagos (como AWS Network Firewall)**, todo o controle de portas e filtragem de pacotes é implementado na **VPC Network ACL (`aws_network_acl`)** a nível de subnet:

### Regras de Entrada (Ingress):
* **Regra 100 (`TCP 22`):** Permite acesso SSH administrativo.
* **Regra 110 (`TCP 8080`):** Permite requisições HTTP REST (JMeter).
* **Regra 120 (`TCP 9090`):** Permite conexões TCP (JMeter + Single-Socket Channel interno).
* **Regra 130 (`UDP 9090`):** Permite datagramas UDP (JMeter).
* **Regra 140 (`TCP 50051`):** Permite chamadas gRPC Streaming (JMeter).
* **Regra 150 (`ALL 10.0.0.0/16`):** Permite comunicação irrestrita entre os nós internos dentro da VPC.
* **Regras 160 e 170 (`TCP/UDP 1024-65535`):** Permite tráfego de retorno em portas efêmeras.

### Regras de Saída (Egress):
* **Regra 100 (`ALL 0.0.0.0/0`):** Saída livre para internet para downloads de pacotes do sistema operacional (`dnf install`) e respostas a clientes.

---

## 5. Guia de Execução e Deploy

### Passo 1: Executar o Deploy Automatizado
A partir da raiz do repositório:
```bash
./cloud/deploy.sh
```

### Passo 2: Testar com o Cliente Python ou JMeter
Após o deploy, o script exibirá o **IP Público Fixo do Gateway**.

Execute os testes apontando para a nuvem:
```bash
# Executar 20 pagamentos via UDP:
GATEWAY_HOST="<IP_PUBLICO_GATEWAY>" ./run-20-payments.sh udp

# Executar 20 pagamentos via TCP:
GATEWAY_HOST="<IP_PUBLICO_GATEWAY>" ./run-20-payments.sh tcp

# Executar 20 pagamentos via HTTP:
GATEWAY_HOST="<IP_PUBLICO_GATEWAY>" ./run-20-payments.sh http

# Executar 20 pagamentos via gRPC:
GATEWAY_HOST="<IP_PUBLICO_GATEWAY>" ./run-20-payments.sh grpc
```

### Passo 3: Escalar o Authorizer (Ex: subir 4 instâncias)
```bash
cd cloud
terraform apply -var="authorizer_count=4" -auto-approve
```

### Passo 4: Destruir a Infraestrutura (Teardown)
```bash
cd cloud
terraform destroy -auto-approve
```
