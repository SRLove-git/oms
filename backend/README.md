# backend

OMS 后端 Monorepo，Maven 多模块工程。统一基于：

- JDK 21（`maven.compiler.release=21`）
- Spring Boot 4.1.0 / Spring Cloud 2025.1.2 / Spring Cloud Alibaba 2025.1.0.0
- MyBatis-Plus 3.5.17 + Flyway + MySQL 8

## 模块

| 模块 | 端口 | 职责 |
| :--- | :--- | :--- |
| `oms-common-core` | - | 统一响应、错误码、业务异常 |
| `oms-common-web` | - | 全局异常处理等 Web 公共能力 |
| `oms-common-redis` | - | RedisTemplate 序列化配置 |
| `oms-gateway` | 8080 | Spring Cloud Gateway 统一入口（认证/路由/限流） |
| `user-service` | 8081 | 用户、RBAC、商户入驻与资质 |
| `order-service` | 8082 | 订单生命周期、状态机、超时处理 |
| `inventory-service` | 8083 | SPU/SKU、库存预占/释放/流水 |
| `after-sales-service` | 8084 | 退货/换货/维修、退款 |
| `payment-center` | 8085 | 聚合支付、回调、对账 |
| `integration-service` | 8086 | 第三方电商平台与物流轨迹集成 |

## 本地启动

先启动基础设施（Nacos、MySQL、Redis、RocketMQ）：

```bash
docker compose -f ../deploy/docker-compose.yml up -d
```

启动单个服务（例如用户服务）：

```bash
mvn -pl user-service -am spring-boot:run
```

全部编译与测试：

```bash
mvn -B verify
```

## 阶段一（P0）已实现能力

| 服务 | 已实现接口 |
| :--- | :--- |
| `user-service` | 登录（JWT）、当前用户、用户管理、商户入驻/审核、资质上传/审核、审计日志 |
| `inventory-service` | SPU/SKU、仓库、入库、库存查询、**预占/释放/扣减/回补**（FEFO + 行级锁）、库存流水 |
| `order-service` | 下单（调库存预占）、订单详情/列表、取消、发起支付、审核、发货、签收、完成、**超时自动取消**（定时扫描） |
| `payment-center` | 聚合支付创建（mock/wechat/alipay 适配器）、**回调验签 + 幂等**、退款、支付成功通知订单 |
| `oms-gateway` | JWT 认证过滤器、用户上下文透传（`X-User-Id` 等） |

### 演示账号（首次启动自动种子，`oms.seed.demo-data=false` 可关闭）

- 平台管理员：`admin / admin123`
- 演示商户：`merchant / merchant123`

### 端到端冒烟

```bash
# 1. 启动基础设施
./scripts/dev-up.sh
# 2. 启动网关与 6 个业务服务（各开一个终端）
mvn -pl oms-gateway,user-service,order-service,inventory-service,after-sales-service,payment-center,integration-service -am spring-boot:run
# 3. 执行阶段一冒烟（下单 → 支付 → 审核 → 发货 → 签收 → 完成）
./scripts/e2e-smoke.sh
# 4. 执行阶段二冒烟（售后三类型 → 物流 → 集成 → 通知 → 对账）
./scripts/e2e-smoke-phase2.sh
```

> 支付默认 `mock` 渠道（`oms.payment.mock-only=true`），微信/支付宝适配器已预留，商户号与证书就绪后接入。
> 订单超时默认 30 分钟，可用 `OMS_ORDER_TIMEOUT_MINUTES` 调整。
> 售后/物流/第三方平台/通知均提供 mock 适配，真实渠道资质就绪后替换适配器即可。

## 阶段三（P2 报表与优化）已实现能力

### 报表接口（统一经网关 `/api/v1/reports/**` 访问，管理端需管理员 token）

