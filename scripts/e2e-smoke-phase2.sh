#!/usr/bin/env bash
set -euo pipefail

# OMS 阶段二 P1 端到端冒烟：售后（退货/换货/维修）→ 物流轨迹 → 第三方集成 → 消息通知 → 支付对账
# 前置：docker compose 已启动、6 个后端服务已启动（含 after-sales-service / integration-service）

GATEWAY="${GATEWAY_URL:-http://localhost:8080}"
AFTER_SALES_URL="${AFTER_SALES_URL:-http://localhost:8084}"
INTEGRATION_URL="${INTEGRATION_URL:-http://localhost:8086}"

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
    sleep 1
  done
  fail "$name 未就绪"
  return 1
}

log '== 1. 等待服务就绪 =='
wait_health "$GATEWAY" "网关" || exit 1
wait_health "$AFTER_SALES_URL" "售后服务" || exit 1
wait_health "$INTEGRATION_URL" "集成服务" || exit 1

log ''
log '== 2. 登录 =='
ADMIN_TOKEN=$(api POST /api/v1/auth/login "" '{"username":"admin","password":"admin123"}' | json_val data.token)
pass "管理员登录成功"
MERCHANT_LOGIN=$(curl_json POST "$GATEWAY/api/v1/auth/login" "" '{"username":"merchant","password":"merchant123"}')
MERCHANT_TOKEN=$(printf '%s' "$MERCHANT_LOGIN" | json_val data.token)
pass "商户登录成功 (merchantId=1)"

log ''
log '== 3. 准备商品与库存 =='
ADMIN_AUTH=(-H "Authorization: Bearer $ADMIN_TOKEN")
WAREHOUSE_ID=$(api POST /api/v1/warehouses "$ADMIN_TOKEN" '{"code":"WH-SMOKE-P2","name":"阶段二冒烟仓库","address":"测试地址"}' | json_val data)
SKU_ID=$(api POST /api/v1/skus "$ADMIN_TOKEN" '{"spuNo":"SPU-SMOKE-P2","spuName":"阶段二冒烟商品","skuNo":"SKU-SMOKE-P2","name":"冒烟测试器械P2","spec":"标准","price":199.50,"udi":"UDI-SMOKE-P2","registrationNo":"REG-002"}' | json_val data)
api POST /api/v1/inventories/inbound "$ADMIN_TOKEN" "{\"warehouseId\":$WAREHOUSE_ID,\"skuId\":$SKU_ID,\"quantity\":100,\"batchNo\":\"B20260809\",\"expireAt\":\"2029-08-09\"}" >/dev/null
pass "已创建仓库/商品并入库 100 件"

log ''
log '== 4. 下单并走到已签收 =='
create_order_and_advance() { # 返回 ORDER_NO
  local order_resp order_no payment_no
  order_resp=$(api POST /api/v1/orders "$MERCHANT_TOKEN" "{\"merchantId\":1,\"orderType\":1,\"remark\":\"e2e-p2\",\"items\":[{\"skuId\":$SKU_ID,\"quantity\":2}]}")
  order_no=$(printf '%s' "$order_resp" | json_val data.orderNo)
  PAY_RESP=$(api POST "/api/v1/orders/$order_no/pay" "$MERCHANT_TOKEN" '{"channel":"mock"}')
  payment_no=$(printf '%s' "$PAY_RESP" | json_val data.paymentNo)
  PAY_AMOUNT=$(printf '%s' "$PAY_RESP" | json_val data.amount)
  curl_json POST "$GATEWAY/api/v1/payment-callbacks/mock" "" "{\"paymentNo\":\"$payment_no\",\"channelTxnNo\":\"TXN-P2-$(date +%s)\",\"amount\":$PAY_AMOUNT,\"status\":\"SUCCESS\"}" >/dev/null
  sleep 1
  api POST "/api/v1/orders/$order_no/audit" "$ADMIN_TOKEN" >/dev/null
  api POST "/api/v1/orders/$order_no/ship" "$ADMIN_TOKEN" '{"trackingNo":"SF-P2-001","carrier":"SF"}' >/dev/null
  api POST "/api/v1/orders/$order_no/sign" "$MERCHANT_TOKEN" >/dev/null
  printf '%s' "$order_no"
}

ORDER_NO=$(create_order_and_advance)
pass "订单 $ORDER_NO 已创建并签收"

