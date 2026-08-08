# OMS 医疗设备订单管理系统

面向医疗器械行业的多租户订单管理系统（OMS），基于 Spring Cloud Alibaba 微服务架构，覆盖商品资质、订单、库存、支付、售后、物流等完整交易链路。

> 需求与排期详见 [项目要求.md](./项目要求.md) 与 [SCHEDULE.md](./SCHEDULE.md)。

## 技术栈

| 端 | 技术 |
| :--- | :--- |
| 管理端 | Vue 3 + TypeScript + Arco Design Vue + Vite + Pinia |
| 商家门户 | React 19 + TypeScript + Arco Design React + Vite |
| 后端 | Spring Boot 4.1 + Spring Cloud 2025.1 + Spring Cloud Alibaba 2025.1（JDK 21） |
| 中间件 | MySQL 8、Redis、RocketMQ、Nacos、MinIO（OSS 模拟） |
| 部署 | Docker / docker-compose（本地）、K8s（生产规划） |

## 仓库结构（Monorepo）

```text
oms/
├── backend/          # Maven 多模块：公共组件 + API 网关 + 6 个业务服务
├── frontend/         # pnpm workspace：admin（Vue3）+ portal（React）
├── docs/             # 架构文档与 ADR
├── deploy/           # docker-compose、Dockerfile、K8s 配置
└── scripts/          # 常用开发脚本
```

## 快速开始

### 1. 启动本地基础设施

```bash
docker compose -f deploy/docker-compose.yml up -d
```

启动 MySQL、Redis、Nacos、RocketMQ、MinIO 与 RocketMQ Dashboard。

### 2. 启动后端

要求：JDK 21+、Maven 3.9+。

```bash
cd backend
mvn -pl user-service -am spring-boot:run
```

网关默认端口 `8080`，各服务端口见 `backend/README.md`。

### 3. 启动前端

要求：Node.js 20+、pnpm 10+。

```bash
cd frontend
pnpm install
pnpm dev
```

管理端默认 `http://localhost:5173`，商家门户默认 `http://localhost:5174`，开发环境 API 代理到网关 `8080`。

## 文档

- [backend/README.md](./backend/README.md)：后端模块说明与启动方式
- [frontend/README.md](./frontend/README.md)：前端工程说明
- [docs/adr/](./docs/adr/)：架构决策记录
- [docs/README.md](./docs/README.md)：文档索引

## 开发规范

- 分支模型：`main` / `develop` / `feature/*` / `hotfix/*` / `release/*`
- 提交信息：Conventional Commits
- 详细规范见 [CONTRIBUTING.md](./CONTRIBUTING.md) 与 [项目要求.md](./项目要求.md) 7.7 节
