-- 商城开放 API：收货信息与配送费（对接规范 §13-1 / §13-2）
-- 收货信息：商城侧下单透传收货人/电话/地址，OMS 发货使用；
-- 配送费：计入订单总额与应付金额，支付成功通知金额 = 商品总额 + 配送费。

ALTER TABLE `order`
    ADD COLUMN `consignee`    VARCHAR(64)   DEFAULT NULL COMMENT '收货人' AFTER `remark`,
    ADD COLUMN `phone`        VARCHAR(32)   DEFAULT NULL COMMENT '收货电话' AFTER `consignee`,
    ADD COLUMN `address`      VARCHAR(255)  DEFAULT NULL COMMENT '收货地址' AFTER `phone`,
    ADD COLUMN `delivery_fee` DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '配送费（计入应付金额）' AFTER `address`;

ALTER TABLE `order_archive`
    ADD COLUMN `consignee`    VARCHAR(64)   DEFAULT NULL COMMENT '收货人' AFTER `remark`,
    ADD COLUMN `phone`        VARCHAR(32)   DEFAULT NULL COMMENT '收货电话' AFTER `consignee`,
    ADD COLUMN `address`      VARCHAR(255)  DEFAULT NULL COMMENT '收货地址' AFTER `phone`,
    ADD COLUMN `delivery_fee` DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '配送费（计入应付金额）' AFTER `address`;
