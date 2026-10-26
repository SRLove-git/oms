# J&T Express Singapore API 对接

J&T Express SG 承运商适配器位于 `integration-service`，商城不直接保存极兔凭据。
管理端订单发货选择 `J&T Express SG` 且不填写现有运单号时，会先创建极兔运单，
再以返回的 `tracking_id` 完成 OMS 发货；现有 OMS → 商城回调继续同步承运商、运单号和状态。

## 支持能力

- Basic 登录换取 JWT，并在进程内缓存（官方 token 有效期 7 天，本地提前 1 小时刷新）。
- 只读连通性检查：`GET /api/v1/logistics/jtexpress/verify?serviceCode=PUDO`，验证账号并返回网点数量。
- 创建国内运单：`POST /api/v1/logistics/jtexpress/shipments`。
- 查询轨迹：`GET /api/v1/logistics/jtexpress/track/{trackingId}`。
- 取消运单：`POST /api/v1/logistics/jtexpress/cancel`。
- 获取 A4/A6 面单地址：`GET /api/v1/logistics/jtexpress/connote/{trackingId}?style=a6`。
- 接收并校验 webhook：`POST /api/v1/logistics/jtexpress/webhook`。
- OMS 原有物流查询遇到承运商 `J&T Express SG` 时，自动从极兔同步轨迹。
- 极兔 webhook 落库后通过 OMS outbox 推送商城，商城订单页继续展示最新轨迹。

官方参考：[J&T Express Singapore API 文档](https://jts.jtexpress.sg/docs/)。

## 环境变量

```dotenv
# 默认关闭，防止本地或测试环境误建真实运单
OMS_JTEXPRESS_ENABLED=false

# Staging
OMS_JTEXPRESS_BASE_URL=https://demoedishops.jtexpress.sg/jts-service-doorstep
# Production（完成 staging 验收后切换）
# OMS_JTEXPRESS_BASE_URL=https://edishops.jtexpress.sg/jts-service-doorstep

OMS_JTEXPRESS_EMAIL=your-api-login-email
OMS_JTEXPRESS_PASSWORD=your-api-login-password
OMS_JTEXPRESS_MERCHANT_CODE=
OMS_JTEXPRESS_SERVICE_CODE=DOM123
OMS_JTEXPRESS_WEBHOOK_SECRET=replace-with-a-random-secret
```

`email/password` 是 J&T API 登录凭据。若账号只开通了 VIP 后台、尚未开通 API，需让 J&T
为账号开启 API 权限。凭据只能通过运行环境 Secret 注入，不得提交到 Git。

## 联调顺序

1. 配置 staging URL、API 凭据与 webhook secret，保持生产 URL 不变。
2. 启动 OMS 后，以管理员 JWT 请求 `GET /api/v1/logistics/jtexpress/status`，确认
   `enabled=true`、`configured=true`、`environment=staging`。
   再请求 `GET /api/v1/logistics/jtexpress/verify?serviceCode=PUDO`，确认凭证可登录且能读取网点。
3. 在 J&T 后台将 webhook 地址设为
   `https://<oms-domain>/api/v1/logistics/jtexpress/webhook`，secret 与环境变量一致。
4. 用一笔测试订单创建运单，核对 tracking id、A6 面单、轨迹回调和商城订单页。
5. 经 J&T 确认后切生产 URL，再单独做一笔小流量真实运单验收。

部分 J&T 新加坡账号只开通 production：若 staging 返回 `username error`、production 能正常
取得 token，应按 production-only 账号处理，先执行网点列表等只读验证，再创建首笔测试运单。

## 安全边界

- 集成默认关闭；代码测试只使用本地模拟 HTTP 服务，不会调用 J&T 创建真实运单。
- webhook 仅精确放行一个路径，并按官方规则使用 `HMAC-SHA1(request-id, secret)` 校验。
- API 错误不会记录账号密码或 webhook 完整载荷。
