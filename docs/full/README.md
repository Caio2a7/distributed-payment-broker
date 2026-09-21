# Distributed Payment Broker (DPB)

Servidor de pagamentos distribuído baseado no padrão arquitetural *Remoting Patterns* com protocolo binário.

## Requisitos

- Java 17+
- Maven 3.8+

## Como Executar

A partir da raiz do repositório:

### Modo Desenvolvimento
```bash
mvn clean compile exec:java
```

### Modo JAR
```bash
mvn clean package
java -jar target/distributed-payment-broker-1.0.0.jar
```
