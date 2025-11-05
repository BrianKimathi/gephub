# CI/CD Pipeline Setup Guide

This guide covers setting up GitHub Actions for continuous integration and deployment.

## Overview

The CI/CD pipeline includes:
- **CI (Continuous Integration)**: Build and test each service on push/PR
- **CD (Continuous Deployment)**: Automatically deploy to VM on push to main

## Pipeline Structure

```
.github/workflows/
├── ci-auth.yml          # Build and test auth service
├── ci-kyc.yml           # Build and test KYC service
├── ci-meets.yml         # Build and test Meets service
├── ci-builder.yml       # Build and test Builder service
├── ci-kyc-worker.yml    # Build KYC worker (Python)
└── cd-deploy.yml        # Deploy all services to VM
```

## CI Workflows (Continuous Integration)

### What They Do

Each CI workflow:
1. Triggers on push/PR to `main` or `develop` branches
2. Only runs when relevant service files change
3. Sets up JDK 21 (or Python 3.11 for worker)
4. Builds the service with Maven
5. Runs tests
6. Builds Docker image

### Services Covered

- ✅ `ci-auth.yml` - Auth service
- ✅ `ci-kyc.yml` - KYC service
- ✅ `ci-meets.yml` - Meets service
- ✅ `ci-builder.yml` - Builder service
- ✅ `ci-kyc-worker.yml` - KYC Python worker

### How It Works

```yaml
# Example: Only runs when auth-service files change
on:
  push:
    paths:
      - 'gephub-auth-service/**'
```

## CD Workflow (Continuous Deployment)

### What It Does

The `cd-deploy.yml` workflow:
1. Triggers on push to `main` branch
2. Connects to your VM via SSH
3. Pulls latest code
4. Rebuilds and restarts Docker containers
5. Performs health checks

### Prerequisites

You need to set up GitHub Secrets:

1. **VM_SSH_PRIVATE_KEY**: SSH private key for VM access
2. **VM_HOST**: Your VM's IP address or hostname
3. **VM_USER**: SSH username (usually `ubuntu` or your username)

## Setup Instructions

### Step 1: Generate SSH Key Pair

On your local machine:

```bash
# Generate SSH key pair (if you don't have one)
ssh-keygen -t ed25519 -C "github-actions" -f ~/.ssh/gephub_vm_deploy

# This creates:
# - ~/.ssh/gephub_vm_deploy (private key)
# - ~/.ssh/gephub_vm_deploy.pub (public key)
```

### Step 2: Add Public Key to VM

```bash
# Copy public key to VM
ssh-copy-id -i ~/.ssh/gephub_vm_deploy.pub username@your-vm-ip

# Or manually:
cat ~/.ssh/gephub_vm_deploy.pub | ssh username@your-vm-ip "mkdir -p ~/.ssh && cat >> ~/.ssh/authorized_keys"
```

### Step 3: Configure GitHub Secrets

1. Go to your GitHub repository
2. Click **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret**
4. Add these secrets:

   **VM_SSH_PRIVATE_KEY:**
   ```bash
   # Copy the entire private key (including BEGIN/END lines)
   cat ~/.ssh/gephub_vm_deploy
   ```
   Paste everything into GitHub Secret

   **VM_HOST:**
   ```
   your-vm-ip-address
   # Or: your-domain.com
   ```

   **VM_USER:**
   ```
   ubuntu
   # Or your username
   ```

### Step 4: Test the Pipeline

1. **Test CI (no secrets needed):**
   ```bash
   # Make a small change to any service
   echo "# Test" >> gephub-auth-service/README.md
   git add .
   git commit -m "Test CI pipeline"
   git push
   ```
   Check GitHub Actions tab to see CI run

2. **Test CD (after setting secrets):**
   ```bash
   # Push to main branch
   git checkout main
   git merge develop  # or just push directly
   git push origin main
   ```
   Check Actions tab to see deployment

## Manual Deployment Trigger

You can also trigger deployment manually:

