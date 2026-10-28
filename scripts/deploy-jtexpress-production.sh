#!/usr/bin/env bash
set -euo pipefail

RELEASE_DIR="${1:?release directory is required}"
SECRETS_FILE="${2:?secrets file is required}"
OMS_ROOT="/opt/oms"
COMPOSE_FILE="$OMS_ROOT/deploy/docker-compose.prod.yml"
ENV_FILE="$OMS_ROOT/.env"
STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP_DIR="$OMS_ROOT/backups/jtexpress-$STAMP"
DEPLOYED=0
BUSINESS_SERVICES=(
  oms-gateway
  user-service
  order-service
  inventory-service
  after-sales-service
  payment-center
  integration-service
  admin-web
)
BUSINESS_CONTAINERS=(
  oms-gateway
  oms-user-service
  oms-order-service
  oms-inventory-service
  oms-after-sales-service
  oms-payment-center
  oms-integration-service
  oms-admin-web
)

compose() {
  if [[ -f "$ENV_FILE" ]]; then
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
  else
    docker compose -f "$COMPOSE_FILE" "$@"
  fi
}

container_env_value() {
  local container="$1"
  local key="$2"
  docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$container" \
    | sed -n "s/^${key}=//p" \
    | head -n 1
}

seed_missing_env() {
  local file="$1"
  local key="$2"
  local container="$3"
  local value
  if grep -q "^${key}=" "$file"; then
    return
  fi
  value="$(container_env_value "$container" "$key")"
  if [[ -n "$value" ]]; then
    printf '%s=%s\n' "$key" "$value" >> "$file"
  fi
}

