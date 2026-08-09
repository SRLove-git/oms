#!/usr/bin/env bash
set -uo pipefail

# OMS 阶段四 SIT（系统集成测试）：覆盖 P0/P1/P2 关键链路的正向与反向用例
# 前置：docker compose 已启动、全部服务与网关已启动（见 backend/README.md）
# 说明：用例使用时间戳后缀，可重复执行；超时用例与归档用例会临时改写数据库时间字段。

GATEWAY="${GATEWAY_URL:-http://localhost:8080}"
PAYMENT_SERVICE="${PAYMENT_SERVICE_URL:-http://localhost:8085}"
MYSQL_EXEC=(docker exec -i oms-mysql mysql -uroot -proot123456)
TS=$(date +%H%M%S)
SUFFIX="SIT$TS"

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

code_of() { printf '%s' "$1" | json_val code 2>/dev/null || echo 'PARSE_ERR'; }

mysql_order() {
  local sql=$1
  "${MYSQL_EXEC[@]}" oms_order -e "$sql" 2>/dev/null
}

mysql_order_n() {
  "${MYSQL_EXEC[@]}" -N oms_order -e "$1" 2>/dev/null | tr -d '\r'
}

log "== OMS SIT 冒烟开始 ($TS) =="

section "1. 认证与访问控制"
ADMIN_LOGIN=$(api POST "$GATEWAY/api/v1/auth/login" "" '{"username":"admin","password":"admin123"}')
ADMIN_TOKEN=$(printf '%s' "$ADMIN_LOGIN" | json_val data.token)
[[ -n "$ADMIN_TOKEN" ]] && pass "管理员登录" || fail "管理员登录"

MERCHANT_LOGIN=$(api POST "$GATEWAY/api/v1/auth/login" "" '{"username":"merchant","password":"merchant123"}')
MERCHANT_TOKEN=$(printf '%s' "$MERCHANT_LOGIN" | json_val data.token)
MERCHANT_ID=$(printf '%s' "$MERCHANT_LOGIN" | json_val data.user.merchantId)
[[ -n "$MERCHANT_TOKEN" ]] && pass "商户登录 (merchantId=$MERCHANT_ID)" || fail "商户登录"

BAD=$(api POST "$GATEWAY/api/v1/auth/login" "" '{"username":"admin","password":"wrong-pass"}')
[[ "$(code_of "$BAD")" != "0" ]] && pass "错误密码登录被拒绝" || fail "错误密码登录未被拒绝"

NO_TOKEN_HTTP=$(curl -s -o /dev/null -w '%{http_code}' "$GATEWAY/api/v1/orders?page=1&size=5")
[[ "$NO_TOKEN_HTTP" == "401" ]] && pass "无 token 访问受保护接口 -> 401" || fail "无 token 访问 -> $NO_TOKEN_HTTP"

TAMPER_HTTP=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer ${ADMIN_TOKEN}x" "$GATEWAY/api/v1/orders?page=1&size=5")
[[ "$TAMPER_HTTP" == "401" ]] && pass "篡改 token -> 401" || fail "篡改 token -> $TAMPER_HTTP"

FORBIDDEN=$(api GET "$GATEWAY/api/v1/users?page=1&size=5" "$MERCHANT_TOKEN")
[[ "$(code_of "$FORBIDDEN")" == "403" ]] && pass "商户访问管理端用户列表被拒（RBAC）" || fail "商户越权未被拦截 code=$(code_of "$FORBIDDEN")"

section "2. 商品、库存与订单主链路"
WAREHOUSE_ID=$(api POST "$GATEWAY/api/v1/warehouses" "$ADMIN_TOKEN" "{\"code\":\"WH-$SUFFIX\",\"name\":\"SIT仓库\",\"address\":\"测试地址\"}" | json_val data)
[[ -n "$WAREHOUSE_ID" ]] && pass "创建仓库" || fail "创建仓库"

SKU_ID=$(api POST "$GATEWAY/api/v1/skus" "$ADMIN_TOKEN" "{\"spuNo\":\"SPU-$SUFFIX\",\"spuName\":\"SIT商品\",\"skuNo\":\"SKU-$SUFFIX\",\"name\":\"SIT测试器械\",\"spec\":\"标准\",\"price\":100.00,\"costPrice\":60.00,\"udi\":\"UDI-$SUFFIX\",\"registrationNo\":\"REG-$SUFFIX\"}" | json_val data)
[[ -n "$SKU_ID" ]] && pass "创建 SKU（含成本价）" || fail "创建 SKU"

