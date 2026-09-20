#!/usr/bin/env bash
set -e

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLOUD_DIR="$ROOT_DIR/cloud"

echo "[1/5] Maven package"
mvn clean package -DskipTests -q -f "$ROOT_DIR/pom.xml"

echo "[2/5] Terraform apply"
terraform -chdir="$CLOUD_DIR" init -input=false > /dev/null
terraform -chdir="$CLOUD_DIR" apply -auto-approve -input=false > /dev/null

echo "[3/5] Coletando IPs"
KEY="$CLOUD_DIR/dpb-key.pem"
chmod 400 "$KEY"
GW_PUB=$(terraform -chdir="$CLOUD_DIR" output -raw gateway_public_ip)
GW_PRIV=$(terraform -chdir="$CLOUD_DIR" output -raw gateway_private_ip)
LEDGER_PUB=$(terraform -chdir="$CLOUD_DIR" output -raw ledger_public_ip)
AUTH_IPS=($(terraform -chdir="$CLOUD_DIR" output -json authorizer_public_ips | grep -oE '[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+'))

wait_ssh() {
    until ssh -i "$KEY" -o StrictHostKeyChecking=no -o LogLevel=ERROR -o ConnectTimeout=3 -q ec2-user@"$1" "true" 2>/dev/null; do
        sleep 2
    done
}

echo "[4/5] Aguardando SSH"
wait_ssh "$GW_PUB"
wait_ssh "$LEDGER_PUB"
for ip in "${AUTH_IPS[@]}"; do wait_ssh "$ip"; done

deploy_jar() {
    local ip=$1 jar=$2 cmd=$3 unit=$4
    scp -i "$KEY" -o StrictHostKeyChecking=no -o LogLevel=ERROR -q "$jar" ec2-user@"$ip":/home/ec2-user/app.jar
    ssh -i "$KEY" -o StrictHostKeyChecking=no -o LogLevel=ERROR ec2-user@"$ip" "
        sudo systemctl stop $unit 2>/dev/null || true
        sudo tee /etc/systemd/system/$unit.service > /dev/null << 'SERVICE'
[Unit]
Description=$unit
After=network.target
[Service]
Type=simple
User=ec2-user
WorkingDirectory=/home/ec2-user
ExecStart=/usr/bin/java -Xms128m -Xmx384m -jar /home/ec2-user/app.jar $cmd
Restart=always
RestartSec=3
StandardOutput=append:/home/ec2-user/$unit.log
StandardError=append:/home/ec2-user/$unit.log
[Install]
WantedBy=multi-user.target
SERVICE
        sudo systemctl daemon-reload
        sudo systemctl enable $unit > /dev/null 2>&1
        sudo systemctl restart $unit
    "
}

echo "[5/5] Realizando deploy"
deploy_jar "$GW_PUB" "$ROOT_DIR/dpb-gateway/target/dpb-gateway-1.0.0.jar" "9090" "dpb-gateway"
sleep 2
deploy_jar "$LEDGER_PUB" "$ROOT_DIR/dpb-ledger/target/dpb-ledger-1.0.0.jar" "$GW_PRIV 9090" "dpb-ledger"
sleep 2
for ip in "${AUTH_IPS[@]}"; do
    deploy_jar "$ip" "$ROOT_DIR/dpb-authorizer/target/dpb-authorizer-1.0.0.jar" "$GW_PRIV 9090" "dpb-authorizer"
done

echo "Gateway IP: $GW_PUB"
