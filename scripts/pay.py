#!/usr/bin/env python3
import argparse
import json
import os
import random
import socket
import struct
import sys
import urllib.request
import urllib.error
import grpc

def _load_env_file():
    root_env = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".env"))
    script_env = os.path.abspath(os.path.join(os.path.dirname(__file__), ".env"))
    for env_path in [root_env, script_env]:
        if os.path.exists(env_path):
            with open(env_path, "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if line and not line.startswith("#") and "=" in line:
                        k, v = line.split("=", 1)
                        k, v = k.strip(), v.strip().strip("'").strip('"')
                        if k not in os.environ:
                            os.environ[k] = v
            break

_load_env_file()


GATEWAY_UDP_PORT = 9090
GATEWAY_TCP_PORT = 9090
GATEWAY_HTTP_PORT = 8080
GATEWAY_GRPC_PORT = 50051

REGISTERED_MIDS = [
    "100202604812345",  # O Boticário
    "100202607965432",  # Cacau Show
    "100202608409160",  # Supermercados Nordestão
    "100202609745875",  # Farmácias Globo
    "100202603927547"   # Lojas Renner
]

STATUS_NAMES = {
    1: "PENDING",
    2: "REJECTED",
    3: "ACCEPTED",
    4: "COMPLETED",
    5: "FAILED"
}

def send_udp(host, packet, req_id, gross_amount, mid):
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.settimeout(3.0)
    try:
        sock.sendto(packet, (host, GATEWAY_UDP_PORT))
        resp_data, _ = sock.recvfrom(1024)
        print_binary_response(resp_data, "UDP", req_id, gross_amount, mid)
    finally:
        sock.close()

def send_tcp(host, packet, req_id, gross_amount, mid):
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(3.0)
    try:
        sock.connect((host, GATEWAY_TCP_PORT))
        framed = struct.pack(">i", len(packet)) + packet
        sock.sendall(framed)

        len_bytes = sock.recv(4)
        if not len_bytes:
            print(f"[ERRO] Req #{req_id:02d} [TCP] | Conexão fechada pelo servidor")
            return
        resp_len = struct.unpack(">i", len_bytes)[0]
        resp_data = bytearray()
        while len(resp_data) < resp_len:
            chunk = sock.recv(resp_len - len(resp_data))
            if not chunk:
                break
            resp_data.extend(chunk)

        print_binary_response(resp_data, "TCP", req_id, gross_amount, mid)
    finally:
        sock.close()

def send_http(host, mid, card, amount, installments, op, month, year, req_id):
    url = f"http://{host}:{GATEWAY_HTTP_PORT}/payments"
    payload = {
        "mid": mid,
        "cardNumber": card,
        "amount": amount,
        "installments": installments,
        "operation": op,
        "expMonth": month,
        "expYear": year
    }
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"}, method="POST")

    try:
        with urllib.request.urlopen(req, timeout=3.0) as resp:
            body = json.loads(resp.read().decode("utf-8"))
            status = body.get("status", "UNKNOWN")
            liquid = body.get("liquidAmount", 0)
            fp = body.get("fingerprint", "")
            print(f"[OK] Req #{req_id:02d} [HTTP] | Status: {status} | Bruto: R$ {amount/100:.2f} | Líquido: R$ {liquid/100:.2f} | Loja: {mid} | Hash: {fp}")
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8")
        print(f"[ERRO] Req #{req_id:02d} [HTTP {e.code}] | {err_body}")
    except Exception as e:
        print(f"[FALHA] Req #{req_id:02d} [HTTP] | {e}")

def send_grpc(host, packet, req_id, gross_amount, mid):
    channel = grpc.insecure_channel(f"{host}:{GATEWAY_GRPC_PORT}")
    stream_fn = channel.stream_stream("/payment.PaymentService/ProcessPaymentStream")

    def req_gen():
        yield bytes(packet)

    try:
        responses = stream_fn(req_gen(), timeout=3.0)
        for resp_data in responses:
            print_binary_response(resp_data, "gRPC", req_id, gross_amount, mid)
    except grpc.RpcError as e:
        details = e.details() if hasattr(e, "details") and e.details() else str(e)
        print(f"[ERRO] Req #{req_id:02d} [gRPC] | {details}")
    finally:
        channel.close()

def print_binary_response(resp_data, proto_label, req_id, gross_amount, mid):
    resp_type, resp_req_id, resp_payload_len = struct.unpack(">Bii", resp_data[:9])
    resp_payload = resp_data[9:9 + resp_payload_len]

    if resp_type == 2:
        fp_len = resp_payload[0]
        fingerprint = resp_payload[1:1 + fp_len].decode("utf-8")
        status_code = resp_payload[1 + fp_len]
        liquid_amount = struct.unpack(">q", resp_payload[2 + fp_len:10 + fp_len])[0]
        status_str = STATUS_NAMES.get(status_code, f"UNKNOWN({status_code})")
        print(f"[OK] Req #{resp_req_id:02d} [{proto_label}] | Status: {status_str} | Bruto: R$ {gross_amount/100:.2f} | Líquido: R$ {liquid_amount/100:.2f} | Loja: {mid} | Hash: {fingerprint}")
    elif resp_type == 99:
        err_msg = resp_payload.decode("utf-8")
        print(f"[ERRO] Req #{resp_req_id:02d} [{proto_label}] | {err_msg}")
    else:
        print(f"[WARN] Req #{resp_req_id:02d} [{proto_label}] | Tipo inesperado: {resp_type}")

def main():
    default_host = os.environ.get("GATEWAY_HOST", "127.0.0.1")
    parser = argparse.ArgumentParser(description="Cliente de Pagamento Multi-Protocolo (UDP, TCP, HTTP, gRPC) para o DPB Gateway")
    parser.add_argument("--host", default=default_host, help="Host do Gateway (padrao: GATEWAY_HOST ou 127.0.0.1)")
    parser.add_argument("-p", "--protocol", choices=["udp", "tcp", "http", "grpc"], default="udp", help="Protocolo a ser testado (udp, tcp, http, grpc)")
    parser.add_argument("--mid", default=None, help="MID do lojista (15 dígitos). Se omitido, sorteia aleatoriamente entre os cadastrados.")
    parser.add_argument("--card", default="4111111111111111", help="Número do cartão de crédito (Visa válido)")
    parser.add_argument("--amount", type=int, default=10000, help="Valor bruto em centavos (ex: 10000 = R$ 100.00)")
    parser.add_argument("--installments", type=int, default=1, help="Número de parcelas (1 a 12)")
    parser.add_argument("--op", choices=["PAYMENT", "REFUND"], default="PAYMENT", help="Operação")
    parser.add_argument("--month", type=int, default=12, help="Mês de expiração (1-12)")
    parser.add_argument("--year", type=int, default=2028, help="Ano de expiração")
    parser.add_argument("--req-id", type=int, default=1, help="ID sequencial da requisição")

    args = parser.parse_args()

    mid = args.mid if args.mid else random.choice(REGISTERED_MIDS)

    if args.protocol == "http":
        send_http(args.host, mid, args.card, args.amount, args.installments, args.op, args.month, args.year, args.req_id)
        return

    mid_bytes = mid.encode("utf-8")
    card_bytes = args.card.encode("utf-8")
    op_code = 0 if args.op == "PAYMENT" else 1

    payload = bytearray()
    payload.append(len(mid_bytes))
    payload.extend(mid_bytes)
    payload.append(op_code)
    payload.append(len(card_bytes))
    payload.extend(card_bytes)
    payload.extend(struct.pack(">i", args.month))
    payload.extend(struct.pack(">i", args.year))
    payload.extend(struct.pack(">q", args.amount))
    payload.extend(struct.pack(">q", args.installments))

    header = struct.pack(">Bii", 1, args.req_id, len(payload))
    packet = header + payload

    try:
        if args.protocol == "udp":
            send_udp(args.host, packet, args.req_id, args.amount, mid)
        elif args.protocol == "tcp":
            send_tcp(args.host, packet, args.req_id, args.amount, mid)
        elif args.protocol == "grpc":
            send_grpc(args.host, packet, args.req_id, args.amount, mid)
    except socket.timeout:
        print(f"[TIMEOUT] Req #{args.req_id:02d} [{args.protocol.upper()}] | Sem resposta do Gateway em {args.host}")
        sys.exit(1)
    except Exception as e:
        print(f"[FALHA] Req #{args.req_id:02d} [{args.protocol.upper()}] | {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
