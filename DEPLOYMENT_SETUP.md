# Gephub Deployment Setup Guide

## Table of Contents
1. [GitHub Repository Setup](#github-repository-setup)
2. [Ubuntu VM Setup](#ubuntu-vm-setup)
3. [Repository Structure](#repository-structure)
4. [Environment Configuration](#environment-configuration)
5. [Pre-Deployment Checklist](#pre-deployment-checklist)

---

## GitHub Repository Setup

### Option 1: Monorepo (Recommended for this project)

**Single repository containing all services:**
```
gephub/
├── gephub-auth-service/
├── kyc-service/
├── meets-service/
├── builder-service/
├── kyc-worker/
├── docker-compose.yml
├── .github/workflows/
└── README.md
```

**Steps:**
1. Go to GitHub.com → Create new repository
   - Name: `gephub`
   - Description: "Gephub - Payment, KYC, Meets, and Builder SDK Platform"
   - Visibility: Private (recommended) or Public
   - Initialize: Don't add README (we already have code)

2. Push your code:
```bash
cd /path/to/your/gephub/project
git init
git add .
git commit -m "Initial commit: All microservices"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/gephub.git
git push -u origin main
```

### Option 2: Multi-Repo (Alternative)

If you prefer separate repos:
- `gephub-auth-service`
- `gephub-kyc-service`
- `gephub-meets-service`
- `gephub-builder-service`
- `gephub-kyc-worker`
- `gephub-infrastructure` (docker-compose, scripts)

---

## Ubuntu VM Setup

### 1. Initial VM Setup

**If using VirtualBox:**
1. Download Ubuntu Server 22.04 LTS (or 24.04)
2. Create new VM:
   - RAM: 4GB minimum (8GB recommended)
   - Storage: 50GB minimum
   - Network: NAT or Bridged Adapter
3. Install Ubuntu (choose minimal installation)

**If using cloud (AWS/DigitalOcean/Azure):**
- Create Ubuntu 22.04 instance
- Ensure SSH access is configured

### 2. Initial Server Configuration

```bash
# Update system
sudo apt update && sudo apt upgrade -y

# Install essential tools
sudo apt install -y curl wget git vim nano htop ufw

# Set up firewall
sudo ufw allow 22/tcp    # SSH
sudo ufw allow 80/tcp    # HTTP
sudo ufw allow 443/tcp   # HTTPS
sudo ufw allow 8080/tcp  # Auth service
sudo ufw allow 8081/tcp  # KYC service
sudo ufw allow 8082/tcp  # Meets service
sudo ufw allow 8083/tcp  # Builder service
sudo ufw enable

# Create gephub user (optional, for security)
sudo useradd -m -s /bin/bash gephub
sudo usermod -aG sudo gephub
```

### 3. Install Docker

```bash
# Remove old versions
sudo apt remove docker docker-engine docker.io containerd runc

# Install prerequisites
sudo apt install -y \
    ca-certificates \
    curl \
    gnupg \
    lsb-release

# Add Docker's official GPG key
sudo mkdir -p /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg

# Set up repository
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Install Docker Engine
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Add your user to docker group (avoid sudo)
sudo usermod -aG docker $USER
# Log out and log back in for this to take effect

# Verify installation
docker --version
docker compose version
```

### 4. Install Docker Compose (if not included)

```bash
# Docker Compose V2 is included with Docker Engine, but if you need standalone:
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose
docker-compose --version
```

### 5. Install PostgreSQL Client (for debugging)

```bash
sudo apt install -y postgresql-client
```

### 6. Install Git (if not already installed)

```bash
sudo apt install -y git
git config --global user.name "Your Name"
git config --global user.email "your.email@example.com"
```

---

## Repository Structure

### Recommended Structure (Monorepo)

```
gephub/
├── .github/
│   └── workflows/
│       ├── ci-auth.yml
│       ├── ci-kyc.yml
│       ├── ci-meets.yml
│       ├── ci-builder.yml
│       └── cd-deploy.yml
├── gephub-auth-service/
│   ├── src/
│   ├── pom.xml
│   ├── Dockerfile
│   └── README.md
├── kyc-service/
│   ├── src/
│   ├── pom.xml
│   ├── Dockerfile
│   └── README.md
├── meets-service/
│   ├── src/
│   ├── pom.xml
│   ├── Dockerfile
│   └── README.md
├── builder-service/
│   ├── src/
│   ├── pom.xml
│   ├── Dockerfile
│   └── README.md
├── kyc-worker/
│   ├── worker.py
│   ├── requirements.txt
│   ├── Dockerfile
│   └── README.md
├── docker-compose.yml
├── docker-compose.prod.yml
├── .env.example
├── .gitignore
├── README.md
├── DEPLOYMENT_SETUP.md
└── scripts/
    ├── setup.sh
    ├── deploy.sh
    └── backup.sh
```

### Creating .gitignore

Create `.gitignore` in root:

```gitignore
# Maven
**/target/
**/.mvn/
**/mvnw
**/mvnw.cmd

# IDE
.idea/
.vscode/
*.iml
*.class
*.log

# Environment
.env
.env.local
*.pem
*.key

# Docker
docker-compose.override.yml

# OS
.DS_Store
Thumbs.db

# Application specific
**/var/lib/gephub/
**/recordings/
**/media/
**/builder-projects/
```

---

## Environment Configuration

### 1. Create .env.example

Create `.env.example` in root:

```bash
# Database
DB_HOST=postgres
DB_PORT=5432
DB_USER=gephub
DB_PASSWORD=gephub_change_me
DB_NAME_AUTH=gephub_auth
DB_NAME_KYC=gephub_kyc
DB_NAME_MEETS=gephub_meets
DB_NAME_BUILDER=gephub_builder

# Redis
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=

# RabbitMQ
RABBITMQ_HOST=rabbitmq
RABBITMQ_PORT=5672
RABBITMQ_USER=gephub
RABBITMQ_PASSWORD=gephub_change_me

# JWT
GEPHUB_JWT_PUBLIC=
GEPHUB_JWT_PRIVATE=
JWT_ISSUER=https://auth.gephub.local
JWT_JWKS_URI=http://localhost:8080/.well-known/jwks.json

# Auth Service
AUTH_SERVER_PORT=8080

# KYC Service
KYC_SERVER_PORT=8081
KYC_STORAGE_ROOT=/var/lib/gephub/kyc-media

# Meets Service
MEETS_SERVER_PORT=8082
MEETS_RECORDINGS_ROOT=/var/lib/gephub/meets-recordings
LIVEKIT_API_KEY=
LIVEKIT_API_SECRET=
LIVEKIT_HOST=http://localhost:7880

# Builder Service
BUILDER_SERVER_PORT=8083
BUILDER_STORAGE_ROOT=/var/lib/gephub/builder-projects
GITHUB_ACCESS_TOKEN=
AI_API_KEY=
AI_PROVIDER=openai
AI_MODEL=gpt-4-turbo-preview
DOCKER_DEPLOYMENT_ENABLED=false
DOCKER_REGISTRY=

# Worker Token (for KYC worker to call KYC service)
KYC_WORKER_TOKEN=your-secure-worker-token
```

### 2. Generate JWT Keys

On your local machine or VM:

```bash
# Generate RSA key pair
openssl genrsa -out jwt_private.pem 2048
openssl rsa -in jwt_private.pem -pubout -out jwt_public.pem

# Convert to single line for .env (remove newlines)
cat jwt_private.pem | tr -d '\n' > jwt_private_oneline.txt
cat jwt_public.pem | tr -d '\n' > jwt_public_oneline.txt
```

Add to `.env`:
```
GEPHUB_JWT_PUBLIC=$(cat jwt_public_oneline.txt)
GEPHUB_JWT_PRIVATE=$(cat jwt_private_oneline.txt)
```

---

## Pre-Deployment Checklist

### Before First Deployment

- [ ] GitHub repository created and code pushed
- [ ] Ubuntu VM set up and accessible via SSH
- [ ] Docker and Docker Compose installed
- [ ] Firewall rules configured
- [ ] `.env` file created from `.env.example` with real values
- [ ] JWT keys generated and added to `.env`
- [ ] GitHub access token obtained (for builder service)
- [ ] AI API key obtained (OpenAI/Anthropic, for builder service)
- [ ] LiveKit API key/secret obtained (for meets service)
- [ ] Database passwords changed from defaults
- [ ] RabbitMQ passwords changed from defaults
- [ ] All ports available (8080-8083, 5432, 6379, 5672)

### GitHub Access Token Setup

1. Go to GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic)
2. Generate new token:
   - Name: `Gephub Builder Service`
   - Expiration: As needed
   - Scopes: `repo` (full control)
3. Copy token and add to `.env`:
   ```
   GITHUB_ACCESS_TOKEN=ghp_xxxxxxxxxxxx
   ```

### AI API Key Setup

**OpenAI:**
1. Go to https://platform.openai.com/api-keys
2. Create new secret key
3. Add to `.env`:
   ```
   AI_API_KEY=sk-xxxxxxxxxxxx
   AI_PROVIDER=openai
   ```

**Or Anthropic:**
1. Go to https://console.anthropic.com/
2. Create API key
3. Add to `.env`:
   ```
   AI_API_KEY=sk-ant-xxxxxxxxxxxx
   AI_PROVIDER=anthropic
   ```

### LiveKit Setup

1. Install LiveKit locally or use LiveKit Cloud
2. Get API key and secret from LiveKit dashboard
3. Add to `.env`:
   ```
   LIVEKIT_API_KEY=your-api-key
   LIVEKIT_API_SECRET=your-api-secret
   LIVEKIT_HOST=http://localhost:7880
   ```

---

## Next Steps

1. **Clone repository on VM:**
   ```bash
   git clone https://github.com/YOUR_USERNAME/gephub.git
   cd gephub
   ```

2. **Create .env file:**
   ```bash
   cp .env.example .env
   nano .env  # Edit with your values
   ```

3. **Start services:**
   ```bash
   docker compose up -d
   ```

4. **Check logs:**
   ```bash
   docker compose logs -f
   ```

5. **Test endpoints:**
   ```bash
   curl http://localhost:8080/actuator/health
   ```

---

## Troubleshooting

### Docker Permission Denied
```bash
sudo usermod -aG docker $USER
# Log out and back in
```

### Port Already in Use
```bash
sudo netstat -tulpn | grep :8080
sudo kill <PID>
```

### Database Connection Issues
```bash
docker compose ps
docker compose logs postgres
```

### Out of Memory
```bash
# Increase VM RAM or add swap
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
```

---

## Security Recommendations

1. **Change all default passwords**
2. **Use strong passwords** (16+ characters, mixed case, numbers, symbols)
3. **Enable SSH key authentication** (disable password auth)
4. **Set up fail2ban** for SSH protection
5. **Regular security updates:** `sudo apt update && sudo apt upgrade`
6. **Backup database regularly**
7. **Monitor logs:** `docker compose logs -f`
8. **Use HTTPS** in production (Let's Encrypt)
9. **Restrict firewall** to necessary ports only
10. **Keep JWT keys secure** (never commit to git)

---

Ready to proceed with deployment? Let me know when your VM is ready!

