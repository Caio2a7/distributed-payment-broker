#!/usr/bin/env bash

PROTO="${1:-udp}"

echo "=== Enviando 20 pagamentos via pay.py (Protocolo: ${PROTO^^}) ==="
echo ""

for i in $(seq 1 20); do
    AMOUNT=$(( 2000 + i * 1500 ))
    PARCELAS=$(( (i % 3) + 1 ))

    python3 pay.py --protocol "$PROTO" --amount "$AMOUNT" --installments "$PARCELAS" --req-id "$i"
    sleep 0.05
done

echo ""
echo "=== 20 pagamentos finalizados ==="
