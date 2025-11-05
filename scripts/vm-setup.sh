#!/bin/bash

# Ubuntu VM Setup Script for Gephub
# Run this script on your Ubuntu VM to set up everything

set -e

echo "=========================================="
echo "Gephub VM Setup Script"
echo "=========================================="
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if running as root
if [ "$EUID" -eq 0 ]; then 
   echo -e "${RED}Please do not run as root${NC}"
   exit 1
fi

echo -e "${GREEN}Step 1: Updating system...${NC}"
sudo apt update && sudo apt upgrade -y

echo -e "${GREEN}Step 2: Installing essential tools...${NC}"
sudo apt install -y curl wget git vim nano htop ufw

echo -e "${GREEN}Step 3: Setting up firewall...${NC}"
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 8080/tcp
sudo ufw allow 8081/tcp
sudo ufw allow 8082/tcp
sudo ufw allow 8083/tcp
sudo ufw --force enable

echo -e "${GREEN}Step 4: Installing Docker...${NC}"
if ! command -v docker &> /dev/null; then
    curl -fsSL https://get.docker.com -o get-docker.sh
    sudo sh get-docker.sh
    sudo usermod -aG docker $USER
    echo -e "${YELLOW}Docker installed. You may need to log out and back in.${NC}"
else
    echo -e "${GREEN}Docker already installed${NC}"
fi

echo -e "${GREEN}Step 5: Verifying Docker installation...${NC}"
docker --version
docker compose version

echo ""
echo -e "${GREEN}=========================================="
echo "Setup Complete!"
echo "==========================================${NC}"
echo ""
echo "Next steps:"
echo "1. If Docker was just installed, log out and log back in"
echo "2. Clone the repository: git clone https://github.com/YOUR_USERNAME/gephub.git"
echo "3. cd gephub"
echo "4. Generate JWT keys: ./scripts/generate-jwt-keys.sh"
echo "5. Create .env file: cp .env.example .env && nano .env"
echo "6. Start services: docker compose up -d"
echo ""

