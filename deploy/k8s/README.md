# k8s 生产编排

对应项目要求 7.5（容器化部署要求）与 SCHEDULE 阶段 5（K8s 灰度发布、回滚演练）。

## 目录

```text
deploy/k8s/
├── namespace.yaml            # oms 命名空间
├── configmap.yaml            # 公共环境变量（中间件地址、业务参数）
├── secret.example.yaml       # 密钥示例（勿提交真实值）
├── gateway.yaml              # Deployment + Service（网关，2 副本）
├── user-service.yaml         # Deployment + Service（1 副本）
├── order-service.yaml        # Deployment + Service（2 副本，HPA 扩至 8）
├── inventory-service.yaml    # Deployment + Service（1 副本）
├── after-sales-service.yaml  # Deployment + Service（1 副本）
├── payment-center.yaml       # Deployment + Service（2 副本，HPA 扩至 8）
├── integration-service.yaml  # Deployment + Service（1 副本）
├── hpa.yaml                  # 网关/订单/支付 HPA（CPU 70% 触发）
├── ingress.yaml              # 入口 + TLS（nginx-ingress 示例）
└── kustomization.yaml        # kustomize 编排入口
```

## 前置

1. 中间件（MySQL/Redis/Nacos/RocketMQ/MinIO）可部署在集群内或外部托管，`configmap.yaml` 中地址需与部署一致。
2. 镜像按 `registry.example.com/oms/<服务>:<git-commit-sha>` 打不可变 tag，禁止 `latest`。
3. 生产密钥用 Secret 管理：参考 `secret.example.yaml`，推荐 External Secrets / Sealed Secrets。
4. TLS 证书 Secret：`kubectl -n oms create secret tls oms-tls --cert=... --key=...`。

## 部署

```bash
# 构建镜像（示例）
docker build -f deploy/docker/Dockerfile --build-arg MODULE=order-service \
  -t registry.example.com/oms/order-service:$(git rev-parse --short HEAD) .
docker push registry.example.com/oms/order-service:$(git rev-parse --short HEAD)

# 通过 kustomize 覆盖镜像 tag 后发布
kubectl kustomize deploy/k8s | kubectl apply -f -
```

发布时用 `kustomize edit set image` 或 overlay 更新镜像 tag。

## 发布（滚动更新）

```bash
kubectl -n oms set image deployment/order-service \
  order-service=registry.example.com/oms/order-service:<新SHA>
kubectl -n oms rollout status deployment/order-service --timeout=180s
```

就绪探针（`/actuator/health`）通过后新副本才接流；失败自动回滚（`--record` 与 `rollout undo` 见下）。

## 灰度发布（可选，nginx-ingress canary）

1. 部署 canary 副本（独立 Deployment `order-service-canary`，selector 带 `canary=true`，少量副本）。
2. 创建独立 Service 指向 canary Pod。
3. Ingress 增加 canary 注解（`ingress.yaml` 中已注释示例）：

```yaml
nginx.ingress.kubernetes.io/canary: "true"
nginx.ingress.kubernetes.io/canary-weight: "10"   # 10% 流量到新版本
```

4. 观察监控（错误率、P99、慢 SQL、消息积压）无异常后逐步提高权重 → 100% 切流 → 移除 canary 注解 → 清理 canary 资源。

## 回滚

```bash
# 查看发布历史
kubectl -n oms rollout history deployment/order-service
# 回滚到上一版本
kubectl -n oms rollout undo deployment/order-service
# 回滚到指定版本
kubectl -n oms rollout undo deployment/order-service --to-revision=2
```

回滚触发条件：错误率 > 5%、核心接口 P99 > 500ms 持续 5 分钟、支付回调大面积失败、库存/对账数据异常。

## 验证

- 探针：全部 Deployment 就绪（`kubectl -n oms get deploy`）。
- 冒烟：网关暴露后执行 `./scripts/e2e-smoke.sh`、`./scripts/report-smoke.sh`。
- 监控：Prometheus target 全部 UP，告警规则加载（见 `deploy/monitoring/`）。