ORDER_DETAIL=$(api GET "/api/v1/orders/$ORDER_NO" "$MERCHANT_TOKEN")
ORDER_ITEM_ID=$(printf '%s' "$ORDER_DETAIL" | json_val data.items.0.id)
ORDER_ITEM_SKU=$(printf '%s' "$ORDER_DETAIL" | json_val data.items.0.skuId)
ORDER_ITEM_QTY=$(printf '%s' "$ORDER_DETAIL" | json_val data.items.0.quantity)
PAYMENT_NO=$(api GET "/api/v1/payments?orderNo=$ORDER_NO&page=1&size=5" "$ADMIN_TOKEN" | json_val data.records.0.paymentNo)
pass "订单明细 itemId=$ORDER_ITEM_ID skuId=$ORDER_ITEM_SKU paymentNo=$PAYMENT_NO"

log ''
log '== 5. 售后：退货全流程 =='
RETURN_RESP=$(api POST /api/v1/return-orders "$MERCHANT_TOKEN" "{\"orderNo\":\"$ORDER_NO\",\"type\":1,\"reason\":\"商品破损\",\"items\":[{\"orderItemId\":$ORDER_ITEM_ID,\"skuId\":$ORDER_ITEM_SKU,\"quantity\":2}]}")
RETURN_NO=$(printf '%s' "$RETURN_RESP" | json_val data.returnNo)
[[ "$(printf '%s' "$RETURN_RESP" | json_val data.status)" == "1" ]] && pass "售后申请已提交 $RETURN_NO" || fail "售后申请状态异常"

api POST "/api/v1/return-orders/$RETURN_NO/review" "$ADMIN_TOKEN" '{"approved":true,"reason":"同意退货"}' >/dev/null
pass "审核通过"
api POST "/api/v1/return-orders/$RETURN_NO/receive" "$ADMIN_TOKEN" '{"qualified":true,"remark":"质检合格"}' >/dev/null
pass "收货质检合格"
api POST "/api/v1/return-orders/$RETURN_NO/refund" "$ADMIN_TOKEN" "{\"paymentNo\":\"$PAYMENT_NO\",\"amount\":399.00,\"method\":1}" >/dev/null
pass "退款成功"

RETURN_DETAIL=$(api GET "/api/v1/return-orders/$RETURN_NO" "$ADMIN_TOKEN")
[[ "$(printf '%s' "$RETURN_DETAIL" | json_val data.status)" == "6" ]] && pass "退货单已完结" || fail "退货单状态异常: $(printf '%s' "$RETURN_DETAIL" | json_val data.status)"
[[ "$(printf '%s' "$RETURN_DETAIL" | json_val data.refunds.0.status)" == "3" ]] && pass "退款记录已入账" || fail "退款记录异常"
ORDER_AFTER=$(api GET "/api/v1/orders/$ORDER_NO" "$ADMIN_TOKEN")
[[ "$(printf '%s' "$ORDER_AFTER" | json_val data.status)" == "6" ]] && pass "订单回到已完成" || fail "订单状态异常: $(printf '%s' "$ORDER_AFTER" | json_val data.status)"
INV=$(curl_json GET "$GATEWAY/api/v1/inventories?skuId=$SKU_ID&page=1&size=5" "$ADMIN_TOKEN")
[[ "$(printf '%s' "$INV" | json_val data.records.0.quantity)" == "100" ]] && pass "退货入库库存已回补" || fail "库存回补异常: $(printf '%s' "$INV" | json_val data.records.0.quantity)"

log ''
log '== 6. 售后：维修全流程 =='
ORDER_REPAIR=$(create_order_and_advance)
REPAIR_ORDER_DETAIL=$(api GET "/api/v1/orders/$ORDER_REPAIR" "$MERCHANT_TOKEN")
R_ITEM_ID=$(printf '%s' "$REPAIR_ORDER_DETAIL" | json_val data.items.0.id)
R_ITEM_SKU=$(printf '%s' "$REPAIR_ORDER_DETAIL" | json_val data.items.0.skuId)
REPAIR_RESP=$(api POST /api/v1/return-orders "$MERCHANT_TOKEN" "{\"orderNo\":\"$ORDER_REPAIR\",\"type\":3,\"reason\":\"无法开机\",\"items\":[{\"orderItemId\":$R_ITEM_ID,\"skuId\":$R_ITEM_SKU,\"quantity\":1}]}")
REPAIR_RETURN_NO=$(printf '%s' "$REPAIR_RESP" | json_val data.returnNo)
api POST "/api/v1/return-orders/$REPAIR_RETURN_NO/review" "$ADMIN_TOKEN" '{"approved":true,"reason":"同意维修"}' >/dev/null
api POST "/api/v1/return-orders/$REPAIR_RETURN_NO/receive" "$ADMIN_TOKEN" '{"qualified":true,"remark":"已收货"}' >/dev/null
REPAIR_ORDER=$(api POST "/api/v1/return-orders/$REPAIR_RETURN_NO/repairs" "$ADMIN_TOKEN" "{\"skuId\":$R_ITEM_SKU,\"faultDesc\":\"主板故障\",\"assignedTo\":\"维修员A\"}")
REPAIR_ID=$(printf '%s' "$REPAIR_ORDER" | json_val data.id)
api POST "/api/v1/return-orders/repairs/$REPAIR_ID/progress" "$ADMIN_TOKEN" '{"action":"start","content":"开始维修"}' >/dev/null
api POST "/api/v1/return-orders/repairs/$REPAIR_ID/progress" "$ADMIN_TOKEN" '{"action":"complete","content":"更换主板，维修完成"}' >/dev/null
REPAIR_DETAIL=$(api GET "/api/v1/return-orders/$REPAIR_RETURN_NO" "$ADMIN_TOKEN")
[[ "$(printf '%s' "$REPAIR_DETAIL" | json_val data.status)" == "6" ]] && pass "维修售后已完结" || fail "维修售后状态异常"

