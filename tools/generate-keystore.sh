#!/bin/bash
# Generate a release keystore, write GitHub secrets to a file, and configure
# local Gradle signing via keystore.properties.
# Usage: ./tools/generate-keystore.sh [alias] [store-pass] [key-pass]

#set -euo pipefail

KEYSTORE_FILE="app/release-keystore.jks"
KEY_ALIAS="${1:-swimgym}"
KEYSTORE_PASSWORD="${2:-$(openssl rand -hex 16)}"
KEY_PASSWORD="${3:-$(openssl rand -hex 16)}"

if [[ ! -d app ]]; then
	echo "run this from app root"
	exit 1
fi
if [[ -f "$KEYSTORE_FILE" ]]; then
	echo "first delete existing keystore in $KEYSTORE_FILE"
	exit 1
fi
keytool -genkeypair \
  -v \
  -dname "CN=SwimGym, OU=SwimGym, O=SwimGym, L=City, ST=State, C=NL" \
  -alias "$KEY_ALIAS" \
  -keypass "$KEY_PASSWORD" \
  -keystore "$KEYSTORE_FILE" \
  -storepass "$KEYSTORE_PASSWORD" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000

BASE64=$(base64 -w0 "$KEYSTORE_FILE")

# Write GitHub secrets to a file
SECRETS_FILE="tools/keystore-secrets.env"
cat > "$SECRETS_FILE" <<EOF
KEYSTORE_BASE64=$BASE64
KEYSTORE_PASSWORD=$KEYSTORE_PASSWORD
KEY_ALIAS=$KEY_ALIAS
KEY_PASSWORD=$KEY_PASSWORD
EOF

chmod 600 "$SECRETS_FILE"

echo "=== GitHub secrets written to: $SECRETS_FILE ==="
echo ""
echo "On GitHub, add the contents of $SECRETS_FILE as repository secrets."
echo "(Or run: for line in \$(cat $SECRETS_FILE); do gh secret set \$line; done)"
echo ""
echo "=== Local signing configured: app/keystore.properties ==="

# Write local keystore.properties for Gradle signing
cat > "keystore.properties" <<EOF
storeFile=app/release-keystore.jks
storePassword=$KEYSTORE_PASSWORD
keyAlias=$KEY_ALIAS
#keyPassword=$KEY_PASSWORD
EOF

chmod 600 "keystore.properties"
echo "Add keystore.properties to .gitignore."
echo ""
echo "=== Keystore file created at: $KEYSTORE_FILE ==="
echo "Add it to .gitignore to avoid committing it."
