-- 订单服务：订单、订单明细、支付关联、订单日志

CREATE TABLE IF NOT EXISTS `order` (
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `order_no`       VARCHAR(64)     NOT NULL COMMENT '订单号',
    `merchant_id`    BIGINT UNSIGNED NOT NULL COMMENT '商户 ID',
    `customer_id`    BIGINT UNSIGNED DEFAULT NULL COMMENT '终端客户 ID',
    `order_type`     TINYINT         NOT NULL DEFAULT 1 COMMENT '1-B2B 2-B2C',
    `status`         TINYINT         NOT NULL DEFAULT 1 COMMENT '1-待支付 2-已支付 3-已审核 4-已发货 5-已签收 6-已完成 7-已取消',
    `total_amount`   DECIMAL(18, 2)  NOT NULL DEFAULT 0 COMMENT '订单总额',
    `pay_amount`     DECIMAL(18, 2)  NOT NULL DEFAULT 0 COMMENT '应付金额',
    `discount_amount` DECIMAL(18, 2) NOT NULL DEFAULT 0 COMMENT '优惠金额',
    `currency`       VARCHAR(8)      NOT NULL DEFAULT 'CNY',
    `warehouse_id`   BIGINT UNSIGNED DEFAULT NULL,
    `remark`         VARCHAR(512)    DEFAULT NULL,
    `paid_at`        DATETIME        DEFAULT NULL,
    `cancelled_at`   DATETIME        DEFAULT NULL,
    `timeout_at`     DATETIME        DEFAULT NULL COMMENT '待支付超时时间',
    `version`        INT             NOT NULL DEFAULT 0,
    `deleted`        TINYINT         NOT NULL DEFAULT 0,
    `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_merchant` (`merchant_id`),
    KEY `idx_customer` (`customer_id`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='订单表';

CREATE TABLE IF NOT EXISTS `order_item` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `order_id`      BIGINT UNSIGNED NOT NULL,
    `sku_id`        BIGINT UNSIGNED NOT NULL,
    `spu_id`        BIGINT UNSIGNED DEFAULT NULL,
    `sku_name`      VARCHAR(255)    NOT NULL,
    `quantity`      INT             NOT NULL DEFAULT 1,
    `unit_price`    DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    `total_price`   DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    `batch_no`      VARCHAR(64)     DEFAULT NULL COMMENT '批次号',
    `serial_no`     VARCHAR(64)     DEFAULT NULL COMMENT '序列号',
    `expire_at`     DATE            DEFAULT NULL COMMENT '效期',
    `version`       INT             NOT NULL DEFAULT 0,
    `deleted`       TINYINT         NOT NULL DEFAULT 0,
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_order` (`order_id`),
    KEY `idx_sku` (`sku_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='订单明细表';

CREATE TABLE IF NOT EXISTS `order_payment` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `order_id`        BIGINT UNSIGNED NOT NULL,
    `payment_no`      VARCHAR(64)     NOT NULL COMMENT '支付单号',
    `channel`         VARCHAR(32)     NOT NULL COMMENT '渠道：wechat/alipay/visa/mastercard/balance',
    `amount`          DECIMAL(18, 2)  NOT NULL,
    `currency`        VARCHAR(8)      NOT NULL DEFAULT 'CNY',
    `status`          TINYINT         NOT NULL DEFAULT 1 COMMENT '1-待支付 2-成功 3-失败 4-已退款',
    `channel_txn_no`  VARCHAR(128)    DEFAULT NULL COMMENT '渠道交易号',
    `paid_at`         DATETIME        DEFAULT NULL,
    `version`         INT             NOT NULL DEFAULT 0,
    `deleted`         TINYINT         NOT NULL DEFAULT 0,
    `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_payment_no` (`payment_no`),
    KEY `idx_order` (`order_id`),
    KEY `idx_channel_txn` (`channel_txn_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='订单支付表';

CREATE TABLE IF NOT EXISTS `order_log` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `order_id`    BIGINT UNSIGNED NOT NULL,
    `from_status` TINYINT         DEFAULT NULL,
    `to_status`   TINYINT         NOT NULL,
    `operator_id` BIGINT UNSIGNED DEFAULT NULL,
    `operator_name` VARCHAR(64)   DEFAULT NULL,
    `remark`      VARCHAR(512)    DEFAULT NULL,
    `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_order` (`order_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='订单状态流转日志表';
