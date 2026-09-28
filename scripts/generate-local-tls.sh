#!/bin/sh
set -eu

tls_directory="${1:-../tls-local}"
certificate_file="$tls_directory/fullchain.pem"
private_key_file="$tls_directory/privkey.pem"

# Keep an existing certificate, including one supplied by a local trusted CA.
if [ -f "$certificate_file" ] && [ -f "$private_key_file" ]; then
  openssl x509 -checkend 0 -noout -in "$certificate_file" >/dev/null || {
    echo "Local certificate has expired. Replace the TLS files in $tls_directory." >&2
    exit 1
  }
  exit 0
fi
if [ -e "$certificate_file" ] || [ -e "$private_key_file" ]; then
  echo "Incomplete local TLS pair in $tls_directory; refusing to overwrite it." >&2
  exit 1
fi

umask 077
mkdir -p "$tls_directory"
openssl req -x509 -newkey rsa:2048 -sha256 -nodes -days 365 \
  -subj "/CN=localhost" -addext "subjectAltName=DNS:localhost" \
  -keyout "$private_key_file" -out "$certificate_file" >/dev/null 2>&1
chmod 600 "$private_key_file"
chmod 644 "$certificate_file"
echo "Created a local-only certificate in $tls_directory. Trust it locally to avoid browser warnings."
