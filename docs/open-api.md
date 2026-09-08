# 商城对接开放 API（Open API）

> 面向外部商城/分销平台的对接接口：商城通过开放 API 向 OMS 拉取商品与库存、下发订单、查询订单状态与取消订单。
> 网关统一负责验签、防重放与 appId → 商户映射，业务服务只感知可信的 `X-Merchant-Id` 头。

## 1. 概览

| 项 | 说明 |
| :--- | :--- |
| 入口 | 统一经 API 网关 `http://<gateway-host>:8080` |
| 路径前缀 | `/api/v1/open/**` |
| 认证 | HMAC-SHA256 签名（见下），无 JWT |
| 幂等 | 下单以 `externalOrderNo`（外部订单号）为幂等键，重复提交返回同一订单 |
| 租户 | appId 在网关映射为 `X-Merchant-Id`，服务端强制校验订单归属，杜绝越权 |

## 2. 签名方案

请求头：

| Header | 说明 |
| :--- | :--- |
| `X-App-Id` | 应用 ID（由 OMS 分配，如 `demo-mall`） |
| `X-Timestamp` | Unix 时间戳（秒），与服务器时间差超过 ±300 秒拒绝 |
| `X-Nonce` | 随机串（每次请求唯一，窗口内防重放） |
| `X-Sign` | 签名（小写 hex） |

待签字符串（`\n` 为换行符）：

```text
stringToSign = HTTP_METHOD + "\n"
             + PATH           + "\n"   // 原始路径，如 /api/v1/open/orders
             + TIMESTAMP      + "\n"
             + NONCE          + "\n"
             + SHA256_HEX(BODY)        // 无 body 时为 SHA256_HEX("")
signature    = Hex(HMAC_SHA256(secret, stringToSign))
```

示例（Bash + OpenSSL）：

```bash
METHOD=POST
PATH=/api/v1/open/orders
BODY='{"externalOrderNo":"M20260814001","items":[{"skuId":1,"quantity":2}]}'
TS=$(date +%s)
NONCE=$(openssl rand -hex 8)
SIGN=$(printf '%s\n%s\n%s\n%s\n%s' "$METHOD" "$PATH" "$TS" "$NONCE" \
  "$(printf '%s' "$BODY" | openssl dgst -sha256 -hex | awk '{print $2}')" \
  | openssl dgst -sha256 -hmac "$SECRET" -hex | awk '{print $2}')

curl -s -X "$METHOD" "http://localhost:8080$PATH" \
  -H "Content-Type: application/json" \
  -H "X-App-Id: $APP_ID" \
  -H "X-Timestamp: $TS" \
  -H "X-Nonce: $NONCE" \
  -H "X-Sign: $SIGN" \
  -d "$BODY"
```

完整可执行冒烟见 `scripts/open-api-smoke.sh`。

## 3. 客户端配置（网关）

```yaml
oms:
  open-api:
    enabled: true
    timestamp-window-seconds: 300
    clients:
      demo-mall:
        secret: demo-mall-secret-change-me   # 生产走 OMS_OPEN_API_DEMO_MALL_SECRET 环境变量/Secret
        merchant-id: 1                        # 该商城在 OMS 的商户 ID
        enabled: true
```

> 生产环境建议：secret 通过 K8s Secret/配置中心注入；nonce 防重放缓存替换为 Redis 统一实现（当前为进程内实现，多副本部署前需升级）。

## 4. 接口清单

### 4.1 商品与库存（inventory-service）

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| GET | `/api/v1/open/products` | 在售 SKU 分页（含实时可售库存），参数 `keyword/page/size` |
| GET | `/api/v1/open/products/{skuId}` | 单个 SKU 详情 + 实时库存（下架返回 409） |
| GET | `/api/v1/open/skus/{skuId}/stock` | 实时库存查询 |

### 4.2 订单（order-service）

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| POST | `/api/v1/open/orders` | 下单（幂等，`externalOrderNo` 唯一） |
| GET | `/api/v1/open/orders/{externalOrderNo}` | 按外部订单号查单（含明细；归档单自动回落冷表） |
| POST | `/api/v1/open/orders/{externalOrderNo}/payment-notify` | 商城收款完成后的**支付成功通知**（订单待支付 → 已支付 + 库存扣减） |
| POST | `/api/v1/open/orders/{externalOrderNo}/cancel` | 取消待支付订单（自动释放库存） |

