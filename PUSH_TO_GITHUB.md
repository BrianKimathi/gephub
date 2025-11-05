# Push to GitHub - Quick Guide

## Step 1: Add Remote Repository

Replace `YOUR_USERNAME` with your GitHub username:

```bash
git remote add origin https://github.com/YOUR_USERNAME/gephub.git
```

Or if you prefer SSH:
```bash
git remote add origin git@github.com:YOUR_USERNAME/gephub.git
```

## Step 2: Push to GitHub

```bash
git push -u origin main
```

If you get an error about authentication, you may need to:
- Use a Personal Access Token instead of password
- Or set up SSH keys

## Verify

After pushing, check your GitHub repository - you should see all 126 files!

