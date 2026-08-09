#!/usr/bin/env bash
set -uo pipefail

# OMS 阶段四安全冒烟：认证绕过、越权、注入、畸形输入、敏感端点暴露
# 前置：docker compose 已启动、全部服务与网关已启动

GATEWAY="${GATEWAY_URL:-http://localhost:8080}"
TS=$(date +%H%M%S)
SUFFIX="SEC$TS"

FAILED=0
PASSED=0
log() { printf '%s\n' "$*"; }
pass() { printf '  ✅ %s\n' "$*"; PASSED=$((PASSED + 1)); }
fail() { printf '  ❌ %s\n' "$*"; FAILED=1; }
section() { log ""; log "== $* =="; }

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
http() {
  local url=$1
  shift
  curl -s -o /dev/null -w '%{http_code}' "$url" "$@"
}

log "== OMS 安全冒烟开始 ($TS) =="

section "1. 认证绕过防护"
for u in orders skus warehouses inventories return-orders payments reconciliation reports/sales/summary; do
  code=$(http "$GATEWAY/api/v1/$u?page=1&size=5")
  [[ "$code" == "401" ]] && pass "未认证访问 /$u -> 401" || fail "未认证访问 /$u -> $code"
done

FAKE=$(http "$GATEWAY/api/v1/orders?page=1&size=5" -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.$(python3 -c "import base64,json;print(base64.urlsafe_b64encode(json.dumps({'sub':'admin'}).encode()).rstrip(b'=').decode())").$(python3 -c "print('x'*43)")")
[[ "$FAKE" == "401" ]] && pass "伪造 JWT -> 401" || fail "伪造 JWT -> $FAKE"

section "2. 身份与越权"
ADMIN_TOKEN=$(api POST "$GATEWAY/api/v1/auth/login" "" '{"username":"admin","password":"admin123"}' | json_val data.token)
MERCHANT_TOKEN=$(api POST "$GATEWAY/api/v1/auth/login" "" '{"username":"merchant","password":"merchant123"}' | json_val data.token)
MERCHANT_ID=$(api POST "$GATEWAY/api/v1/auth/login" "" '{"username":"merchant","password":"merchant123"}' | json_val data.user.merchantId)

LOGIN_RESP=$(api POST "$GATEWAY/api/v1/auth/login" "" '{"username":"admin","password":"admin123"}')
if printf '%s' "$LOGIN_RESP" | rg -q '"password"'; then
  fail "登录响应回显密码字段"
else
  pass "登录响应不包含密码字段"
fi

M2_ID=$(api POST "$GATEWAY/api/v1/merchants/register" "" "{\"name\":\"安全测试商户\",\"contactName\":\"t\",\"contactPhone\":\"13800000000\",\"username\":\"sec_merchant_$TS\",\"password\":\"sec123456\"}" | json_val data)
api POST "$GATEWAY/api/v1/merchants/$M2_ID/review" "$ADMIN_TOKEN" '{"approved":true,"reason":"安全测试"}' >/dev/null
M2_TOKEN=$(api POST "$GATEWAY/api/v1/auth/login" "" "{\"username\":\"sec_merchant_$TS\",\"password\":\"sec123456\"}" | json_val data.token)

USER_LIST=$(api GET "$GATEWAY/api/v1/users?page=1&size=5" "$M2_TOKEN")
[[ "$(printf '%s' "$USER_LIST" | json_val code)" == "403" ]] && pass "非管理员访问用户管理 -> 403" || fail "商户越权访问用户管理"

ORDER_NO=$(api GET "$GATEWAY/api/v1/orders?page=1&size=5" "$ADMIN_TOKEN" | json_val data.records.0.orderNo)
OTHER_ORDER=$(api GET "$GATEWAY/api/v1/orders/$ORDER_NO" "$M2_TOKEN")
[[ "$(printf '%s' "$OTHER_ORDER" | json_val code)" == "403" ]] && pass "商户读取他商户订单 -> 403" || fail "商户越权读单 code=$(printf '%s' "$OTHER_ORDER" | json_val code)"

section "3. 注入与畸形输入"
INJ=$(api GET "$GATEWAY/api/v1/skus?keyword=%27%20OR%20%271%27%3D%271&page=1&size=5" "$ADMIN_TOKEN")
[[ "$(printf '%s' "$INJ" | json_val code)" == "0" || "$(printf '%s' "$INJ" | json_val code)" == "400" ]] && pass "SQL 注入探测被安全处理" || fail "SQL 注入探测异常 code=$(printf '%s' "$INJ" | json_val code)"

MAL=$(api POST "$GATEWAY/api/v1/warehouses" "$ADMIN_TOKEN" '{bad json')
[[ "$(printf '%s' "$MAL" | json_val code)" == "400" ]] && pass "畸形 JSON -> 400 统一错误码" || fail "畸形 JSON 处理异常 code=$(printf '%s' "$MAL" | json_val code)"

section "4. 敏感端点与信息泄漏"
ENV_HTTP=$(http "$GATEWAY/actuator/env")
[[ "$ENV_HTTP" == "404" || "$ENV_HTTP" == "401" || "$ENV_HTTP" == "403" ]] && pass "actuator/env 未暴露 (HTTP $ENV_HTTP)" || fail "actuator/env 可访问 (HTTP $ENV_HTTP)"

HEALTH=$(http "$GATEWAY/actuator/health")
[[ "$HEALTH" == "200" ]] && pass "健康检查端点白名单开放" || fail "健康检查异常 HTTP $HEALTH"

USERS_RESP=$(api GET "$GATEWAY/api/v1/users?page=1&size=5" "$ADMIN_TOKEN")
if printf '%s' "$USERS_RESP" | rg -q '"password"'; then
  fail "用户列表响应泄露密码字段"
else
  pass "用户列表响应不含密码字段"
fi

log ""
log "安全冒烟结果：通过 $PASSED 项"
if [[ "$FAILED" == "0" ]]; then
  printf '✅✅ 安全冒烟全部通过\n'
else
  printf '❌ 安全冒烟存在失败项\n'
  exit 1
fi