DUP=$(api POST "$GATEWAY/api/v1/skus" "$ADMIN_TOKEN" "{\"spuNo\":\"SPU-$SUFFIX\",\"spuName\":\"SIT商品\",\"skuNo\":\"SKU-$SUFFIX\",\"name\":\"重复\",\"spec\":\"标准\",\"price\":100.00,\"costPrice\":60.00}" | json_val code)
[[ "$DUP" == "409" ]] && pass "重复 SKU 编码被拒绝（唯一约束）" || fail "重复 SKU 未被拒绝 code=$DUP"

api POST "$GATEWAY/api/v1/inventories/inbound" "$ADMIN_TOKEN" "{\"warehouseId\":$WAREHOUSE_ID,\"skuId\":$SKU_ID,\"quantity\":100,\"batchNo\":\"B-$SUFFIX\",\"expireAt\":\"2029-08-09\"}" >/dev/null
INV=$(api GET "$GATEWAY/api/v1/inventories?skuId=$SKU_ID&page=1&size=5" "$ADMIN_TOKEN")
INV_QTY=$(printf '%s' "$INV" | json_val data.records.0.quantity)
[[ "$INV_QTY" == "100" ]] && pass "入库 100 件" || fail "入库数量异常 quantity=$INV_QTY"

ORDER_RESP=$(api POST "$GATEWAY/api/v1/orders" "$MERCHANT_TOKEN" "{\"merchantId\":$MERCHANT_ID,\"orderType\":1,\"remark\":\"sit-$SUFFIX\",\"items\":[{\"skuId\":$SKU_ID,\"quantity\":2}]}")
ORDER_NO=$(printf '%s' "$ORDER_RESP" | json_val data.orderNo)
[[ -n "$ORDER_NO" ]] && pass "下单成功 $ORDER_NO" || fail "下单失败"

OVERSELL=$(api POST "$GATEWAY/api/v1/orders" "$MERCHANT_TOKEN" "{\"merchantId\":$MERCHANT_ID,\"orderType\":1,\"items\":[{\"skuId\":$SKU_ID,\"quantity\":9999}]}")
[[ "$(code_of "$OVERSELL")" != "0" ]] && pass "超卖下单被拦截" || fail "超卖下单未被拦截"

PAY_RESP=$(api POST "$GATEWAY/api/v1/orders/$ORDER_NO/pay" "$MERCHANT_TOKEN" '{"channel":"mock"}')
PAYMENT_NO=$(printf '%s' "$PAY_RESP" | json_val data.paymentNo)
PAY_AMOUNT=$(printf '%s' "$PAY_RESP" | json_val data.amount)
CB=$(api POST "$PAYMENT_SERVICE/api/v1/payment-callbacks/mock" "" "{\"paymentNo\":\"$PAYMENT_NO\",\"channelTxnNo\":\"TXN-$TS\",\"amount\":$PAY_AMOUNT,\"status\":\"SUCCESS\"}")
[[ "$(printf '%s' "$CB" | json_val code)" == "0" ]] && pass "支付回调成功" || fail "支付回调失败: $CB"
sleep 1
ST=$(api GET "$GATEWAY/api/v1/orders/$ORDER_NO" "$MERCHANT_TOKEN" | json_val data.status)
[[ "$ST" == "2" ]] && pass "支付成功（mock 回调）status=2" || fail "支付状态异常 status=$ST"

PAY2=$(api POST "$GATEWAY/api/v1/orders/$ORDER_NO/pay" "$MERCHANT_TOKEN" '{"channel":"mock"}' | json_val code)
[[ "$PAY2" != "0" ]] && pass "重复支付被拦截" || fail "重复支付未被拦截"

api POST "$GATEWAY/api/v1/orders/$ORDER_NO/audit" "$ADMIN_TOKEN" >/dev/null
api POST "$GATEWAY/api/v1/orders/$ORDER_NO/ship" "$ADMIN_TOKEN" "{\"trackingNo\":\"TRK-$SUFFIX\",\"carrier\":\"顺丰\"}" >/dev/null
api POST "$GATEWAY/api/v1/orders/$ORDER_NO/sign" "$MERCHANT_TOKEN" >/dev/null
api POST "$GATEWAY/api/v1/orders/$ORDER_NO/complete" "$ADMIN_TOKEN" >/dev/null
ST=$(api GET "$GATEWAY/api/v1/orders/$ORDER_NO" "$MERCHANT_TOKEN" | json_val data.status)
[[ "$ST" == "6" ]] && pass "订单全生命周期 2→3→4→5→6 完成" || fail "订单状态流转异常 status=$ST"

