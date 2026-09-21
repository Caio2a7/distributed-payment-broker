#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Carrega .env se existir
if [ -f "$ROOT_DIR/.env" ]; then
    set -a
    source "$ROOT_DIR/.env"
    set +a
elif [ -f "$SCRIPT_DIR/.env" ]; then
    set -a
    source "$SCRIPT_DIR/.env"
    set +a
fi

PROTO="${1:-udp}"
HOST="${GATEWAY_HOST:-127.0.0.1}"

echo "=== Enviando 20 pagamentos via pay.py (Host: $HOST | Protocolo: ${PROTO^^}) ==="
echo ""

BASE_OFFSET=$(date +%s%N | cut -b12-16)

for i in $(seq 1 20); do
    AMOUNT=$(( 2000 + i * 1500 + BASE_OFFSET ))
    PARCELAS=$(( (i % 3) + 1 ))

    python3 "$SCRIPT_DIR/pay.py" --host "$HOST" --protocol "$PROTO" --amount "$AMOUNT" --installments "$PARCELAS" --req-id "$i"
    sleep 0.05
done

echo ""
echo "=== 20 pagamentos finalizados ==="
