#!/bin/sh
set -eu

tls_directory="${1:-../tls}"
certificate_file="$tls_directory/fullchain.pem"
private_key_file="$tls_directory/privkey.pem"

if [ -e "$certificate_file" ] || [ -L "$certificate_file" ] || \
  [ -e "$private_key_file" ] || [ -L "$private_key_file" ]; then
  echo "Refusing to overwrite existing VM TLS files in: $tls_directory" >&2
  exit 1
fi

umask 077
mkdir -p "$tls_directory"
openssl req -x509 -newkey rsa:2048 -sha256 -nodes -days 30 \
  -subj "/CN=lumina.sg" \
  -addext "subjectAltName=DNS:lumina.sg" \
  -keyout "$private_key_file" \
  -out "$certificate_file" >/dev/null 2>&1
chmod 600 "$private_key_file"
chmod 644 "$certificate_file"
echo "Created 30-day self-signed VM certificate for lumina.sg in $tls_directory."
