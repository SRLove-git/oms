# 上线与护航手册（Go-Live Playbook）

对应 SCHEDULE 阶段 5 与第 10 节上线检查清单。以下流程已在本地全栈环境完成演练；真实生产执行时按同一顺序推进。

## 1. 上线前检查（对照 SCHEDULE 第 10 节）

| 检查项 | 状态 | 执行方式 |
| :--- | :--- | :--- |
| 生产环境（K8s、中间件）就绪，容量与备份验证 | 演练完成 | `kubectl -n oms get deploy,pods`；MySQL 备份演练 |
| 支付渠道正式商户号、证书、回调地址 | 外部依赖 | 真实渠道就绪后替换 mock 适配器并回归 |
| 域名、HTTPS 证书、OSS、短信/邮件 | 外部依赖 | Ingress TLS Secret、渠道配置 |
| 监控告警就绪 | ✅ 演练完成 | `deploy/monitoring/` 全组件 UP，规则加载 |
| 数据库迁移脚本在预发演练 | ✅ 演练完成 | Flyway 全量迁移通过（各服务启动即验证） |
| 回滚方案演练 | ✅ 就绪 | 见 §3，脚本 `scripts/k8s-rollback-drill.sh` |
| 镜像安全扫描与合规评审 | 部分 | 阶段四合规预评审通过；Trivy 扫描纳入 CI 前置 |
| 值班与升级机制 | ✅ 就绪 | 见 `oncall-runbook.md` |
| 护航计划与文档交接 | ✅ 就绪 | 见 §4 与 `handover.md` |

## 2. 上线执行序列（演练版）

```bash
# 1) 预发/生产发布（kustomize）
kubectl kustomize deploy/k8s | kubectl apply -f -
kubectl -n oms rollout status deployment --all --timeout=300s

# 2) 冒烟（网关就绪后）
GATEWAY_URL=https://<域名> ./scripts/e2e-smoke.sh
GATEWAY_URL=https://<域名> ./scripts/e2e-smoke-phase2.sh
GATEWAY_URL=https://<域名> ./scripts/report-smoke.sh
GATEWAY_URL=https://<域名> ./scripts/sit-test.sh

# 3) 支付回调地址与渠道配置核对（真实渠道就绪后）
# 4) 监控确认：Prometheus targets 全 UP、Grafana 面板有数据
# 5) 灰度切流（nginx-ingress canary 权重 10% → 50% → 100%）
```

## 3. 回滚演练

```bash
# 演练（默认只打印命令）
DRY_RUN=1 ./scripts/k8s-rollback-drill.sh
# 真实执行（对当前 kubectl 上下文生效）
DRY_RUN=0 ./scripts/k8s-rollback-drill.sh
```

触发条件：错误率 > 5% 持续 5 分钟、核心 P99 > 500ms、支付回调大面积失败、库存/对账异常、数据迁移失败。

## 4. 上线后护航（24–72 小时）

| 时段 | 动作 |
| :--- | :--- |
| 0–2h | 高频巡检：健康检查、错误率、P99、支付成功率、消息积压，每 15 分钟 |
| 2–24h | 每 2 小时巡检一次；跟进对账结果（连续 7 日差异为 0） |
| 24–72h | 每 4 小时巡检；输出护航日报与遗留问题清单 |

值班与升级见 `oncall-runbook.md`。
