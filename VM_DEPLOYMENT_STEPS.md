# Ubuntu VM Deployment Steps

## Step 1: Connect to Your Ubuntu VM

**If using VirtualBox:**

- Start your VM
- Login via SSH or directly in VirtualBox

**If using cloud (AWS/DigitalOcean/Azure):**

```bash
ssh username@your-vm-ip
```

## Step 2: Initial System Setup

Run these commands on your Ubuntu VM:

```bash
# Update system
sudo apt update && sudo apt upgrade -y

# Install essential tools
sudo apt install -y curl wget git vim nano htop ufw

# Set up firewall (allow necessary ports)
sudo ufw allow 22/tcp    # SSH
sudo ufw allow 80/tcp    # HTTP
sudo ufw allow 443/tcp   # HTTPS
sudo ufw allow 8080/tcp  # Auth service
sudo ufw allow 8081/tcp  # KYC service
sudo ufw allow 8082/tcp  # Meets service
sudo ufw allow 8083/tcp  # Builder service
sudo ufw enable
```

## Step 3: Install Docker

```bash
# Install Docker using official scriptcurl wget git vim nanohtop ufw

curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# Add your user to docker group (to avoid sudo)
sudo usermod -aG docker $USER

# Log out and log back in for group change to take effect
# Or run: newgrp docker

# Verify installation
docker --version
docker compose version
```

## Step 4: Clone Repository

```bash
# Recommended location: your home folder (no sudo needed)
mkdir -p ~/apps && cd ~/apps

# Clone your repository
git clone https://github.com/YOUR_USERNAME/gephub.git
cd gephub

# Verify files are there
ls -la
```

Note: You can also use `/opt/gephub` if you prefer a system-wide location:

```bash
sudo mkdir -p /opt/gephub && sudo chown -R $USER:$USER /opt/gephub
cd /opt
git clone https://github.com/YOUR_USERNAME/gephub.git
cd gephub
```

## Step 5: Generate JWT Keys

```bash
# Make script executable
chmod +x scripts/generate-jwt-keys.sh

# Run the script
./scripts/generate-jwt-keys.sh

# This will generate:
# - jwt_private.pem
# - jwt_public.pem
# - jwt_private_oneline.txt
# - jwt_public_oneline.txt
```

**Copy the keys from the output** - you'll need them for the .env file.

## Step 6: Create .env File

```bash
# Copy example file
cp .env.example .env

# Edit the file
nano .env
```

**Required changes in .env:**

1. **Database passwords** (change from defaults):

   ```
   DB_PASSWORD=your_secure_password_here
   RABBITMQ_PASSWORD=your_secure_password_here
   ```

2. **JWT keys** (from Step 5):

   ```
   GEPHUB_JWT_PUBLIC=<paste from jwt_public_oneline.txt>
   GEPHUB_JWT_PRIVATE=<paste from jwt_private_oneline.txt>
   ```

3. **Optional API keys** (can add later):
   ```
   GITHUB_ACCESS_TOKEN=  # For builder service
   AI_API_KEY=           # For builder service
   LIVEKIT_API_KEY=      # For meets service
   LIVEKIT_API_SECRET=   # For meets service
   ```

**Save and exit:** `Ctrl+X`, then `Y`, then `Enter`

## Step 7: Start Services

```bash
# Start all services
docker compose up -d

# Watch logs (press Ctrl+C to exit)
docker compose logs -f

# Or watch specific service
docker compose logs -f gephub-auth-service
```

## Step 8: Verify Services

```bash
# Check all containers are running
docker compose ps

# Test health endpoints
curl http://localhost:8080/actuator/health  # Auth service
curl http://localhost:8081/actuator/health  # KYC service
curl http://localhost:8082/actuator/health  # Meets service
curl http://localhost:8083/actuator/health  # Builder service

# Check database
docker compose exec postgres-auth psql -U gephub -d gephub_auth -c "\dt"
```

Database note: You do NOT need to install PostgreSQL on the VM. Docker Compose starts Postgres containers for each service. Connect using `docker compose exec` as above, or from other containers using the service hostname (e.g., `postgres-auth`, `postgres-kyc`, `postgres-meets`, `postgres-builder`) defined in `docker-compose.yml`.

## Step 9: Test Registration

```bash
# Register a test user
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "Test123!",
    "organizationName": "Test Organization"
  }'
```

## Step 10: Access Swagger UI

Open in browser (if you have access to VM's IP):

- Auth: http://YOUR_VM_IP:8080/swagger-ui.html
- KYC: http://YOUR_VM_IP:8081/swagger-ui.html
- Meets: http://YOUR_VM_IP:8082/swagger-ui.html
- Builder: http://YOUR_VM_IP:8083/swagger-ui.html

## Troubleshooting

### Services won't start

```bash
# Check logs
docker compose logs

# Check if ports are in use
sudo netstat -tulpn | grep :8080

# Restart services
docker compose down
docker compose up -d
```

### Database connection errors

```bash
# Check database logs
docker compose logs postgres-auth

# Verify database is running
docker compose ps postgres-auth
```

### Out of memory

```bash
# Check memory usage
free -h

# Add swap if needed
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
```

### Permission denied

```bash
# Add user to docker group (if not done)
sudo usermod -aG docker $USER
newgrp docker
```

## Step 11: Set Up CI/CD Pipeline (Optional but Recommended)

See [CI_CD_SETUP.md](./CI_CD_SETUP.md) for complete CI/CD setup instructions.

**Quick setup:**

1. Generate SSH key for GitHub Actions
2. Add public key to VM: `ssh-copy-id -i ~/.ssh/key.pub user@vm-ip`
3. Add GitHub Secrets:
   - `VM_SSH_PRIVATE_KEY` (private key content)
   - `VM_HOST` (your VM IP)
   - `VM_USER` (SSH username)
4. Push to main branch - deployment will trigger automatically!

## Next Steps After Deployment

1. **Set up API keys** (GitHub, AI, LiveKit) in .env
2. **Configure domain** (if using custom domain)
3. **Set up SSL** (Let's Encrypt)
4. **Configure CI/CD** - See [CI_CD_SETUP.md](./CI_CD_SETUP.md) ✅
5. **Set up monitoring** (logs, metrics)

## Useful Commands

```bash
# Stop all services
docker compose down

# Start services
docker compose up -d

# Restart a service
docker compose restart gephub-auth-service

# View logs
docker compose logs -f [service-name]

# Execute command in container
docker compose exec gephub-auth-service sh

# Backup database
docker compose exec postgres-auth pg_dump -U gephub gephub_auth > backup.sql
```
