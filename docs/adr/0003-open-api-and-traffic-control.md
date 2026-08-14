# ADR-0003：商城对接开放 API 与网关流量治理

- 状态：已接受（2026-08-14）
- 关联需求：项目要求 2.2（流量管控）、7.2（安全）、7.4（接口与集成）；商城对接诉求（OMS 与自营商城对接）

## 背景

1. OMS 需要与外部商城（自营商城/分销平台）对接：商城拉取商品与库存、下发订单、查询订单状态。与现有 integration-service（OMS 主动拉取天猫/京东）方向相反，需要一套 **外部调用 OMS** 的开放接口。
2. 项目要求 2.2 要求 Sentinel 流控落地；7.2 要求接口防重放、防篡改；7.4 要求接口版本化与幂等键。

## 决策

### 1. 开放 API 走网关统一验签，业务服务信任网关注入的头

- 路径前缀 `/api/v1/open/**`，HMAC-SHA256 签名：`sign = HMAC(method \n path \n timestamp \n nonce \n sha256(body))`。
- 请求头 `X-App-Id / X-Timestamp / X-Nonce / X-Sign`；时间戳窗口 ±300 秒 + nonce 一次性使用（进程内缓存，生产多副本前替换为 Redis 统一缓存）。
- 网关按 `oms.open-api.clients` 配置把 appId 映射为 `X-Merchant-Id` 注入下游；业务服务强制校验订单归属（租户隔离）。

### 2. 下单幂等以 `externalOrderNo` 为键

- `order` 表新增 `external_order_no`（唯一索引 `uk_external_order_no`）与 `source` 字段；归档表同步同构，冷热查询口径一致。
- 重复提交返回首次订单；并发重复由唯一索引兜底，重复单自动回滚并释放库存。

### 3. Sentinel 流控分级落地

- 网关：按路由 id + API 分组（支付回调单独分组）配置 QPS 流控与 RT 降级（`oms.sentinel.*`，启动时程序化加载）。
- 业务服务：`@SentinelResource` 打在订单创建/支付/回调等核心方法上，blockHandler 抛 503 业务异常；回调限流兜底为丢弃+日志（渠道侧重试）。
- sentinel-spring-cloud-gateway-adapter 无 Spring Boot 自动装配（仅 ServiceLoader），网关显式注册 `SentinelGatewayFilter` 与 `SentinelGatewayBlockExceptionHandler`。

### 4. OpenAPI 文档接入 springdoc

- 各服务 springdoc-openapi-starter-webmvc-ui / 网关 webflux-ui；网关聚合 Swagger UI（`/swagger-ui.html`），按服务分组展示，`/v3/api-docs` 与 `/swagger-ui` 路径免认证。

### 5. 核心链路并发安全加固

- 取消/超时取消/支付成功回调改为条件状态流转（`UPDATE ... WHERE status = 原状态`），防止并发覆盖。
- 支付成功回调增加金额校验，不一致忽略并告警；支付单创建幂等覆盖已支付状态；退款校验金额上限。

## 后果

- 商城接入无需 JWT，凭 appId/secret 即可对接；secret 需经 Secret/配置中心注入，泄露风险集中在网关配置。
- nonce 缓存为进程内实现，多副本部署前需替换为 Redis；时间戳窗口依赖网关与调用方时钟同步。
- 开放 API 订单与 OMS 内部订单同表存储，报表与审计天然统一；`source` 字段支撑来源维度分析。
- Sentinel 规则默认值面向本地/预发容量，生产阈值需结合压测在控制台/配置中心调优。
