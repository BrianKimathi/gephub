#!/bin/bash

# Generate JWT RSA key pair for Gephub Auth Service
# Usage: ./generate-jwt-keys.sh

echo "Generating JWT RSA key pair..."

# Generate private key
openssl genrsa -out jwt_private.pem 2048

# Generate public key
openssl rsa -in jwt_private.pem -pubout -out jwt_public.pem

# Convert to single line format for .env file
echo ""
echo "Private key (single line):"
cat jwt_private.pem | tr -d '\n' > jwt_private_oneline.txt
cat jwt_private_oneline.txt

echo ""
echo ""
echo "Public key (single line):"
cat jwt_public.pem | tr -d '\n' > jwt_public_oneline.txt
cat jwt_public_oneline.txt

echo ""
echo ""
echo "Keys generated successfully!"
echo "Add these to your .env file:"
echo "GEPHUB_JWT_PUBLIC=$(cat jwt_public_oneline.txt)"
echo "GEPHUB_JWT_PRIVATE=$(cat jwt_private_oneline.txt)"
echo ""
echo "⚠️  Keep these keys secure! Never commit them to git."

