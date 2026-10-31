-- 保存支付渠道侧商户订单号（YeahPay thirdOrderId），退款时与 leshuaOrderId 一起校验原交易。

ALTER TABLE `order_payment`
    ADD COLUMN `provider_order_no` VARCHAR(64) DEFAULT NULL AFTER `channel_txn_no`;

CREATE INDEX `idx_order_payment_provider_order`
    ON `order_payment` (`provider_order_no`);
