# deploy

本地开发环境编排与容器化产物。

## docker-compose（本地开发）

一键启动 MySQL、Redis、Nacos、RocketMQ、MinIO：

```bash
./scripts/dev-up.sh
```

访问入口：

| 组件 | 地址 |
| :--- | :--- |
| Nacos 控制台 | http://localhost:8848/nacos（nacos/nacos） |
| MinIO 控制台 | http://localhost:9001（minioadmin/minioadmin） |
| MySQL | localhost:3306（oms/oms123456，root/root123456） |
| Redis | localhost:6379（无密码） |

RocketMQ Dashboard 为可选组件，需要时启用：

```bash
./scripts/dev-up.sh --tools   # http://localhost:8088
```

> 若镜像拉取遇到 429（镜像加速器限流），稍等片刻重试 `./scripts/dev-up.sh` 即可；dashboard 不在核心链路内，不影响开发。

停止：`./scripts/dev-down.sh`。

## 镜像构建（后端服务）

```bash
docker build -f deploy/docker/Dockerfile \
  --build-arg MODULE=order-service \
  -t registry.example.com/oms/order-service:0.1.0 .
```

构建上下文为仓库根目录。镜像符合项目要求 7.5：多阶段构建、精简运行时、非 root 用户、`HEALTHCHECK`。

## k8s

生产 K8s 编排（Deployment/HPA/Ingress）在 `k8s/` 目录规划，随阶段 1 联调环境一并落地。