log ''
log '== 7. 售后：换货全流程 =='
ORDER_EXCHANGE=$(create_order_and_advance)
EXCHANGE_ORDER_DETAIL=$(api GET "/api/v1/orders/$ORDER_EXCHANGE" "$MERCHANT_TOKEN")
E_ITEM_ID=$(printf '%s' "$EXCHANGE_ORDER_DETAIL" | json_val data.items.0.id)
E_ITEM_SKU=$(printf '%s' "$EXCHANGE_ORDER_DETAIL" | json_val data.items.0.skuId)
EXCHANGE_RESP=$(api POST /api/v1/return-orders "$MERCHANT_TOKEN" "{\"orderNo\":\"$ORDER_EXCHANGE\",\"type\":2,\"reason\":\"规格不符\",\"items\":[{\"orderItemId\":$E_ITEM_ID,\"skuId\":$E_ITEM_SKU,\"quantity\":1}]}")
EXCHANGE_RETURN_NO=$(printf '%s' "$EXCHANGE_RESP" | json_val data.returnNo)
api POST "/api/v1/return-orders/$EXCHANGE_RETURN_NO/review" "$ADMIN_TOKEN" '{"approved":true,"reason":"同意换货"}' >/dev/null
api POST "/api/v1/return-orders/$EXCHANGE_RETURN_NO/receive" "$ADMIN_TOKEN" '{"qualified":true,"remark":"已收货"}' >/dev/null
api POST "/api/v1/return-orders/$EXCHANGE_RETURN_NO/exchange-ship" "$ADMIN_TOKEN" >/dev/null
EXCHANGE_DETAIL=$(api GET "/api/v1/return-orders/$EXCHANGE_RETURN_NO" "$ADMIN_TOKEN")
[[ "$(printf '%s' "$EXCHANGE_DETAIL" | json_val data.status)" == "6" ]] && pass "换货售后已完结" || fail "换货售后状态异常"

log ''
log '== 8. 物流轨迹 =='
curl_json POST "$GATEWAY/api/v1/logistics/callback" "$ADMIN_TOKEN" "{\"orderNo\":\"$ORDER_REPAIR\",\"trackingNo\":\"SF-P2-001\",\"carrier\":\"SF\",\"status\":\"in_transit\",\"trace\":\"快件已到达上海转运中心\"}" >/dev/null
curl_json POST "$GATEWAY/api/v1/logistics/callback" "$ADMIN_TOKEN" "{\"orderNo\":\"$ORDER_REPAIR\",\"trackingNo\":\"SF-P2-001\",\"carrier\":\"SF\",\"status\":\"signed\",\"trace\":\"快件已签收\"}" >/dev/null
LOGISTICS=$(api GET "/api/v1/logistics/by-order/$ORDER_REPAIR" "$ADMIN_TOKEN")
TRACE_COUNT=$(printf '%s' "$LOGISTICS" | python3 -c 'import json,sys; print(len(json.load(sys.stdin)["data"]["trace"]))')
[[ "$(printf '%s' "$LOGISTICS" | json_val data.status)" == "signed" ]] && pass "物流轨迹已同步 (共 $TRACE_COUNT 条)" || fail "物流轨迹异常"