case "$RELEASE_DIR" in
  "$OMS_ROOT"/releases/*) ;;
  *) echo "release directory must be under $OMS_ROOT/releases" >&2; exit 2 ;;
esac

for file in oms-gateway.jar order-service.jar integration-service.jar admin.tgz docker-compose.prod.yml; do
  test -s "$RELEASE_DIR/$file"
done
test -s "$SECRETS_FILE"

mkdir -p "$BACKUP_DIR/jars" "$BACKUP_DIR/deploy" "$BACKUP_DIR/dist"
cp -a "$OMS_ROOT/jars/oms-gateway.jar" "$BACKUP_DIR/jars/"
cp -a "$OMS_ROOT/jars/order-service.jar" "$BACKUP_DIR/jars/"
cp -a "$OMS_ROOT/jars/integration-service.jar" "$BACKUP_DIR/jars/"
cp -a "$COMPOSE_FILE" "$BACKUP_DIR/deploy/"
if [[ -f "$ENV_FILE" ]]; then
  cp -a "$ENV_FILE" "$BACKUP_DIR/env"
else
  : > "$BACKUP_DIR/env"
fi
seed_missing_env "$BACKUP_DIR/env" OMS_JWT_SECRET oms-user-service
seed_missing_env "$BACKUP_DIR/env" MYSQL_PASSWORD oms-user-service
seed_missing_env "$BACKUP_DIR/env" MYSQL_ROOT_PASSWORD oms-mysql
seed_missing_env "$BACKUP_DIR/env" OMS_OPEN_API_DEMO_MALL_SECRET oms-gateway
cp -a "$OMS_ROOT/dist/admin" "$BACKUP_DIR/dist/admin"
chmod 600 "$BACKUP_DIR/env"
DEPLOYED=1

rollback() {
  if [[ "$DEPLOYED" != "1" ]]; then
    return
  fi
  echo "Deployment failed; restoring $BACKUP_DIR" >&2
  cp -a "$BACKUP_DIR/jars/." "$OMS_ROOT/jars/"
  cp -a "$BACKUP_DIR/deploy/docker-compose.prod.yml" "$COMPOSE_FILE"
  cp -a "$BACKUP_DIR/env" "$ENV_FILE"
  rm -rf "$OMS_ROOT/dist/admin"
  cp -a "$BACKUP_DIR/dist/admin" "$OMS_ROOT/dist/admin"
  compose up -d --no-deps --force-recreate "${BUSINESS_SERVICES[@]}" || true
}
trap rollback ERR

install -m 0644 "$RELEASE_DIR/oms-gateway.jar" "$OMS_ROOT/jars/oms-gateway.jar"
install -m 0644 "$RELEASE_DIR/order-service.jar" "$OMS_ROOT/jars/order-service.jar"
install -m 0644 "$RELEASE_DIR/integration-service.jar" "$OMS_ROOT/jars/integration-service.jar"
install -m 0644 "$RELEASE_DIR/docker-compose.prod.yml" "$COMPOSE_FILE"

NEXT_ENV="$ENV_FILE.next"
cp "$BACKUP_DIR/env" "$NEXT_ENV"
while IFS='=' read -r key value; do
  [[ -n "$key" ]] || continue
  case "$key" in
    OMS_JTEXPRESS_*|OMS_INTERNAL_SERVICE_SECRET|OMS_CALLBACK_SECRET) ;;
    *) echo "unexpected secret key: $key" >&2; exit 3 ;;
  esac
  TEMP_ENV="$NEXT_ENV.tmp"
  grep -v "^${key}=" "$NEXT_ENV" > "$TEMP_ENV" || true
  printf '%s=%s\n' "$key" "$value" >> "$TEMP_ENV"
  mv "$TEMP_ENV" "$NEXT_ENV"
done < "$SECRETS_FILE"
chmod 600 "$NEXT_ENV"
mv "$NEXT_ENV" "$ENV_FILE"

# 启用极兔但没有 webhook secret 时，建单能成功但轨迹永远回不来，直接阻止发布。
if [[ "$(sed -n 's/^OMS_JTEXPRESS_ENABLED=//p' "$ENV_FILE" | head -n 1)" == "true" ]]; then
  JT_WEBHOOK_SECRET="$(sed -n 's/^OMS_JTEXPRESS_WEBHOOK_SECRET=//p' "$ENV_FILE" | head -n 1)"
  if [[ -z "$JT_WEBHOOK_SECRET" ]]; then
    echo "OMS_JTEXPRESS_ENABLED=true requires a non-empty OMS_JTEXPRESS_WEBHOOK_SECRET" >&2
    false
  fi
fi

# OMS order/after-sales outbox callbacks must use the same non-default HMAC
# secret as the storefront callback endpoint.
OMS_CALLBACK_SECRET="$(sed -n 's/^OMS_CALLBACK_SECRET=//p' "$ENV_FILE" | head -n 1)"
if [[ -z "$OMS_CALLBACK_SECRET" || "$OMS_CALLBACK_SECRET" == "demo-mall-callback-secret-change-me" ]]; then
  echo "OMS_CALLBACK_SECRET must be set to a non-default value" >&2
  false
fi

NEW_ADMIN="$OMS_ROOT/dist/admin.new-$STAMP"
mkdir -p "$NEW_ADMIN"
tar -xzf "$RELEASE_DIR/admin.tgz" -C "$NEW_ADMIN"
rm -rf "$OMS_ROOT/dist/admin"
mv "$NEW_ADMIN" "$OMS_ROOT/dist/admin"

compose config >/dev/null
compose up -d --no-deps --force-recreate "${BUSINESS_SERVICES[@]}"

wait_healthy() {
  local container="$1"
  local status
  for _ in $(seq 1 36); do
    status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container")"
    if [[ "$status" == "healthy" || "$status" == "running" ]]; then
      return 0
    fi
    if [[ "$status" == "unhealthy" || "$status" == "exited" || "$status" == "dead" ]]; then
      echo "$container entered state $status" >&2
      return 1
    fi
    sleep 5
  done
  echo "$container did not become healthy in time" >&2
  return 1
}

wait_healthy oms-gateway
wait_healthy oms-user-service
wait_healthy oms-order-service
wait_healthy oms-inventory-service
wait_healthy oms-after-sales-service
wait_healthy oms-payment-center
wait_healthy oms-integration-service
wait_healthy oms-admin-web

EXPECTED_JWT_SECRET="$(sed -n 's/^OMS_JWT_SECRET=//p' "$ENV_FILE" | head -n 1)"
EXPECTED_INTERNAL_SECRET="$(sed -n 's/^OMS_INTERNAL_SERVICE_SECRET=//p' "$ENV_FILE" | head -n 1)"
EXPECTED_CALLBACK_SECRET="$(sed -n 's/^OMS_CALLBACK_SECRET=//p' "$ENV_FILE" | head -n 1)"
test -n "$EXPECTED_JWT_SECRET"
test -n "$EXPECTED_INTERNAL_SECRET"
test -n "$EXPECTED_CALLBACK_SECRET"
for container in "${BUSINESS_CONTAINERS[@]:0:7}"; do
  test "$(container_env_value "$container" OMS_JWT_SECRET)" = "$EXPECTED_JWT_SECRET"
  test "$(container_env_value "$container" OMS_INTERNAL_SERVICE_SECRET)" = "$EXPECTED_INTERNAL_SECRET"
done
for container in oms-order-service oms-after-sales-service; do
  test "$(container_env_value "$container" OMS_CALLBACK_SECRET)" = "$EXPECTED_CALLBACK_SECRET"
done

VERIFY_RESULT="$(docker exec oms-integration-service wget -qO- \
  --header='X-User-Type: 1' \
  'http://127.0.0.1:8086/api/v1/logistics/jtexpress/verify?serviceCode=PUDO')"
printf '%s\n' "$VERIFY_RESULT"
grep -q '"connected":true' <<< "$VERIFY_RESULT"

STATUS_RESULT="$(docker exec oms-integration-service wget -qO- \
  --header='X-User-Type: 1' \
  'http://127.0.0.1:8086/api/v1/logistics/jtexpress/status')"
printf '%s\n' "$STATUS_RESULT"
grep -q '"webhookConfigured":true' <<< "$STATUS_RESULT"
compose ps "${BUSINESS_SERVICES[@]}"

rm -f "$SECRETS_FILE"
trap - ERR
echo "Deployment completed. Backup: $BACKUP_DIR"
