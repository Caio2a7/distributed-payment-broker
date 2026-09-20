terraform {
  required_version = ">= 1.0.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
    local = {
      source  = "hashicorp/local"
      version = "~> 2.4"
    }
  }
}

variable "aws_region" {
  type        = string
  default     = "us-east-1"
  description = "Região AWS de provisionamento"
}

variable "authorizer_count" {
  type        = number
  default     = 2
  description = "Quantidade de instâncias stateless do Authorizer (escalável: 2, 3, 4, etc.)"
}

provider "aws" {
  region = var.aws_region
}

# ============================================================
# 1. REDE: VPC, Subnet e Internet Gateway
# ============================================================
resource "aws_vpc" "dpb_vpc" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name = "dpb-vpc"
  }
}

resource "aws_internet_gateway" "dpb_igw" {
  vpc_id = aws_vpc.dpb_vpc.id

  tags = {
    Name = "dpb-igw"
  }
}

resource "aws_subnet" "dpb_subnet" {
  vpc_id                  = aws_vpc.dpb_vpc.id
  cidr_block              = "10.0.1.0/24"
  map_public_ip_on_launch = true
  availability_zone       = "${var.aws_region}a"

  tags = {
    Name = "dpb-subnet-public"
  }
}

resource "aws_route_table" "dpb_rt" {
  vpc_id = aws_vpc.dpb_vpc.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.dpb_igw.id
  }

  tags = {
    Name = "dpb-route-table"
  }
}

resource "aws_route_table_association" "dpb_rta" {
  subnet_id      = aws_subnet.dpb_subnet.id
  route_table_id = aws_route_table.dpb_rt.id
}

# ============================================================
# 2. CONTROLE DE ACESSO: VPC Network ACL (NACL - 100% Gratuito)
# ============================================================
resource "aws_network_acl" "dpb_nacl" {
  vpc_id     = aws_vpc.dpb_vpc.id
  subnet_ids = [aws_subnet.dpb_subnet.id]

  ingress {
    rule_no    = 100
    protocol   = "tcp"
    action     = "allow"
    cidr_block = "0.0.0.0/0"
    from_port  = 22
    to_port    = 22
  }

  ingress {
    rule_no    = 110
    protocol   = "tcp"
    action     = "allow"
    cidr_block = "0.0.0.0/0"
    from_port  = 8080
    to_port    = 8080
  }

  ingress {
    rule_no    = 120
    protocol   = "tcp"
    action     = "allow"
    cidr_block = "0.0.0.0/0"
    from_port  = 9090
    to_port    = 9090
  }

  ingress {
    rule_no    = 130
    protocol   = "udp"
    action     = "allow"
    cidr_block = "0.0.0.0/0"
    from_port  = 9090
    to_port    = 9090
  }

  ingress {
    rule_no    = 140
    protocol   = "tcp"
    action     = "allow"
    cidr_block = "0.0.0.0/0"
    from_port  = 50051
    to_port    = 50051
  }

  ingress {
    rule_no    = 150
    protocol   = "-1"
    action     = "allow"
    cidr_block = "10.0.0.0/16"
    from_port  = 0
    to_port    = 0
  }

  ingress {
    rule_no    = 160
    protocol   = "tcp"
    action     = "allow"
    cidr_block = "0.0.0.0/0"
    from_port  = 1024
    to_port    = 65535
  }

  ingress {
    rule_no    = 170
    protocol   = "udp"
    action     = "allow"
    cidr_block = "0.0.0.0/0"
    from_port  = 1024
    to_port    = 65535
  }

  egress {
    rule_no    = 100
    protocol   = "-1"
    action     = "allow"
    cidr_block = "0.0.0.0/0"
    from_port  = 0
    to_port    = 0
  }

  tags = {
    Name = "dpb-nacl"
  }
}

# ============================================================
# 3. SECURITY GROUPS (Camada nativa gratuita da AWS)
# ============================================================
resource "aws_security_group" "gateway_sg" {
  name        = "dpb-gateway-sg"
  description = "Security Group publico para o DPB Gateway"
  vpc_id      = aws_vpc.dpb_vpc.id

  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "JMeter UDP Sampler"
    from_port   = 9090
    to_port     = 9090
    protocol    = "udp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "JMeter TCP Sampler e Canais Internos TCP"
    from_port   = 9090
    to_port     = 9090
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "JMeter HTTP POST REST"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "JMeter gRPC Stream"
    from_port   = 50051
    to_port     = 50051
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "dpb-gateway-sg"
  }
}