下单请求体：

```json
{
  "externalOrderNo": "M20260814001",
  "orderType": 2,
  "remark": "商城订单",
  "consignee": "张三",
  "phone": "+65 8123 4567",
  "address": "新加坡示例路 1 号",
  "deliveryFee": 9.90,
  "items": [
    { "skuId": 1001, "quantity": 2 }
  ]
}
```

请求字段：

| 字段 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `externalOrderNo` | string | 是 | 外部订单号，全局唯一，幂等键 |
| `orderType` | int | 否 | 默认 `1`，商城零售订单传 `2`（B2C） |
| `remark` | string | 否 | 备注 |
| `consignee` | string | 否 | 收货人（OMS 发货使用，建议必填） |
| `phone` | string | 否 | 收货电话（建议必填） |
| `address` | string | 否 | 收货地址（建议必填） |
| `deliveryFee` | decimal | 否 | 配送费，默认 `0`，不能为负；计入订单总额与应付金额 |
| `items` | array | 是 | 订单明细 |
| `items[].skuId` | long | 是 | OMS SKU ID |
| `items[].quantity` | int | 是 | 购买数量 |

下单响应（`code=0` 成功）：

```json
{
  "code": 0,
  "message": "成功",
  "data": {
    "orderNo": "O2026081412000112345",
    "externalOrderNo": "M20260814001",
    "source": "OPEN_API",
    "orderType": 2,
    "status": 1,
    "totalAmount": 208.90,
    "currency": "SGD",
    "remark": "商城订单",
    "consignee": "张三",
    "phone": "+65 8123 4567",
    "address": "新加坡示例路 1 号",
    "deliveryFee": 9.90,
    "paidAt": null,
    "createdAt": "2026-08-14T12:00:01",
    "items": [ ... ]
  }
}
```

金额口径：`totalAmount`（订单总额）= 商品总额 + `deliveryFee`，支付成功通知的 `amount` 必须与该 `totalAmount` 一致。

订单状态：`1-待支付 2-已支付 3-已审核 4-已发货 5-已签收 6-已完成 7-已取消`（与内部口径一致）。

### 4.3 支付成功通知（商城收款 → OMS）

商城面向终端客户自行收款（微信/支付宝/余额等，渠道由商城决定），收款成功后调用本接口通知 OMS，OMS 将订单推进为已支付并完成库存物理扣减。

```http
POST /api/v1/open/orders/{externalOrderNo}/payment-notify
Content-Type: application/json
X-App-Id: demo-mall
X-Timestamp: ...
X-Nonce: ...
X-Sign: ...

{
  "paymentNo": "MP20260814001",
  "amount": 199.00,
  "channel": "wechat",
  "channelTxnNo": "4200001234567890",
  "paidAt": "2026-08-14T12:05:00"
}
```

| 字段 | 必填 | 说明 |
| :--- | :--- | :--- |
| `paymentNo` | 是 | 商城支付流水号，全局唯一，作为幂等键 |
| `amount` | 是 | 实收金额，必须与订单应付金额一致（应付金额 = 商品总额 + 配送费，即下单响应中的 `totalAmount`），否则 409 |
| `channel` | 否 | 商城收款渠道（wechat/alipay/balance 等），默认 `OPEN_API` |
| `channelTxnNo` | 否 | 渠道交易号（对账用） |
| `paidAt` | 否 | 支付完成时间，默认服务器当前时间 |

行为约定：

- **幂等**：同一 `paymentNo` 或订单已处于已支付及之后状态时，重复通知直接返回当前订单，不重复扣减库存。
- **金额校验**：通知金额与订单应付金额不一致 → 409 拒绝并告警，订单保持待支付（超时自动取消释放库存）。
- **状态约束**：已取消订单不可支付（409）；待支付之外且未取消的订单视为重复通知幂等处理。
- **库存联动**：通知成功后按 FEFO 完成库存扣减并记录流水；支付记录写入 `order_payment`（`channel` 按上述规则），来源 `source=OPEN_API` 便于报表区分。
- 商城侧收款失败/用户放弃支付时，无需通知 OMS；订单按超时策略自动取消并释放库存。

## 5. 行为约定

