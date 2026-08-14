#!/usr/bin/env bash
set -euo pipefail

# OMS 商城开放 API 冒烟：验签 → 商品/库存查询 → 下单(幂等) → 查单 → 取消
# 前置：网关与 order/inventory 服务已启动；默认客户端 demo-mall（见 docs/open-api.md）
# 用法：GATEWAY_URL=http://localhost:8080 ./scripts/open-api-smoke.sh

GATEWAY="${GATEWAY_URL:-http://localhost:8080}"
APP_ID="${OPEN_API_APP_ID:-demo-mall}"
SECRET="${OPEN_API_SECRET:-demo-mall-secret-change-me}"

FAILED=0
log() { printf '%s\n' "$*"; }
pass() { printf '✅ %s\n' "$*"; }
fail() { printf '❌ %s\n' "$*"; FAILED=1; }

sha256_hex() { printf '%s' "$1" | openssl dgst -sha256 -hex | awk '{print $2}'; }

# 开放 API 调用：按 docs/open-api.md 签名方案逐请求现算签名
open_api() { # method path body -> 原始响应
  local method=$1 path=$2 body=${3:-}
  local ts nonce sign
  ts=$(date +%s)
  nonce=$(openssl rand -hex 8)
  sign=$(printf '%s\n%s\n%s\n%s\n%s' \
    "$method" "$path" "$ts" "$nonce" "$(sha256_hex "$body")" \
    | openssl dgst -sha256 -hmac "$SECRET" -hex | awk '{print $2}')
  local args=(
    -s -X "$method" "$GATEWAY$path" -H 'Content-Type: application/json'
    -H "X-App-Id: $APP_ID" -H "X-Timestamp: $ts" -H "X-Nonce: $nonce" -H "X-Sign: $sign"
  )
  [[ -n "$body" ]] && args+=(-d "$body")
  curl "${args[@]}"
}

json_val() {
  python3 -c "
import json, sys
d = json.load(sys.stdin)
for k in '$1'.strip('.').split('.'):
    if isinstance(d, dict) and k in d:
        d = d[k]
    elif isinstance(d, list) and k.isdigit() and int(k) < len(d):
        d = d[int(k)]
    else:
        sys.exit(1)
print(d)
"
}

log '== 1. 等待网关就绪 =='
for _ in $(seq 1 60); do
  if curl -s "$GATEWAY/actuator/health" | grep -q '"UP"'; then
    pass '网关已就绪'
    break
  fi
  sleep 2
done

log ''
log '== 2. 商品与库存查询 =='
PRODUCTS=$(open_api GET '/api/v1/open/products?page=1&size=5')
if printf '%s' "$PRODUCTS" | json_val code | grep -q '^0$'; then
  pass '商品列表查询成功'
else
  fail "商品列表查询失败: $PRODUCTS"
fi

SKU_ID=$(printf '%s' "$PRODUCTS" | json_val 'data.records.0.skuId' 2>/dev/null || printf '')
if [[ -z "$SKU_ID" || "$SKU_ID" == "None" ]]; then
  log '⚠️  无在售商品，跳过商品详情/库存/下单步骤'
  SKU_ID=""
else
  DETAIL=$(open_api GET "/api/v1/open/products/$SKU_ID")
  printf '%s' "$DETAIL" | json_val code | grep -q '^0$' && pass '商品详情查询成功' || fail "商品详情查询失败: $DETAIL"

  STOCK=$(open_api GET "/api/v1/open/skus/$SKU_ID/stock")
  printf '%s' "$STOCK" | json_val code | grep -q '^0$' && pass '库存查询成功' || fail "库存查询失败: $STOCK"
fi

log ''
log '== 3. 下单（幂等）=='
EXTERNAL_NO="MALL-$(date +%s)"
ORDER_BODY=$(printf '{"externalOrderNo":"%s","orderType":2,"remark":"开放 API 冒烟","items":[{"skuId":%s,"quantity":1}]}' "$EXTERNAL_NO" "${SKU_ID:-1}")

if [[ -n "$SKU_ID" ]]; then
  FIRST=$(open_api POST '/api/v1/open/orders' "$ORDER_BODY")
  ORDER_NO_1=$(printf '%s' "$FIRST" | json_val data.orderNo 2>/dev/null || printf '')
  if printf '%s' "$FIRST" | json_val code | grep -q '^0$'; then
    pass "下单成功 orderNo=$ORDER_NO_1"
  else
    fail "下单失败: $FIRST"
  fi

  SECOND=$(open_api POST '/api/v1/open/orders' "$ORDER_BODY")
  ORDER_NO_2=$(printf '%s' "$SECOND" | json_val data.orderNo 2>/dev/null || printf '')
  if [[ -n "$ORDER_NO_1" && "$ORDER_NO_2" == "$ORDER_NO_1" ]]; then
    pass '重复提交幂等（返回同一订单）'
  else
    fail "幂等校验失败: 首次=$ORDER_NO_1 二次=$ORDER_NO_2"
  fi

  log ''
  log '== 4. 查单与取消 =='
  QUERY=$(open_api GET "/api/v1/open/orders/$EXTERNAL_NO")
  if printf '%s' "$QUERY" | json_val code | grep -q '^0$'; then
    pass '按外部订单号查单成功'
  else
    fail "查单失败: $QUERY"
  fi

  CANCEL=$(open_api POST "/api/v1/open/orders/$EXTERNAL_NO/cancel" '{}')
  if printf '%s' "$CANCEL" | json_val code | grep -q '^0$'; then
    pass '取消订单成功'
  else
    fail "取消订单失败: $CANCEL"
  fi
fi

log ''
log '== 5. 非法签名拒绝 =='
BAD=$(curl -s -X GET "$GATEWAY/api/v1/open/products" \
  -H 'Content-Type: application/json' \
  -H 'X-App-Id: demo-mall' -H "X-Timestamp: $(date +%s)" \
  -H 'X-Nonce: badnonce' -H 'X-Sign: deadbeef')
if printf '%s' "$BAD" | json_val code | grep -q '^401$'; then
  pass '非法签名被拒绝（401）'
else
  fail "非法签名未被拒绝: $BAD"
fi

log ''
if [[ "$FAILED" == "0" ]]; then
  log '🎉 开放 API 冒烟全部通过'
else
  log '💥 存在失败用例，请检查服务日志'
fi
exit $FAILED
