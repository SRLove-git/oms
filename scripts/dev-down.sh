#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT/deploy/.env"

if [[ ! -f "$ENV_FILE" ]]; then
  cp "$ROOT/deploy/.env.example" "$ENV_FILE"
fi

docker compose --env-file "$ENV_FILE" -f "$ROOT/deploy/docker-compose.yml" --profile tools down
