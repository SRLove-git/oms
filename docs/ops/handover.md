# 文档交接

## 部署手册

- 本地：`backend/README.md`（基础设施 + 服务启动 + 冒烟）
- 生产：`deploy/k8s/README.md`（kustomize 部署、灰度、回滚）
- 监控：`deploy/monitoring/README.md`
- 上线流程：`docs/ops/go-live-playbook.md`

## 运维手册

- 日志：应用 stdout/stderr → Filebeat → ELK（生产规划，见项目要求五）。
- 指标：`/actuator/prometheus` → Prometheus/Grafana，告警规则见 `deploy/monitoring/alert-rules.yml`。
- 配置：环境变量注入（`configmap.yaml`），Nacos 配置中心可刷新。
- 数据库：Flyway 管理迁移，禁止手工改库；归档任务见 `docs/adr/0002-cold-hot-order-archive.md`。
- 备份：MySQL 全量+binlog（RPO ≤ 5 分钟）、Redis AOF、归档表定期归档备份。

## 操作手册

- 管理端：http://localhost:5173（admin/admin123）
- 门户端：http://localhost:5174（merchant/merchant123）
- 关键流程：下单→支付→发货→签收→完成；售后；对账；报表；归档（触发 `POST /api/v1/orders/archive/run`）
- 演示数据：首次启动自动种子（`oms.seed.demo-data=false` 可关闭）

## 密钥与外部资源

- JWT Secret、数据库密码、渠道证书：`deploy/k8s/secret.example.yaml` 模板 + External Secrets。
- 真实渠道资质（支付商户号、平台权限、物流面单）：见 SCHEDULE 第 4 节与第 10 节清单。