| 服务 | 路径 | 说明 |
| :--- | :--- | :--- |
| order-service | `/api/v1/reports/sales/summary` | 经营汇总：订单数、支付金额、客单价、毛利、退款金额、复购率 |
| order-service | `/api/v1/reports/sales/trend` | 按日销售趋势 |
| order-service | `/api/v1/reports/sales/source` | 订单来源（B2B/B2C） |
| order-service | `/api/v1/reports/sales/daily` | 每日销售快照（定时生成，`OMS_REPORT_DAILY_CRON` 默认每日 01:10，启动时回填近 7 天） |
| inventory-service | `/api/v1/reports/inventory/warehouse-stock`、`stock-summary` | 仓库库存与汇总 |
| inventory-service | `/api/v1/reports/inventory/expiry-distribution` | 效期分布（已过期/0-90/91-180/181-365/365+） |
| inventory-service | `/api/v1/reports/inventory/turnover` | 库存周转 TOP（出库量/当前库存） |
| inventory-service | `/api/v1/reports/inventory/slow-moving` | 滞销预警（默认 90 天未动销且有库存） |
| payment-center | `/api/v1/reports/payments/channel-stats` | 渠道交易与退款率 |
| payment-center | `/api/v1/reports/payments/reconciliation-stats` | 对账差异统计 |
| after-sales-service | `/api/v1/reports/aftersales/type-stats`、`reason-distribution`、`repair-duration`、`return-rate` | 售后类型/原因/维修时长/退货率 |

所有报表接口均支持 CSV 导出：`/export?type=...`（如 `/api/v1/reports/sales/export?type=trend`），输出 UTF-8 BOM，Excel 可直接打开。

### 性能优化

- 销售/库存/支付报表结果缓存至 Redis（`oms:report:*`，默认 TTL 5 分钟），缓存异常自动降级。
- 报表查询索引通过各服务 Flyway 迁移落地（见 `docs/report/performance-test.md`）。
- 压测脚本：`scripts/benchmark.sh`；冒烟脚本：`scripts/report-smoke.sh`。

### 历史订单冷热分离

- 终态订单（已完成/已取消）超过 `OMS_ORDER_ARCHIVE_DAYS`（默认 180 天）后由定时任务归档至 `order_archive` 等冷表（批大小 `OMS_ORDER_ARCHIVE_BATCH_SIZE`，默认 200）。
- 手动触发：`POST /api/v1/orders/archive/run`（管理员）；历史单查询：`GET /api/v1/orders/archived`；订单详情自动回退冷表。
- 报表统计对热冷表做 UNION 聚合，归档不影响长周期口径。详见 `docs/adr/0002-cold-hot-order-archive.md`。

### 冒烟

```bash
# 阶段三报表冒烟（依赖阶段一/二冒烟产生的数据）
./scripts/report-smoke.sh
```

## 配置说明

- 所有环境相关配置通过环境变量注入，默认值适配本地 docker-compose，见各服务 `application.yml`。
- Nacos 配置中心使用 `spring.config.import`（`optional:nacos:...`），Nacos 不可用时服务仍可本地启动。
- 数据库迁移由 Flyway 管理，脚本位于各服务 `src/main/resources/db/migration/`，禁止手工改库。

## OpenAPI 文档与流控

- **OpenAPI（springdoc）**：各服务 `/v3/api-docs` + Swagger UI `/swagger-ui.html`；网关聚合入口 `http://localhost:8080/swagger-ui.html`（聚合 user/order/inventory/after-sales/payment/integration 六个服务，无需认证）。
- **Sentinel 流控**：网关按路由与 API 分组限流（下单 300 QPS、支付回调 1000 QPS 等），订单/支付服务在核心方法上以 `@SentinelResource` 限流与 RT 降级；阈值见各服务 `application.yml` 的 `oms.sentinel.*`，可连接 Sentinel 控制台（`SENTINEL_DASHBOARD`，默认 127.0.0.1:8858）动态调整。

## 商城对接开放 API

外部商城/分销平台通过 `/api/v1/open/**` 对接 OMS（HMAC-SHA256 签名 + 时间戳窗口 + nonce 防重放，appId 映射商户）：

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| GET | `/api/v1/open/products` | 在售商品分页（含实时可售库存） |
| GET | `/api/v1/open/products/{skuId}` | 商品详情 + 库存 |
| GET | `/api/v1/open/skus/{skuId}/stock` | 实时库存 |
| POST | `/api/v1/open/orders` | 下单（以 `externalOrderNo` 幂等） |
| GET | `/api/v1/open/orders/{externalOrderNo}` | 按外部订单号查单（自动回落归档表） |
| POST | `/api/v1/open/orders/{externalOrderNo}/cancel` | 取消待支付订单（释放库存） |

签名方案、客户端配置与冒烟脚本见 `docs/open-api.md` 与 `scripts/open-api-smoke.sh`。

## 约定

- 包结构：`controller / service / mapper / entity / dto / constant`
- 接口：RESTful，统一 `/api/v1/**`，响应格式 `{code, message, data}`
- 表结构：小写下划线、单数表名、`id` 主键、`created_at/updated_at/deleted/version` 公共字段
