# YeahPay 原路退款

实现依据：YeahPay Trade API 的 **4.6 退款查询**（`/order/queryRefund`）和
**4.9 统一撤销或退款**（`/unified/reversalOperation`）。

文档入口：<https://payment.yeahpay.net/api/#/apiDocCenter?type=trade-api>

## 当前范围

- `sceneType=1`，用于商城 H5/PC 收银台及扫码支付订单。
- 生产先开放整单全额退款；按文档不传 `amount` 表示全额退款。
- `merchantRefundId` 使用 OMS 退款单号，16-64 位字母或数字，并在渠道结果不确定时原号重查，禁止生成新单重复退款。
- 一旦开始请求渠道，OMS 会先持久化该退款单号；即使网络中断或响应无法解析，也保留为“退款中”，后续只用原号查询，不生成新号。
- 商城支付通知会同时保存 YeahPay 的 `thirdOrderId`（渠道侧商户订单号）和 `leshuaOrderId`（渠道交易号）；直连钱包不会误用 OMS 内部订单号。
- 发起前先调用退款查询。YeahPay 状态 `10`/`3` 保持“退款中”，`11`/`2` 才记“退款成功”，`12`/`1` 记“退款失败”。
- OMS 只有在渠道明确成功后才完成售后单和支付流水；网络超时或渠道处理中不会显示“退款成功”。
- 同一订单已有成功退款记录，或支付流水已经标记为已退款时，系统会拒绝第二次退款。
- 对已创建且状态为“退款中”的退款单，售后服务每分钟用同一个 `merchantRefundId` 自动复查渠道终态；不会自动给尚未点击退款的售后单发起退款。

## 生产配置

支付中心使用以下环境变量：

```text
OMS_YEAHPAY_ENABLED=true
OMS_YEAHPAY_APP_ID=...
OMS_YEAHPAY_API_KEY=...
OMS_YEAHPAY_MERCHANT_ID=...
OMS_YEAHPAY_API_BASE_URL=https://open-api.yeahpay.sg/acceptance/acceptance-open-api
OMS_YEAHPAY_SIGNATURE_DOMAIN=https://open-api.yeahpay.sg/acceptance
```

部署脚本从同机 `buzud-server` 容器已有的 `IKEA_YEAHPAY_*` 生产配置安全映射到
OMS 环境文件，不向 GitHub Actions 输出或传输凭据。发布后通过只读的商户支付方式查询验证
签名、凭据和网络连通性；该验证不会创建交易或退款。

## 验收边界

配置校验和自动化测试只证明接口与状态机已接通。真实资金退款仍需使用一笔已成功的小额
YeahPay 交易，在 OMS 完成售后审核与退款操作，并同时核对 YeahPay 商户后台、OMS 退款单、
商城订单状态和付款账户入账。