section "3. 订单超时自动取消（含库存释放）"
TIMEOUT_NO=$(api POST "$GATEWAY/api/v1/orders" "$MERCHANT_TOKEN" "{\"merchantId\":$MERCHANT_ID,\"orderType\":1,\"remark\":\"timeout-$SUFFIX\",\"items\":[{\"skuId\":$SKU_ID,\"quantity\":1}]}" | json_val data.orderNo)
mysql_order "UPDATE \`order\` SET timeout_at = DATE_SUB(NOW(), INTERVAL 1 MINUTE) WHERE order_no = '$TIMEOUT_NO'"
TIMEOUT_OK=0
for i in $(seq 1 15); do
  ST=$(api GET "$GATEWAY/api/v1/orders/$TIMEOUT_NO" "$MERCHANT_TOKEN" | json_val data.status)
  if [[ "$ST" == "7" ]]; then TIMEOUT_OK=1; break; fi
  sleep 5
done
[[ "$TIMEOUT_OK" == "1" ]] && pass "待支付超时自动取消" || fail "超时未自动取消"
INV=$(api GET "$GATEWAY/api/v1/inventories?skuId=$SKU_ID&page=1&size=5" "$ADMIN_TOKEN")
QTY=$(printf '%s' "$INV" | json_val data.records.0.quantity)
RESV=$(printf '%s' "$INV" | json_val data.records.0.reservedQuantity)
[[ "$QTY" == "98" && "$RESV" == "0" ]] && pass "超时取消释放该单库存（可用 98、预占 0）" || fail "库存释放异常 quantity=$QTY reserved=$RESV"

section "4. 售后：退货退款与库存回补"
ORDER_DETAIL=$(api GET "$GATEWAY/api/v1/orders/$ORDER_NO" "$MERCHANT_TOKEN")
ITEM_ID=$(printf '%s' "$ORDER_DETAIL" | json_val data.items.0.id)
ITEM_SKU=$(printf '%s' "$ORDER_DETAIL" | json_val data.items.0.skuId)
RETURN_RESP=$(api POST "$GATEWAY/api/v1/return-orders" "$MERCHANT_TOKEN" "{\"orderNo\":\"$ORDER_NO\",\"type\":1,\"reason\":\"SIT质检不合格\",\"items\":[{\"orderItemId\":$ITEM_ID,\"skuId\":$ITEM_SKU,\"quantity\":1}]}")
RETURN_NO=$(printf '%s' "$RETURN_RESP" | json_val data.returnNo)
[[ -n "$RETURN_NO" ]] && pass "退货申请 $RETURN_NO" || fail "退货申请失败"

api POST "$GATEWAY/api/v1/return-orders/$RETURN_NO/review" "$ADMIN_TOKEN" '{"approved":true,"reason":"SIT通过"}' >/dev/null
api POST "$GATEWAY/api/v1/return-orders/$RETURN_NO/receive" "$ADMIN_TOKEN" '{"qualified":true,"remark":"质检合格"}' >/dev/null
api POST "$GATEWAY/api/v1/return-orders/$RETURN_NO/refund" "$ADMIN_TOKEN" "{\"paymentNo\":\"\",\"amount\":100.00,\"method\":2}" >/dev/null
RETURN_STATUS=$(api GET "$GATEWAY/api/v1/return-orders/$RETURN_NO" "$MERCHANT_TOKEN" | json_val data.status)
[[ "$RETURN_STATUS" == "6" ]] && pass "退货审核→收货→退款→完成" || fail "售后状态异常 status=$RETURN_STATUS"
INV=$(api GET "$GATEWAY/api/v1/inventories?skuId=$SKU_ID&page=1&size=5" "$ADMIN_TOKEN")
QTY=$(printf '%s' "$INV" | json_val data.records.0.quantity)
[[ "$QTY" == "99" ]] && pass "退货入库回补库存（可用 99）" || fail "回补异常 quantity=$QTY"

section "5. 支付对账（连续 7 日 + 差异处理）"
RECON_OK=1
for d in 1 2 3 4 5 6 7; do
  DAY=$(date -v-${d}d +%F 2>/dev/null || date -d "-$d days" +%F)
  R=$(api POST "$GATEWAY/api/v1/reconciliation/run" "$ADMIN_TOKEN" "{\"bizDate\":\"$DAY\",\"channel\":\"mock\",\"simulateDiff\":false}")
  DC=$(printf '%s' "$R" | json_val data.diffCount)
  [[ "$DC" != "0" ]] && RECON_OK=0
