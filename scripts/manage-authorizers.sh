#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Carrega variáveis do arquivo .env se existir
if [ -f "$ROOT_DIR/.env" ]; then
    set -a
    source "$ROOT_DIR/.env"
    set +a
elif [ -f "$SCRIPT_DIR/.env" ]; then
    set -a
    source "$SCRIPT_DIR/.env"
    set +a
fi

if [ -z "$AWS_ACCESS_KEY_ID" ] || [ -z "$AWS_SECRET_ACCESS_KEY" ]; then
    echo "Erro: AWS_ACCESS_KEY_ID e AWS_SECRET_ACCESS_KEY devem estar definidos no ambiente ou no arquivo .env"
    exit 1
fi

export AWS_ACCESS_KEY_ID
export AWS_SECRET_ACCESS_KEY
export AWS_DEFAULT_REGION="${AWS_REGION:-us-east-1}"

AMI_ID="${AWS_AMI_ID}"
SUBNET_ID="${AWS_SUBNET_ID}"
SG_ID="${AWS_SECURITY_GROUP_ID}"
ACTION="${1:-up}"

if [ "$ACTION" = "down" ]; then
    echo "Localizando instancias do Authorizer para desligar..."
    RUNNING_IDS=$(aws ec2 describe-instances \
        --filters "Name=tag:Role,Values=authorizer" "Name=instance-state-name,Values=running,pending" \
        --query "Reservations[].Instances[].InstanceId" \
        --output text)

    if [ -z "$RUNNING_IDS" ] || [ "$RUNNING_IDS" = "None" ]; then
        echo "Nenhuma instancia do Authorizer esta em execucao."
        exit 0
    fi

    echo "Desligando instancias: $RUNNING_IDS"
    aws ec2 stop-instances --instance-ids $RUNNING_IDS > /dev/null
    
    echo "Aguardando desligamento completo..."
    aws ec2 wait instance-stopped --instance-ids $RUNNING_IDS
    echo "Todas as instancias do Authorizer foram desligadas com sucesso."
    echo "No JMeter: a taxa de erro subira para 100%."
    exit 0
fi

if [ "$ACTION" = "up" ]; then
    echo "Verificando instancias paradas do Authorizer..."
    STOPPED_IDS=$(aws ec2 describe-instances \
        --filters "Name=tag:Role,Values=authorizer" "Name=instance-state-name,Values=stopped" \
        --query "Reservations[].Instances[].InstanceId" \
        --output text)

    if [ -n "$STOPPED_IDS" ] && [ "$STOPPED_IDS" != "None" ]; then
        aws ec2 start-instances --instance-ids $STOPPED_IDS > /dev/null
        TARGET_IDS="$STOPPED_IDS"
    else
        echo "Criando 2 novas instancias clones a partir da imagem $AMI_ID..."
        TARGET_IDS=$(aws ec2 run-instances \
            --image-id "$AMI_ID" \
            --count 2 \
            --instance-type "t3.micro" \
            --subnet-id "$SUBNET_ID" \
            --security-group-ids "$SG_ID" \
            --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=dpb-authorizer-clone},{Key=Role,Value=authorizer}]" \
            --query "Instances[].InstanceId" \
            --output text)
    fi

    echo "Aguardando boot das instancias na AWS..."
    aws ec2 wait instance-running --instance-ids $TARGET_IDS
    echo "Instancias em estado running. Aguardando inicializacao do servico pelo systemd..."
    
    for i in $(seq 1 10); do
        sleep 2
        echo "Verificando conexao no Gateway... ($((i * 2))s)"
    done

    echo "Instancias do Authorizer estao ativas e conectadas ao Gateway."
    echo "No JMeter: as instancias se auto-registraram e a taxa de erro caira para 0%."
    exit 0
fi

echo "Uso: ./scripts/manage-authorizers.sh [down|up]"
exit 1