log ''
log '== 9. 第三方平台集成 =='
MAPPING=$(api POST /api/v1/integrations/orders/pull "$ADMIN_TOKEN" "{\"platform\":\"tmall\",\"platformOrderNo\":\"TM-20260809-001\",\"orderNo\":\"$ORDER_NO\",\"rawData\":\"{\\\"buyer\\\":\\\"张三\\\"}\"}")
MAPPING_ID=$(printf '%s' "$MAPPING" | json_val data.id)
[[ "$(printf '%s' "$MAPPING" | json_val data.status)" == "2" ]] && pass "平台订单已拉取并映射" || fail "平台订单映射异常"
api POST /api/v1/integrations/orders/ship-sync "$ADMIN_TOKEN" "{\"platform\":\"tmall\",\"platformOrderNo\":\"TM-20260809-001\",\"orderNo\":\"$ORDER_NO\",\"trackingNo\":\"SF-P2-001\",\"carrier\":\"SF\"}" >/dev/null
pass "发货回传成功"
api POST /api/v1/integrations/orders/after-sales-sync "$ADMIN_TOKEN" "{\"platform\":\"tmall\",\"platformOrderNo\":\"TM-20260809-001\",\"returnNo\":\"$RETURN_NO\",\"status\":\"COMPLETED\"}" >/dev/null
pass "售后单同步成功"
api POST /api/v1/integrations/stock-sync "$ADMIN_TOKEN" "{\"platform\":\"tmall\",\"skuNo\":\"SKU-SMOKE-P2\",\"quantity\":100,\"warehouseCode\":\"WH-SMOKE-P2\"}" >/dev/null
pass "库存同步成功"

log ''
log '== 10. 消息通知 =='
api POST /api/v1/notifications/templates "$ADMIN_TOKEN" '{"code":"AFTER_SALES_APPLIED","name":"售后申请通知","channel":"in_app","scene":"AFTER_SALES_APPLIED","titleTemplate":"售后申请已提交","contentTemplate":"您的售后申请 {returnNo} 已提交"}' >/dev/null
pass "通知模板已保存"
OK_MSG=$(api POST /api/v1/notifications/send "$ADMIN_TOKEN" '{"channel":"in_app","scene":"AFTER_SALES_APPLIED","receiver":"merchant:1","title":"售后申请已提交","content":"售后单已提交，等待审核"}')
[[ "$(printf '%s' "$OK_MSG" | json_val data.status)" == "1" ]] && pass "站内信发送成功" || fail "站内信发送失败"
FAIL_MSG=$(api POST /api/v1/notifications/send "$ADMIN_TOKEN" '{"channel":"sms","scene":"AFTER_SALES_APPLIED","receiver":"fail:13800000000","title":"售后通知","content":"模拟失败短信"}')
FAIL_MSG_ID=$(printf '%s' "$FAIL_MSG" | json_val data.id)
[[ "$(printf '%s' "$FAIL_MSG" | json_val data.status)" == "2" ]] && pass "失败短信已记录" || fail "失败短信未记录"
RETRY_MSG=$(api POST "/api/v1/notifications/messages/$FAIL_MSG_ID/retry" "$ADMIN_TOKEN")
[[ "$(printf '%s' "$RETRY_MSG" | json_val data.retryCount)" == "1" ]] && pass "失败消息已重试" || fail "消息重试异常"

log ''
log '== 11. 支付对账 =='
RUN_CLEAN=$(api POST /api/v1/reconciliation/run "$ADMIN_TOKEN" "{\"bizDate\":\"$(date +%F)\",\"channel\":\"mock\",\"simulateDiff\":false}")
[[ "$(printf '%s' "$RUN_CLEAN" | json_val data.diffCount)" == "0" ]] && pass "对账一致 (渠道=本地, 0 差异)" || fail "对账应一致但出现差异"
RUN_DIFF=$(api POST /api/v1/reconciliation/run "$ADMIN_TOKEN" "{\"bizDate\":\"$(date +%F)\",\"channel\":\"mock\",\"simulateDiff\":true}")
DIFF_ID=$(printf '%s' "$RUN_DIFF" | json_val data.id)
[[ "$(printf '%s' "$RUN_DIFF" | json_val data.diffCount)" -gt "0" ]] && pass "模拟差异已生成 (diffCount=$(printf '%s' "$RUN_DIFF" | json_val data.diffCount))" || fail "模拟差异未生成"
api POST "/api/v1/reconciliation/$DIFF_ID/handle" "$ADMIN_TOKEN" >/dev/null
RUN_AFTER=$(api GET "/api/v1/reconciliation?bizDate=$(date +%F)&channel=mock&page=1&size=5" "$ADMIN_TOKEN")
for i in 0 1 2 3 4; do
  RID=$(printf '%s' "$RUN_AFTER" | json_val "data.records.$i.id")
  if [[ "$RID" == "$DIFF_ID" ]]; then
    [[ "$(printf '%s' "$RUN_AFTER" | json_val "data.records.$i.status")" == "2" ]] && pass "对账差异已处理" || fail "对账差异未处理"
    break
  fi
done

log ''
if [[ "$FAILED" == "0" ]]; then
  log '✅✅ 阶段二 P1 端到端冒烟全部通过'
else
  log '❌ 冒烟存在失败项，请查看上方日志'
  exit 1
fi
