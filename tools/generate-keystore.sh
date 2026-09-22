#!/bin/bash
# Generate a release keystore and print the secrets to add to GitHub.
# Usage: ./tools/generate-keystore.sh

set -euo pipefail

KEYSTORE_FILE="app/release-keystore.jks"
KEY_ALIAS="${1:-swimgym}"
KEYSTORE_PASSWORD="${2:-$(openssl rand -base64 16)}"
KEY_PASSWORD="${3:-$(openssl rand -base64 16)}"
STORE_TYPE="JKS"

mkdir -p app

keytool -genkeypair \
  -v \
  -dname "CN=SwimGym, OU=SwimGym, O=SwimGym, L=City, ST=State, C=US" \
  -alias "$KEY_ALIAS" \
  -keypass "$KEY_PASSWORD" \
  -keystore "$KEYSTORE_FILE" \
  -storepass "$KEYSTORE_PASSWORD" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000

BASE64=$(base64 -w0 "$KEYSTORE_FILE")

echo "=== Add these to GitHub Secrets ==="
echo ""
echo "KEYSTORE_BASE64=$BASE64"
echo "KEYSTORE_PASSWORD=$KEYSTORE_PASSWORD"
echo "KEY_ALIAS=$KEY_ALIAS"
echo "KEY_PASSWORD=$KEY_PASSWORD"
echo ""
echo "=== Keystore file created at: $KEYSTORE_FILE ==="
echo "Add it to .gitignore to avoid committing it."
