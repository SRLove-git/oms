#!/usr/bin/env bash
set -euo pipefail

# OMS 阶段三 P2 报表冒烟：销售/库存/支付/售后报表接口与 CSV 导出
# 前置：docker compose 已启动、6 个后端服务与网关已启动

GATEWAY="${GATEWAY_URL:-http://localhost:8080}"

FAILED=0

log() { printf '%s\n' "$*"; }
pass() { printf '✅ %s\n' "$*"; }
fail() { printf '❌ %s\n' "$*"; FAILED=1; }

api() {
  local method=$1
  local url=$2
  local token=${3:-}
  local body=${4:-}
  local args=(-s -X "$method" "$url" -H 'Content-Type: application/json')
  [[ -n "$token" ]] && args+=(-H "Authorization: Bearer $token")
  [[ -n "$body" ]] && args+=(-d "$body")
  curl "${args[@]}"
}

json_val() {
  python3 -c "
import json, sys
d = json.load(sys.stdin)
for k in '$1'.strip('.').split('.'):
    if k.isdigit():
        d = d[int(k)]
    else:
        d = d[k]
print(d)
"
}

log "== 1. 登录 =="
ADMIN_TOKEN=$(api POST "$GATEWAY/api/v1/auth/login" "" '{"username":"admin","password":"admin123"}' | json_val data.token)
[[ -n "$ADMIN_TOKEN" ]] && pass "管理员登录成功" || fail "管理员登录失败"

log ""
log "== 2. 报表接口 =="
ENDPOINTS=(
  "reports/sales/summary"
  "reports/sales/trend"
  "reports/sales/source"
  "reports/sales/daily"
  "reports/inventory/warehouse-stock"
  "reports/inventory/stock-summary"
  "reports/inventory/expiry-distribution"
  "reports/inventory/turnover"
  "reports/inventory/slow-moving"
  "reports/payments/channel-stats"
  "reports/payments/reconciliation-stats"
  "reports/aftersales/type-stats"
  "reports/aftersales/reason-distribution"
  "reports/aftersales/repair-duration"
  "reports/aftersales/return-rate"
)
for ep in "${ENDPOINTS[@]}"; do
  code=$(api GET "$GATEWAY/api/v1/$ep" "$ADMIN_TOKEN" | json_val code)
  if [[ "$code" == "0" ]]; then
    pass "GET /api/v1/$ep"
  else
    fail "GET /api/v1/$ep -> code=$code"
  fi
done

log ""
log "== 3. CSV 导出 =="
for pair in "sales:trend" "inventory:warehouse-stock" "inventory:expiry" "inventory:turnover" "inventory:slow-moving" "payments:channel" "payments:reconciliation" "aftersales:type" "aftersales:reason" "aftersales:repair" "aftersales:return-rate"; do
  service=${pair%%:*}
  type=${pair##*:}
  status=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $ADMIN_TOKEN" \
    "$GATEWAY/api/v1/reports/$service/export?type=$type")
  if [[ "$status" == "200" ]]; then
    pass "导出 $service/$type (HTTP $status)"
  else
    fail "导出 $service/$type (HTTP $status)"
  fi
done

log ""
if [[ "$FAILED" == "0" ]]; then
  printf '✅✅ 阶段三报表冒烟全部通过\n'
else
  printf '❌ 阶段三报表冒烟存在失败项\n'
  exit 1
fi