- **幂等下单**：同一 `externalOrderNo` 重复提交返回首次创建的订单；并发重复由 `uk_external_order_no` 唯一索引兜底，重复单自动回滚并释放库存。
- **支付由商城发起**：OMS 不向商城开放支付发起接口，商城自行收款后经 `payment-notify` 通知（见 4.3），通知幂等、金额强校验。
- **租户隔离**：查单/取消/支付通知校验订单 `merchant_id` 与 appId 映射的商户一致，不一致返回 403。
- **审计**：开放 API 产生的订单状态流转，操作人统一记为 `OPEN_API`，全程进入订单日志。
- **状态反馈**：发货、签收、退款等结果由 OMS 内部流程驱动，商城侧可同时通过查单接口（`GET /api/v1/open/orders/{externalOrderNo}`）**轮询兜底**（建议间隔 ≥ 5 秒）与 OMS → 商城**主动回调**（见第 7 节）获取；轮询与回调并存，任一路失败都可被另一路兜底。

## 6. 与第三方平台集成的区别

| 方向 | 模块 | 说明 |
| :--- | :--- | :--- |
| 平台 → OMS（已有） | integration-service | OMS 主动拉取天猫/京东平台订单、回传发货、同步库存（mock 适配器） |
| 商城 → OMS（本文档） | 网关 + order/inventory | 自营商城/分销平台通过开放 API 主动对接 OMS |

两类通道互相独立，商城侧接入不依赖第三方电商平台资质。

## 7. 商城事件回调（OMS → 商城）

> 已实施：OMS 订单/售后状态变化时主动通知商城，轮询仍保留为兜底。

### 7.1 事件模型（当前已投递）

| 事件 | 触发点 | data 关键字段 |
| :--- | :--- | :--- |
| `order.shipped` | 订单发货 | `orderNo`、`externalOrderNo`、`status`、`carrier`、`trackingNo`、`logisticsStatus` |
| `order.signed` | 确认签收 | `orderNo`、`externalOrderNo`、`status` |
| `order.completed` | 完成订单 | `orderNo`、`externalOrderNo`、`status` |
| `order.after_sales` | 售后介入 | `orderNo`、`externalOrderNo`、`status` |
| `aftersale.updated` | 售后状态变更 | `orderNo`、`externalOrderNo`、`returnNo`、`omsReturnNo`、`afterSaleStatus` |

> 说明：`order.paid` 由商城收款后主动调用 `payment-notify` 触发，无需 OMS 回推；OMS 超时取消暂以商城轮询兜底。

### 7.2 推送协议

- **方向**：OMS → 商城，回调地址由商城提供（OMS 侧配置 `oms.callback.base-url`）。
- **报文**：`POST {base-url}/api/v1/callback/oms`，请求体信封：
  ```json
  {
    "eventId": "uuid",
    "event": "order.shipped",
    "appId": "oms",
    "timestamp": "2026-09-08T03:00:00Z",
    "data": { "orderNo": "...", "externalOrderNo": "...", "status": 4 }
  }
  ```
- **签名**：OMS 按第 2 节同款 HMAC-SHA256 方案签名，请求头 `X-App-Id / X-Timestamp / X-Nonce / X-Sign`；商城验签通过后返回 `{"code":0}` 视为成功。
- **幂等**：`eventId` 全局唯一，商城按 `eventId` 去重，重复投递返回成功。

### 7.3 可靠性

- **outbox**：状态流转与事件写入同一本地事务，避免状态已变但事件漏发。
- **重试**：定时任务扫描 `open_api_event`，失败按 1s/5s/30s/5min/30min 指数退避重试，最多重试 5 次，超限置失败（死信）并告警。
- **兜底**：回调失败不影响 OMS 内部状态流转；商城侧始终可查单接口兜底核对（轮询与回调并存）。
- **审计**：事件投递与重试记录 `retry_count/last_error/status`，可追溯、可对账。

### 7.4 工程落地

- OMS：`order-service` 与 `after-sales-service` 各自维护 `open_api_event` outbox 表（Flyway 迁移），`MallCallbackNotifier` 入队、`OpenApiCallbackDispatcher` 投递。
- 商城：`POST /api/v1/callback/oms` 验签（时间戳窗口 + nonce 防重放 + HMAC）并按 `eventId` 去重后回写本地订单状态。
