# OMS 医疗设备订单管理系统

面向医疗器械行业的多租户订单管理系统（OMS），基于 Spring Cloud Alibaba 微服务架构，覆盖商品资质、订单、库存、支付、售后、物流等完整交易链路。

> 需求与排期详见 [项目要求.md](./项目要求.md) 与 [SCHEDULE.md](./SCHEDULE.md)。

## 技术栈

| 端 | 技术 |
| :--- | :--- |
| 管理端 | Vue 3 + TypeScript + Arco Design Vue + Vite + Pinia |
| 商家门户 | React 19 + TypeScript + Arco Design React + Vite |
| 移动端 H5 | Vue 3 + TypeScript + Arco Design Vue + Vite（移动优先布局） |
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

管理端默认 `http://localhost:5173`，商家门户默认 `http://localhost:5174`，移动端 H5 默认 `http://localhost:5175`，开发环境 API 代理到网关 `8080`。

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

## 阶段三（P2 报表与优化）已交付

- 报表中心：销售（汇总/趋势/来源/每日快照）、库存（仓库库存/效期分布/周转 TOP/滞销预警）、支付（渠道交易/对账差异）、售后（类型/原因分布/维修时长/退货率）
- 数据大盘：管理端首页升级为经营大盘（KPI + 销售趋势/效期分布/支付渠道/售后类型）
- 报表导出：所有报表支持 CSV 导出（UTF-8 BOM，Excel 直接打开）
- 历史订单冷热分离：终态订单超阈值自动归档至冷表，订单详情与报表口径不受影响（详见 `docs/adr/0002-cold-hot-order-archive.md`）
- 性能优化：报表查询 Redis 缓存（5 分钟 TTL）、报表索引落地、库存扣减流水口径修正
- 验证脚本：`scripts/report-smoke.sh`（报表冒烟）、`scripts/benchmark.sh`（读侧压测），压测说明见 `docs/report/performance-test.md`

## 阶段四（测试与验收）已交付

- SIT 用例套件：`scripts/sit-test.sh`（29 项用例，覆盖认证权限、交易主链路、超时、售后、对账、报表、归档、审计）
- 安全冒烟：`scripts/security-test.sh`（17 项用例：认证绕过、越权、注入、畸形输入、敏感端点）
- 性能压测：`N=5000 C=100 ./scripts/benchmark.sh`，P99 55–192ms，全部达标
- 合规预评审与验收报告：见 [docs/sit/](./docs/sit/)（SIT 用例、安全报告、压测报告、合规评审、UAT 计划、验收报告）
- 验收结论：满足《项目要求》7.8 六项标准（真实渠道/生产环境类项列入 M6 上线前置清单）

## 阶段五（上线与护航）已交付

- K8s 生产编排：[deploy/k8s/](./deploy/k8s/)（7 服务 Deployment/Service + HPA + Ingress + kustomize），含灰度发布与回滚说明
- 监控告警：[deploy/monitoring/](./deploy/monitoring/)（Prometheus + Alertmanager + Grafana，7 个采集目标 UP、5 条告警规则）
- 回滚演练脚本：`scripts/k8s-rollback-drill.sh`
- 护航与交接文档：[docs/ops/](./docs/ops/)（上线演练手册、值班 Runbook、文档交接、项目复盘）
- 修复：各服务补充 `micrometer-registry-prometheus` 依赖，`/actuator/prometheus` 指标端点可用
- 说明：M6 以“就绪 + 本地/预发演练”口径达成；真实生产部署（生产集群、真实渠道资质、渗透测试）列入上线检查清单跟踪

## 阶段六（工程完善）已交付

- 前端体验：管理端/门户端/移动端接入 **i18n 中英文切换**（vue-i18n / react-i18next，Arco locale 同步）与 **亮/暗主题切换**（`body[arco-theme]`，持久化）
- 移动端 H5：[frontend/mobile/](./frontend/mobile/)（商品搜索/详情/下单/支付、订单状态操作、售后申请、我的；5175 端口）
- OpenAPI 文档：全服务 springdoc，网关聚合 Swagger UI `http://localhost:8080/swagger-ui.html`（免认证）
- Sentinel 流控：网关路由/API 分组限流（下单 300 QPS、支付回调 1000 QPS 等）+ 订单/支付核心方法 `@SentinelResource` 限流与 RT 降级（详见 `docs/adr/0003-open-api-and-traffic-control.md`）
- 商城对接开放 API：`/api/v1/open/**`（HMAC 签名 + 防重放 + appId→商户映射；商品/库存查询、幂等下单、支付成功通知、查单、取消），文档 [docs/open-api.md](./docs/open-api.md)，冒烟 `scripts/open-api-smoke.sh`；状态反馈当前为**查单轮询**，回调订阅协议已预留（见文档第 7 节，下一迭代实施）
- 核心链路加固：取消/超时/支付回调条件状态流转防并发覆盖，回调金额校验，支付单幂等覆盖已支付态，退款金额校验（详见 `docs/adr/0003`）
- 前端测试：管理端/门户端/移动端接入 Vitest（组件/状态/i18n 用例，共 27 例）
- 后端测试补强：订单/库存/支付核心链路单测扩充（订单 35、库存 14、支付 17、网关 7，全量 83 用例通过）

## 支付补强（国际卡 / 部分支付 / 余额）已交付

- 国际卡 PSP：新增 Visa / Mastercard 渠道适配器（mock 通道，真实 PSP 证书就绪后替换内部实现）
- 部分支付：订单支持多笔支付（定金 + 尾款），支付中心校验待支付金额，订单服务累计已付金额，付清后扣减库存
- 余额支付：新增商户余额账户与流水，支持充值、余额支付、余额退款回充
- 相关说明见 [backend/README.md](./backend/README.md) 与 [支付中心适配器目录](./backend/payment-center/src/main/java/com/oms/payment/adapter/)

## 文档

- [backend/README.md](./backend/README.md)：后端模块说明与启动方式
- [frontend/README.md](./frontend/README.md)：前端工程说明
- [docs/adr/](./docs/adr/)：架构决策记录
- [docs/README.md](./docs/README.md)：文档索引

## 开发规范

- 分支模型：`main` / `develop` / `feature/*` / `hotfix/*` / `release/*`
- 提交信息：Conventional Commits
- 详细规范见 [CONTRIBUTING.md](./CONTRIBUTING.md) 与 [项目要求.md](./项目要求.md) 7.7 节
