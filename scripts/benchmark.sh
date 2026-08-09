#!/usr/bin/env bash
set -euo pipefail

# OMS 核心接口压测（读侧）：订单分页 / 销售报表汇总
# 用法：
#   ./scripts/benchmark.sh                       # 默认 2000 请求 / 50 并发
#   N=5000 C=100 ./scripts/benchmark.sh          # 自定义

GATEWAY="${GATEWAY_URL:-http://localhost:8080}"
N="${N:-2000}"
C="${C:-50}"

log() { printf '%s\n' "$*"; }

TOKEN=$(curl -s -X POST "$GATEWAY/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['token'])")
log "已获取管理员 token"

if command -v wrk >/dev/null 2>&1; then
  RUNNER="wrk"
elif command -v ab >/dev/null 2>&1 || [[ -x /usr/sbin/ab ]]; then
  RUNNER="ab"
else
  log "未找到 wrk/ab，请安装后重试（macOS 自带 ab：/usr/sbin/ab）"
  exit 1
fi

bench() {
  local name=$1
  local url=$2
  log ""
  log "== $name =="
  log "目标: $url"
  if [[ "$RUNNER" == "wrk" ]]; then
    wrk -t4 -c"$C" -d10s -H "Authorization: Bearer $TOKEN" "$url"
  else
    AB_BIN="ab"
    [[ -x /usr/sbin/ab ]] && AB_BIN=/usr/sbin/ab
    "$AB_BIN" -n "$N" -c "$C" -H "Authorization: Bearer $TOKEN" "$url"
  fi
}

bench "订单分页列表" "$GATEWAY/api/v1/orders?page=1&size=10"
bench "销售报表汇总" "$GATEWAY/api/v1/reports/sales/summary"
bench "销售趋势" "$GATEWAY/api/v1/reports/sales/trend"
bench "仓库库存报表" "$GATEWAY/api/v1/reports/inventory/warehouse-stock"

log ""
log "性能目标参考（项目要求 7.2）：核心接口 P99 ≤ 500ms；普通查询 P99 ≤ 1s"
