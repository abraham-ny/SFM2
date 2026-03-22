#!/usr/bin/env bash
# generate-keystore.sh
#
# Generates a new release keystore for SceneFind and prints the base64 value
# you need to paste into your GitHub Secret KEYSTORE_BASE64.
#
# Usage:
#   chmod +x generate-keystore.sh
#   ./generate-keystore.sh
#
# Requirements:
#   keytool  (ships with any JDK — run: which keytool)
#   base64   (standard on Linux/macOS)

set -euo pipefail

# ── Configuration ─────────────────────────────────────────────────────────────
KEYSTORE_FILE="release.jks"
KEY_ALIAS="sfm-release"
VALIDITY_DAYS=10000          # ~27 years
KEY_SIZE=2048
DNAME="CN=SceneFind,OU=Mobile,O=Vitalsoft,L=Unknown,ST=Unknown,C=US"

# ── Prompt for passwords ───────────────────────────────────────────────────────
echo ""
echo "═══════════════════════════════════════════════════"
echo "  SceneFind — Release Keystore Generator"
echo "═══════════════════════════════════════════════════"
echo ""

read -s -p "Enter store password (min 6 chars): " STORE_PASS; echo
read -s -p "Confirm store password:              " STORE_PASS2; echo

if [ "$STORE_PASS" != "$STORE_PASS2" ]; then
    echo "❌  Passwords do not match. Aborting."
    exit 1
fi

if [ ${#STORE_PASS} -lt 6 ]; then
    echo "❌  Password must be at least 6 characters. Aborting."
    exit 1
fi

read -s -p "Enter key password (leave blank = same as store): " KEY_PASS; echo
if [ -z "$KEY_PASS" ]; then
    KEY_PASS="$STORE_PASS"
fi

# ── Generate keystore ─────────────────────────────────────────────────────────
echo ""
echo "⏳  Generating keystore..."

keytool -genkeypair \
    -keystore "$KEYSTORE_FILE" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize "$KEY_SIZE" \
    -validity "$VALIDITY_DAYS" \
    -storepass "$STORE_PASS" \
    -keypass "$KEY_PASS" \
    -dname "$DNAME" \
    -storetype JKS 2>/dev/null

echo "✅  Keystore created: $KEYSTORE_FILE"
echo ""

# ── Encode to base64 ──────────────────────────────────────────────────────────
B64=$(base64 -w 0 "$KEYSTORE_FILE" 2>/dev/null || base64 "$KEYSTORE_FILE")

# ── Print summary ─────────────────────────────────────────────────────────────
echo "═══════════════════════════════════════════════════"
echo "  Add these 4 secrets to your GitHub repository:"
echo "  Settings → Secrets and variables → Actions → New repository secret"
echo "═══════════════════════════════════════════════════"
echo ""
echo "Secret name:  KEYSTORE_BASE64"
echo "Secret value: (see keystore_base64.txt)"
echo ""
echo "Secret name:  KEYSTORE_PASSWORD"
echo "Secret value: $STORE_PASS"
echo ""
echo "Secret name:  KEY_ALIAS"
echo "Secret value: $KEY_ALIAS"
echo ""
echo "Secret name:  KEY_PASSWORD"
echo "Secret value: $KEY_PASS"
echo ""

# Write base64 to a file so you can copy it easily
echo "$B64" > keystore_base64.txt
echo "📄  Base64 keystore written to: keystore_base64.txt"
echo "    Copy the full contents of that file as the KEYSTORE_BASE64 secret value."
echo ""

# Write keystore.properties for local builds
cat > app/keystore.properties <<EOF
storeFile=../release.jks
storePassword=$STORE_PASS
keyAlias=$KEY_ALIAS
keyPassword=$KEY_PASS
EOF
echo "📄  app/keystore.properties written for local builds."
echo ""
echo "⚠️   IMPORTANT:"
echo "    - Keep $KEYSTORE_FILE and app/keystore.properties OUT of git."
echo "    - Both are already in .gitignore."
echo "    - Back up $KEYSTORE_FILE securely — you cannot re-sign updates without it."
echo ""
echo "═══════════════════════════════════════════════════"
echo "  Done! To trigger a release build:"
echo "    git tag v1.0.0 && git push origin v1.0.0"
echo "═══════════════════════════════════════════════════"
