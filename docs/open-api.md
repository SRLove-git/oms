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
| POST | `/api/v1/open/orders/{externalOrderNo}/cancel` | 取消待支付订单（自动释放库存） |

下单请求体：

```json
{
  "externalOrderNo": "M20260814001",
  "orderType": 2,
  "remark": "商城订单",
  "items": [
    { "skuId": 1001, "quantity": 2 }
  ]
}
```

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
    "totalAmount": 199.0,
    "currency": "CNY",
    "remark": "商城订单",
    "paidAt": null,
    "createdAt": "2026-08-14T12:00:01",
    "items": [ ... ]
  }
}
```

订单状态：`1-待支付 2-已支付 3-已审核 4-已发货 5-已签收 6-已完成 7-已取消`（与内部口径一致）。

## 5. 行为约定

- **幂等下单**：同一 `externalOrderNo` 重复提交返回首次创建的订单；并发重复由 `uk_external_order_no` 唯一索引兜底，重复单自动回滚并释放库存。
- **租户隔离**：查单/取消校验订单 `merchant_id` 与 appId 映射的商户一致，不一致返回 403。
- **审计**：开放 API 产生的订单状态流转，操作人统一记为 `OPEN_API`，全程进入订单日志。
- **状态只读反馈**：发货、签收、退款等结果由 OMS 内部流程驱动，商城通过查单接口轮询/回调订阅获取（回调推送在商城侧就绪后补充）。

## 6. 与第三方平台集成的区别

| 方向 | 模块 | 说明 |
| :--- | :--- | :--- |
| 平台 → OMS（已有） | integration-service | OMS 主动拉取天猫/京东平台订单、回传发货、同步库存（mock 适配器） |
| 商城 → OMS（本文档） | 网关 + order/inventory | 自营商城/分销平台通过开放 API 主动对接 OMS |

两类通道互相独立，商城侧接入不依赖第三方电商平台资质。
