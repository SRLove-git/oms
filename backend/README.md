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

## 配置说明

- 所有环境相关配置通过环境变量注入，默认值适配本地 docker-compose，见各服务 `application.yml`。
- Nacos 配置中心使用 `spring.config.import`（`optional:nacos:...`），Nacos 不可用时服务仍可本地启动。
- 数据库迁移由 Flyway 管理，脚本位于各服务 `src/main/resources/db/migration/`，禁止手工改库。

## 约定

- 包结构：`controller / service / mapper / entity / dto / constant`
- 接口：RESTful，统一 `/api/v1/**`，响应格式 `{code, message, data}`
- 表结构：小写下划线、单数表名、`id` 主键、`created_at/updated_at/deleted/version` 公共字段
