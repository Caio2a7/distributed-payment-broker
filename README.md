# Distributed Payment Broker (DPB)

Sistema distribuído de intermediação de pagamentos com tolerância a falhas, balanceamento de carga Round-Robin e suporte concorrente aos protocolos HTTP (8080), TCP (9090), UDP (9090) e gRPC (50051).

## Requisitos

- Java 21+
- Maven 3.8+
- Apache JMeter 5.6+
- AWS CLI (para controle de instâncias remotas)

## Configuração de Ambiente (.env)

Copie o arquivo de exemplo e preencha as variáveis de ambiente antes da execução:

```bash
cp .env.example .env
```

Defina no `.env` os parâmetros de rede e as credenciais necessárias:
- `GATEWAY_HOST`: Endereço IP do Gateway (padrão local: `127.0.0.1` ou IP público da AWS)
- `AWS_ACCESS_KEY_ID` e `AWS_SECRET_ACCESS_KEY`: Chaves de acesso AWS para gerenciar instâncias
- `AWS_REGION`: Região da AWS (padrão: `us-east-1`)

## Compilação

Execute a compilação a partir da raiz do repositório:

```bash
mvn clean package -DskipTests
```

Artefatos gerados nos módulos:
- dpb-gateway/target/dpb-gateway-1.0.0.jar
- dpb-authorizer/target/dpb-authorizer-1.0.0.jar
- dpb-ledger/target/dpb-ledger-1.0.0.jar

## Execução

Inicie os componentes em terminais separados na seguinte ordem:

1. API Gateway (inicializa as portas 8080, 9090 e 50051):
```bash
java -jar dpb-gateway/target/dpb-gateway-1.0.0.jar
```

2. Ledger (nó contábil stateful com fila sequencial):
```bash
java -jar dpb-ledger/target/dpb-ledger-1.0.0.jar
```

3. Instâncias do Authorizer (motores de autorização stateless):
```bash
java -jar dpb-authorizer/target/dpb-authorizer-1.0.0.jar
```

## Testes com JMeter

Os planos de teste encontram-se no diretório `scripts/`:

- Teste de carga contínua para apresentação e resiliência (6 threads, 6s ramp-up):
```bash
jmeter -t scripts/dpb-load-test.jmx
```

- Teste de capacidade (medição de Knee e Usable Capacity):
```bash
jmeter -n -t scripts/dpb-metrics-test.jmx -Jthreads=20 -Jrampup=5 -Jloops=5 -l resultado.jtl
```

- Cliente manual para testes pontuais de cada protocolo:
```bash
python3 scripts/pay.py -p http
python3 scripts/pay.py -p tcp
python3 scripts/pay.py -p udp
python3 scripts/pay.py -p grpc
```

## Controle de Instâncias (AWS)

```bash
./scripts/manage-authorizers.sh down   # Desliga instâncias do Authorizer para injeção de falha
./scripts/manage-authorizers.sh up     # Liga instâncias do Authorizer para validar auto-registro
```
