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
# 2. 启动网关与 4 个核心服务（各开一个终端）
mvn -pl oms-gateway,user-service,order-service,inventory-service,payment-center -am spring-boot:run
# 3. 执行冒烟（下单 → 支付 → 审核 → 发货 → 签收 → 完成）
./scripts/e2e-smoke.sh
```

> 支付默认 `mock` 渠道（`oms.payment.mock-only=true`），微信/支付宝适配器已预留，商户号与证书就绪后接入。
> 订单超时默认 30 分钟，可用 `OMS_ORDER_TIMEOUT_MINUTES` 调整。

## 配置说明

- 所有环境相关配置通过环境变量注入，默认值适配本地 docker-compose，见各服务 `application.yml`。
- Nacos 配置中心使用 `spring.config.import`（`optional:nacos:...`），Nacos 不可用时服务仍可本地启动。
- 数据库迁移由 Flyway 管理，脚本位于各服务 `src/main/resources/db/migration/`，禁止手工改库。

## 约定

- 包结构：`controller / service / mapper / entity / dto / constant`
- 接口：RESTful，统一 `/api/v1/**`，响应格式 `{code, message, data}`
- 表结构：小写下划线、单数表名、`id` 主键、`created_at/updated_at/deleted/version` 公共字段
