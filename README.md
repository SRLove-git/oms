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

## 阶段一（P0 核心交易链路）已交付

- 认证与租户：JWT 登录、用户/商户/资质审核、审计日志
- 商品与库存：SPU/SKU、仓库、入库、FEFO 预占/释放/扣减/回补、库存流水
- 订单：下单 → 待支付 → 支付回调 → 审核 → 发货 → 签收 → 完成，超时自动取消并释放库存
- 支付：聚合支付适配器（mock/wechat/alipay），回调验签与幂等，退款
- 前端：管理端 9 个页面（订单/商品/库存/支付/商户/资质/用户/审计），门户端商品下单与我的订单
- 演示账号：管理端 `admin/admin123`，商户端 `merchant/merchant123`
- 端到端验证：`./scripts/e2e-smoke.sh`（下单 → 支付 → 发货 → 签收 → 完成全链路）

## 阶段二（P1 服务完善）已交付

- 售后服务：退货/换货/维修三类售后单（申请 → 审核 → 收货质检 → 退款/换货发运/维修 → 完成），维修工单与进度记录，退款联动支付中心、退货入库联动库存回补、订单售后状态联动
- 物流轨迹：承运商回调模拟（快递100 风格），按订单号/运单号查询轨迹
- 第三方平台集成：平台订单拉取与幂等映射、发货回传、售后单同步、库存同步（mock 通道，预留真实平台接入位）
- 消息通知：短信/邮件/站内信/微信模板四渠道，模板可配置，发送失败记录与重试
- 支付对账：渠道账单与本地流水逐笔比对、差异单自动生成与人工处理
- 前端：管理端新增售后服务/支付对账/物流轨迹/消息通知 4 个页面，商家门户新增我的售后（申请/取消）
- 端到端验证：`./scripts/e2e-smoke-phase2.sh`（售后三类型 → 物流 → 集成 → 通知 → 对账全链路）

> 第三方电商平台（天猫/京东）与真实支付/物流渠道为 mock 适配，商户号与平台权限就绪后替换适配器即可接入。

## 文档

- [backend/README.md](./backend/README.md)：后端模块说明与启动方式
- [frontend/README.md](./frontend/README.md)：前端工程说明
- [docs/adr/](./docs/adr/)：架构决策记录
- [docs/README.md](./docs/README.md)：文档索引

## 开发规范

- 分支模型：`main` / `develop` / `feature/*` / `hotfix/*` / `release/*`
- 提交信息：Conventional Commits
- 详细规范见 [CONTRIBUTING.md](./CONTRIBUTING.md) 与 [项目要求.md](./项目要求.md) 7.7 节
