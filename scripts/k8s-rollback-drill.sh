#!/usr/bin/env bash
set -euo pipefail

# K8s 发布/回滚演练脚本
# 默认只打印命令（DRY_RUN=1）；在真实集群演练时设置 DRY_RUN=0
# 用法：DRY_RUN=0 SERVICE=order-service ./scripts/k8s-rollback-drill.sh

NAMESPACE="${NAMESPACE:-oms}"
DRY_RUN="${DRY_RUN:-1}"
SERVICE="${SERVICE:-order-service}"
REGISTRY="${REGISTRY:-registry.example.com/oms}"

run() {
  if [[ "$DRY_RUN" == "0" ]]; then
    eval "$*"
  else
    printf '>> %s\n' "$*"
  fi
}

if ! command -v kubectl >/dev/null 2>&1; then
  echo "未找到 kubectl"
  exit 1
fi

echo "== 回滚演练：$SERVICE (DRY_RUN=$DRY_RUN) =="

echo "-- 1. 模拟发布新版本（drill-sha tag） --"
run "kubectl -n $NAMESPACE set image deployment/$SERVICE $SERVICE=$REGISTRY/$SERVICE:drill-sha"
run "kubectl -n $NAMESPACE rollout status deployment/$SERVICE --timeout=180s || true"
run "kubectl -n $NAMESPACE rollout history deployment/$SERVICE"

echo "-- 2. 确认触发条件（错误率>5% / P99>500ms / 支付回调失败 / 数据异常）--"
echo "   确认后执行第 3 步回滚"

echo "-- 3. 回滚到上一稳定版本 --"
run "kubectl -n $NAMESPACE rollout undo deployment/$SERVICE"
run "kubectl -n $NAMESPACE rollout status deployment/$SERVICE --timeout=180s"

echo "-- 4. 回滚后验证 --"
run "kubectl -n $NAMESPACE get pods -l app=$SERVICE"
run "kubectl -n $NAMESPACE logs deployment/$SERVICE --tail=50"
echo "演练完成；随后执行冒烟（e2e/report/sit）确认服务正常。"
