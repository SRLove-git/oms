#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT/deploy/.env"
PROFILE_ARGS=""

if [[ "${1:-}" == "--tools" ]]; then
  PROFILE_ARGS="--profile tools"
  echo "启用可选组件（RocketMQ Dashboard）"
fi

if [[ ! -f "$ENV_FILE" ]]; then
  cp "$ROOT/deploy/.env.example" "$ENV_FILE"
  echo "已从 deploy/.env.example 生成 deploy/.env"
fi

docker compose --env-file "$ENV_FILE" -f "$ROOT/deploy/docker-compose.yml" $PROFILE_ARGS up -d
