#!/bin/sh
set -eu

secrets_file="${1:-../secrets.txt}"

if [ -e "$secrets_file" ] || [ -L "$secrets_file" ]; then
  echo "Refusing to overwrite existing secrets file: $secrets_file" >&2
  exit 1
fi

umask 077
{
  printf '%s\n' '# Lumina production runtime secrets. Keep this file outside the repository.'
  printf '%s\n' '# Replace AI_ENGINE_API_KEY with a real key from the approved provider.'
  printf 'POSTGRES_PW='; openssl rand -hex 32
  printf 'JWT_SECRET='; openssl rand -hex 48
  printf 'LEARNING_MARKING_SYNC_KEY='; openssl rand -hex 32
  printf 'BOOTSTRAP_TUTOR_PASSWORD=Lumina!'; openssl rand -hex 24
  printf '%s\n' 'BOOTSTRAP_TUTOR_EMAIL=admin@lumina.sg'
  printf '%s\n' 'BOOTSTRAP_TUTOR_FULL_NAME="Lumina Administrator"'
  printf '%s\n' 'AI_ENGINE_API_KEY=REPLACE_WITH_AN_APPROVED_AI_PROVIDER_KEY'
} > "$secrets_file"

chmod 600 "$secrets_file"
echo "Created $secrets_file with owner-only permissions."
