# 监控告警（Prometheus + Alertmanager + Grafana）

对应项目要求 7.5（可观测性）与 SCHEDULE 阶段 5（监控告警）。

## 本地一键启动（采集本机已运行的服务）

```bash
docker compose -f deploy/monitoring/docker-compose.monitoring.yml up -d
```

| 组件 | 地址 | 说明 |
| :--- | :--- | :--- |
| Prometheus | http://localhost:9090 | 指标采集 + 告警规则 |
| Alertmanager | http://localhost:9093 | 告警路由与通知 |
| Grafana | http://localhost:3000 | admin / admin123 |

服务已暴露 `/actuator/prometheus`（management 配置含 `prometheus`），采集器直接拉取 8080–8086。

## 告警规则

- `OmsServiceDown`：目标离线 2 分钟（critical）
- `OmsHighErrorRate`：5xx 错误率 5 分钟均值 > 5%（warning）
- `OmsCoreP99High`：核心接口 P99 5 分钟均值 > 500ms（warning，对应性能红线）
- `OmsHighCpu`：进程 CPU > 85% 持续 10 分钟
- `OmsOrderArchiveStuck`：订单服务下线导致超时/归档停摆提示

## 关键 Grafana 查询

- 核心接口 P99：`histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, job))`
- 请求量：`sum(rate(http_server_requests_seconds_count[5m])) by (job)`
- 5xx 率：`sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) by (job) / sum(rate(http_server_requests_seconds_count[5m])) by (job)`
- JVM 内存：`jvm_memory_used_bytes{area="heap"}`

## K8s 生产

- 建议使用 kube-prometheus-stack / Prometheus Operator（ServiceMonitor 采集），镜像仓库为 `prometheus-community/kube-prometheus-stack`。
- 日志：Filebeat → ELK；链路追踪：SkyWalking agent（见项目要求五）。
- 消息积压：RocketMQ 指标接入 Prometheus exporter 后补一条积压告警。