1. Go to **Actions** tab in GitHub
2. Select **CD - Deploy to VM**
3. Click **Run workflow**
4. Select branch and click **Run workflow**

## Pipeline Flow

```
Developer pushes code
    ↓
GitHub Actions triggers CI workflows
    ↓
Build and test each changed service
    ↓
If all tests pass AND push to main:
    ↓
CD workflow triggers
    ↓
SSH to VM
    ↓
Pull latest code
    ↓
Rebuild Docker images
    ↓
Restart services
    ↓
Health checks
    ↓
✅ Deployment complete
```

## Customizing the Pipeline

### Change Deployment Branch

Edit `.github/workflows/cd-deploy.yml`:
```yaml
on:
  push:
    branches: [ production ]  # Change from 'main'
```

### Add More Health Checks

Edit `cd-deploy.yml`:
```yaml
- name: Extended health check
  run: |
    curl -f http://${{ secrets.VM_HOST }}:8080/api/v1/auth/register || exit 1
```

### Skip Deployment for Certain Changes

Edit `cd-deploy.yml`:
```yaml
on:
  push:
    branches: [ main ]
    paths-ignore:
      - '**.md'
      - 'docs/**'
      - 'scripts/**'
```

## Troubleshooting

### CI Pipeline Fails

**Build fails:**
- Check Maven dependencies
- Verify JDK 21 is available
- Check test logs

**Docker build fails:**
- Verify Dockerfile exists
- Check Dockerfile syntax

### CD Pipeline Fails

**SSH connection fails:**
- Verify SSH key is correct in GitHub Secrets
- Check VM firewall allows SSH (port 22)
- Test SSH manually: `ssh username@vm-ip`

**Deployment fails:**
- Check VM has Docker installed
- Verify `docker compose` command works on VM
- Check VM disk space: `df -h`

**Health checks fail:**
- Services may need time to start (increase sleep)
- Check service logs: `docker compose logs`
- Verify ports are accessible

### Debugging

**View workflow logs:**
1. Go to **Actions** tab
2. Click on failed workflow
3. Click on failed job
4. Expand failed step to see logs

**Test manually on VM:**
```bash
ssh username@vm-ip
cd gephub
git pull origin main
docker compose down
docker compose up -d --build
docker compose ps
docker compose logs -f
```

## Advanced: Multi-Environment Deployment

### Staging Environment

Create `.github/workflows/cd-deploy-staging.yml`:
```yaml
name: CD - Deploy to Staging

on:
  push:
    branches: [ develop ]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      # Same as production but different VM_HOST secret
      - name: Deploy to VM
        run: |
          ssh ${{ secrets.STAGING_VM_USER }}@${{ secrets.STAGING_VM_HOST }} ...
```

### Production Environment

Keep `cd-deploy.yml` for production (main branch).

## Security Best Practices

1. **Never commit secrets** - Use GitHub Secrets only
2. **Rotate SSH keys** - Regularly regenerate and update
3. **Limit SSH access** - Use key-based auth, disable password
4. **Monitor deployments** - Review logs regularly
5. **Use branch protection** - Require PR reviews for main

## Monitoring Deployments

### GitHub Actions

- View all runs in **Actions** tab
- Set up notifications for failed deployments
- Add badges to README:

```markdown
![CI](https://github.com/YOUR_USERNAME/gephub/workflows/CI/badge.svg)
![CD](https://github.com/YOUR_USERNAME/gephub/workflows/CD/badge.svg)
```

### On VM

```bash
# Check last deployment
docker compose ps

# View logs
docker compose logs --tail=100 -f

# Check service health
curl http://localhost:8080/actuator/health
```

## Next Steps

1. ✅ Set up GitHub Secrets
2. ✅ Test CI pipeline
3. ✅ Test CD pipeline
4. ✅ Monitor first deployment
5. ✅ Set up notifications
6. ✅ Configure branch protection rules

---

**Ready to set up CI/CD?** Follow Step 1-4 above to configure GitHub Secrets and test the pipeline!