resource "aws_security_group" "internal_sg" {
  name        = "dpb-internal-sg"
  description = "Security Group para Authorizer e Ledger (sem portas de aplicacao abertas para a internet)"
  vpc_id      = aws_vpc.dpb_vpc.id

  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "Comunicacao interna VPC"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["10.0.0.0/16"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "dpb-internal-sg"
  }
}

# ============================================================
# 4. CHAVE SSH AUTOMATICA
# ============================================================
resource "tls_private_key" "dpb_key" {
  algorithm = "RSA"
  rsa_bits  = 4096
}

resource "aws_key_pair" "dpb_key_pair" {
  key_name   = "dpb-cluster-key"
  public_key = tls_private_key.dpb_key.public_key_openssh
}

resource "local_file" "private_key" {
  content         = tls_private_key.dpb_key.private_key_pem
  filename        = "${path.module}/dpb-key.pem"
  file_permission = "0400"
}

# ============================================================
# 5. AMI E USER DATA (Amazon Linux 2023 + Java 17)
# ============================================================
data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-x86_64"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

locals {
  user_data_script = <<-EOF
    #!/bin/bash
    dnf update -y
    dnf install -y java-21-amazon-corretto-headless
    mkdir -p /opt/dpb
    chown -R ec2-user:ec2-user /opt/dpb
  EOF
}

# ============================================================
# 6. INSTANCIAS EC2 (t3.micro)
# ============================================================
resource "aws_instance" "gateway" {
  ami                    = data.aws_ami.al2023.id
  instance_type          = "t3.micro"
  subnet_id              = aws_subnet.dpb_subnet.id
  private_ip             = "10.0.1.10"
  vpc_security_group_ids = [aws_security_group.gateway_sg.id]
  key_name               = aws_key_pair.dpb_key_pair.key_name
  user_data              = local.user_data_script

  tags = {
    Name = "dpb-gateway"
    Role = "gateway"
  }
}

resource "aws_eip" "gateway_eip" {
  instance = aws_instance.gateway.id
  domain   = "vpc"

  tags = {
    Name = "dpb-gateway-eip"
  }
}

resource "aws_instance" "ledger" {
  ami                    = data.aws_ami.al2023.id
  instance_type          = "t3.micro"
  subnet_id              = aws_subnet.dpb_subnet.id
  vpc_security_group_ids = [aws_security_group.internal_sg.id]
  key_name               = aws_key_pair.dpb_key_pair.key_name
  user_data              = local.user_data_script

  tags = {
    Name = "dpb-ledger"
    Role = "ledger"
  }
}

resource "aws_instance" "authorizer" {
  count                  = var.authorizer_count
  ami                    = data.aws_ami.al2023.id
  instance_type          = "t3.micro"
  subnet_id              = aws_subnet.dpb_subnet.id
  vpc_security_group_ids = [aws_security_group.internal_sg.id]
  key_name               = aws_key_pair.dpb_key_pair.key_name
  user_data              = local.user_data_script

  tags = {
    Name = "dpb-authorizer-${count.index + 1}"
    Role = "authorizer"
  }
}

# ============================================================
# 7. OUTPUTS
# ============================================================
output "gateway_public_ip" {
  description = "IP Publico Fixo (Elastic IP) do API Gateway"
  value       = aws_eip.gateway_eip.public_ip
}

output "gateway_private_ip" {
  value = aws_instance.gateway.private_ip
}

output "ledger_public_ip" {
  value = aws_instance.ledger.public_ip
}

output "ledger_private_ip" {
  value = aws_instance.ledger.private_ip
}

output "authorizer_public_ips" {
  description = "Lista de IPs publicos das instancias do Authorizer"
  value       = aws_instance.authorizer[*].public_ip
}

output "authorizer_private_ips" {
  description = "Lista de IPs privados das instancias do Authorizer"
  value       = aws_instance.authorizer[*].private_ip
}

output "ssh_commands" {
  description = "Comandos diretos para acesso SSH a cada instancia"
  value = merge(
    {
      gateway = "ssh -i dpb-key.pem ec2-user@${aws_eip.gateway_eip.public_ip}"
      ledger  = "ssh -i dpb-key.pem ec2-user@${aws_instance.ledger.public_ip}"
    },
    { for idx, ip in aws_instance.authorizer[*].public_ip : "authorizer_${idx + 1}" => "ssh -i dpb-key.pem ec2-user@${ip}" }
  )
}
