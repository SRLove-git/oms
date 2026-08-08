#!/usr/bin/env bash
set -euo pipefail

# OMS 阶段一 P0 端到端冒烟：下单 → 支付 → 审核 → 发货 → 签收 → 完成
# 前置：docker compose 已启动、6 个后端服务已启动（见 backend/README.md）

GATEWAY="${GATEWAY_URL:-http://localhost:8080}"
USER_SERVICE="${USER_SERVICE_URL:-http://localhost:8081}"
ORDER_SERVICE="${ORDER_SERVICE_URL:-http://localhost:8082}"
INVENTORY_SERVICE="${INVENTORY_SERVICE_URL:-http://localhost:8083}"
PAYMENT_SERVICE="${PAYMENT_SERVICE_URL:-http://localhost:8085}"

FAILED=0

log() { printf '%s\n' "$*"; }
pass() { printf '✅ %s\n' "$*"; }
fail() { printf '❌ %s\n' "$*"; FAILED=1; }

curl_json() {
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

api() { # method path token body -> data 字段
  local method=$1 path=$2 token=${3:-} body=${4:-}
  local out
  out=$(curl_json "$method" "$GATEWAY$path" "$token" "$body")
  local code
  code=$(printf '%s' "$out" | json_val code)
  if [[ "$code" != "0" ]]; then
    printf 'API 调用失败: %s %s -> %s\n' "$method" "$path" "$out" >&2
    return 1
  fi
  printf '%s' "$out"
}

wait_health() {
  local url=$1 name=$2
  for _ in $(seq 1 60); do
    if curl -s "$url/actuator/health" | grep -q '"UP"'; then
      pass "$name 已就绪"
      return 0
    fi
    sleep 2
  done
  fail "$name 未就绪"
  return 1
}

log '== 1. 等待服务就绪 =='
wait_health "$GATEWAY" "网关" || exit 1
wait_health "$USER_SERVICE" "用户服务" || exit 1
wait_health "$ORDER_SERVICE" "订单服务" || exit 1
wait_health "$INVENTORY_SERVICE" "库存服务" || exit 1
wait_health "$PAYMENT_SERVICE" "支付中心" || exit 1

log ''
log '== 2. 登录 =='
ADMIN_TOKEN=$(api POST /api/v1/auth/login "" '{"username":"admin","password":"admin123"}' | json_val data.token)
pass "管理员登录成功"

MERCHANT_LOGIN=$(curl_json POST "$GATEWAY/api/v1/auth/login" "" '{"username":"merchant","password":"merchant123"}')
if [[ "$(printf '%s' "$MERCHANT_LOGIN" | json_val code)" != "0" ]]; then
  MERCHANT_ID=$(api POST /api/v1/merchants/register "" '{"name":"冒烟测试商户","contactName":"测试","contactPhone":"13900000000","username":"smoke_merchant","password":"smoke123"}' | json_val data)
  api POST "/api/v1/merchants/$MERCHANT_ID/review" "$ADMIN_TOKEN" '{"approved":true,"reason":"冒烟测试"}'
  MERCHANT_LOGIN=$(curl_json POST "$GATEWAY/api/v1/auth/login" "" '{"username":"smoke_merchant","password":"smoke123"}')
fi
MERCHANT_TOKEN=$(printf '%s' "$MERCHANT_LOGIN" | json_val data.token)
MERCHANT_ID=$(printf '%s' "$MERCHANT_LOGIN" | json_val data.user.merchantId)
pass "商户登录成功 (merchantId=$MERCHANT_ID)"

log ''
log '== 3. 准备商品与库存 =='
WAREHOUSE_ID=$(api POST /api/v1/warehouses "$ADMIN_TOKEN" '{"code":"WH-SMOKE","name":"冒烟仓库","address":"测试地址"}' | json_val data)
SKU_ID=$(api POST /api/v1/skus "$ADMIN_TOKEN" '{"spuNo":"SPU-SMOKE","spuName":"冒烟商品","skuNo":"SKU-SMOKE","name":"冒烟测试器械","spec":"标准","price":199.50,"udi":"UDI-SMOKE","registrationNo":"REG-001"}' | json_val data)
api POST /api/v1/inventories/inbound "$ADMIN_TOKEN" "{\"warehouseId\":$WAREHOUSE_ID,\"skuId\":$SKU_ID,\"quantity\":100,\"batchNo\":\"B20260808\",\"expireAt\":\"2029-08-08\"}" >/dev/null
pass "已创建仓库/商品并入库 100 件"

log ''
log '== 4. 下单（预占库存） =='
ORDER_RESP=$(api POST /api/v1/orders "$MERCHANT_TOKEN" "{\"merchantId\":$MERCHANT_ID,\"orderType\":1,\"remark\":\"e2e-smoke\",\"items\":[{\"skuId\":$SKU_ID,\"quantity\":2}]}")
ORDER_NO=$(printf '%s' "$ORDER_RESP" | json_val data.orderNo)
ORDER_STATUS=$(printf '%s' "$ORDER_RESP" | json_val data.status)
[[ "$ORDER_STATUS" == "1" ]] && pass "订单已创建: $ORDER_NO (待支付)" || fail "订单状态异常: $ORDER_STATUS"

INV=$(curl_json GET "$INVENTORY_SERVICE/api/v1/inventories?skuId=$SKU_ID&page=1&size=5" "$ADMIN_TOKEN")
RESERVED=$(printf '%s' "$INV" | json_val data.records.0.reservedQuantity)
AVAILABLE=$(printf '%s' "$INV" | json_val data.records.0.quantity)
[[ "$RESERVED" == "2" ]] && pass "库存预占=2, 可用=$AVAILABLE" || fail "预占异常: reserved=$RESERVED available=$AVAILABLE"

log ''
log '== 5. 模拟支付与回调 =='
PAY_RESP=$(api POST "/api/v1/orders/$ORDER_NO/pay" "$MERCHANT_TOKEN" '{"channel":"mock"}')
PAYMENT_NO=$(printf '%s' "$PAY_RESP" | json_val data.paymentNo)
PAY_AMOUNT=$(printf '%s' "$PAY_RESP" | json_val data.amount)
PAY_CALLBACK=$(curl_json POST "$PAYMENT_SERVICE/api/v1/payment-callbacks/mock" "" "{\"paymentNo\":\"$PAYMENT_NO\",\"channelTxnNo\":\"TXN-$(date +%s)\",\"amount\":$PAY_AMOUNT,\"status\":\"SUCCESS\"}")
[[ "$(printf '%s' "$PAY_CALLBACK" | json_val code)" == "0" ]] && pass "支付回调成功" || fail "支付回调失败: $PAY_CALLBACK"
sleep 1
ORDER_AFTER_PAY=$(api GET "/api/v1/orders/$ORDER_NO" "$MERCHANT_TOKEN")
[[ "$(printf '%s' "$ORDER_AFTER_PAY" | json_val data.status)" == "2" ]] && pass "订单已支付" || fail "订单未变为已支付"

INV=$(curl_json GET "$INVENTORY_SERVICE/api/v1/inventories?skuId=$SKU_ID&page=1&size=5" "$ADMIN_TOKEN")
RESERVED=$(printf '%s' "$INV" | json_val data.records.0.reservedQuantity)
AVAILABLE=$(printf '%s' "$INV" | json_val data.records.0.quantity)
[[ "$RESERVED" == "0" ]] && pass "支付后库存已物理扣减 (可用=$AVAILABLE)" || fail "扣减异常: reserved=$RESERVED"

log ''
log '== 6. 审核 → 发货 → 签收 → 完成 =='
api POST "/api/v1/orders/$ORDER_NO/audit" "$ADMIN_TOKEN" >/dev/null && pass "审核通过"
api POST "/api/v1/orders/$ORDER_NO/ship" "$ADMIN_TOKEN" '{"trackingNo":"SF-SMOKE-001","carrier":"SF"}' >/dev/null && pass "已发货"
api POST "/api/v1/orders/$ORDER_NO/sign" "$MERCHANT_TOKEN" >/dev/null && pass "已签收"
api POST "/api/v1/orders/$ORDER_NO/complete" "$ADMIN_TOKEN" >/dev/null && pass "已完成"

FINAL=$(api GET "/api/v1/orders/$ORDER_NO" "$MERCHANT_TOKEN")
[[ "$(printf '%s' "$FINAL" | json_val data.status)" == "6" ]] && pass "订单最终状态：已完成" || fail "最终状态异常"

log ''
log '== 7. 负向用例：库存不足拦截 =='
BIG_ORDER=$(curl_json POST "$GATEWAY/api/v1/orders" "$MERCHANT_TOKEN" "{\"merchantId\":$MERCHANT_ID,\"orderType\":1,\"items\":[{\"skuId\":$SKU_ID,\"quantity\":9999}]}")
if [[ "$(printf '%s' "$BIG_ORDER" | json_val code)" != "0" ]]; then
  pass "超量下单已被拦截：$(printf '%s' "$BIG_ORDER" | json_val message)"
else
  fail "超量下单未被拦截"
fi

log ''
if [[ "$FAILED" == "0" ]]; then
  log '✅✅ 阶段一 P0 端到端冒烟全部通过'
else
  log '❌ 冒烟存在失败项，请查看上方日志'
  exit 1
fi