done
[[ "$RECON_OK" == "1" ]] && pass "连续 7 日对账差异为 0" || fail "存在对账差异"
SIM=$(api POST "$GATEWAY/api/v1/reconciliation/run" "$ADMIN_TOKEN" "{\"bizDate\":\"$(date +%F)\",\"channel\":\"mock\",\"simulateDiff\":true}")
SIM_DC=$(printf '%s' "$SIM" | json_val data.diffCount)
[[ "$SIM_DC" != "0" ]] && pass "模拟渠道差异生成 (diffCount=$SIM_DC)" || fail "差异生成失败"
SIM_ID=$(printf '%s' "$SIM" | json_val data.id)
api POST "$GATEWAY/api/v1/reconciliation/$SIM_ID/handle" "$ADMIN_TOKEN" >/dev/null
SIM_STATUS=$(api GET "$GATEWAY/api/v1/reconciliation?page=1&size=5" "$ADMIN_TOKEN" | json_val data.records.0.status)
[[ "$SIM_STATUS" == "2" ]] && pass "差异人工处理完成" || fail "差异处理状态异常"

section "6. 报表中心（15 个接口）"
REPORT_OK=1
for ep in \
  reports/sales/summary reports/sales/trend reports/sales/source reports/sales/daily \
  reports/inventory/warehouse-stock reports/inventory/stock-summary reports/inventory/expiry-distribution \
  reports/inventory/turnover reports/inventory/slow-moving \
  reports/payments/channel-stats reports/payments/reconciliation-stats \
  reports/aftersales/type-stats reports/aftersales/reason-distribution \
  reports/aftersales/repair-duration reports/aftersales/return-rate; do
  C=$(api GET "$GATEWAY/api/v1/$ep" "$ADMIN_TOKEN" | json_val code)
  [[ "$C" != "0" ]] && REPORT_OK=0
done
[[ "$REPORT_OK" == "1" ]] && pass "全部报表接口返回正常" || fail "存在报表接口异常"

section "7. 历史订单归档（冷热分离）"
ARCHIVE_BEFORE=$(mysql_order_n "SELECT COUNT(*) FROM order_archive")
mysql_order "UPDATE \`order\` SET updated_at = DATE_SUB(NOW(), INTERVAL 200 DAY) WHERE order_no = '$ORDER_NO'"
ARCHIVED=$(api POST "$GATEWAY/api/v1/orders/archive/run" "$ADMIN_TOKEN" | json_val data)
ARCHIVE_AFTER=$(mysql_order_n "SELECT COUNT(*) FROM order_archive")
[[ "$ARCHIVE_AFTER" -gt "$ARCHIVE_BEFORE" ]] && pass "终态订单归档成功（本次归档 $ARCHIVED 单）" || fail "归档失败 before=$ARCHIVE_BEFORE after=$ARCHIVE_AFTER"
FALLBACK=$(api GET "$GATEWAY/api/v1/orders/$ORDER_NO" "$ADMIN_TOKEN" | json_val code)
[[ "$FALLBACK" == "0" ]] && pass "归档后订单详情回退冷表可查" || fail "归档后详情不可查 code=$FALLBACK"
SUMMARY=$(api GET "$GATEWAY/api/v1/reports/sales/summary" "$ADMIN_TOKEN")
PAID_AMOUNT=$(printf '%s' "$SUMMARY" | json_val data.paidAmount)
[[ -n "$PAID_AMOUNT" ]] && pass "归档后报表口径仍完整 (支付金额 $PAID_AMOUNT)" || fail "报表口径异常"

section "8. 审计日志"
AUDIT_TOTAL=$(api GET "$GATEWAY/api/v1/audit-logs?page=1&size=10" "$ADMIN_TOKEN" | json_val data.total)
[[ -n "$AUDIT_TOTAL" && "$AUDIT_TOTAL" -gt 0 ]] && pass "关键操作审计日志可查询 (total=$AUDIT_TOTAL)" || fail "审计日志为空"

log ""
log "SIT 结果：通过 $PASSED 项"
if [[ "$FAILED" == "0" ]]; then
  printf '✅✅ SIT 全部通过\n'
else
  printf '❌ SIT 存在失败项\n'
  exit 1
fi
