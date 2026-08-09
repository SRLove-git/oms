-- 阶段3：报表支持与历史订单冷热分离

ALTER TABLE `order_item`
    ADD COLUMN `cost_amount` DECIMAL(18, 2) NOT NULL DEFAULT 0 COMMENT '成本金额快照（下单时取自 SKU 成本价）' AFTER `total_price`;

ALTER TABLE `order`
    ADD KEY `idx_paid_at` (`paid_at`),
    ADD KEY `idx_status_paid` (`status`, `paid_at`);

ALTER TABLE `order_item`
    ADD KEY `idx_order_sku` (`order_id`, `sku_id`);

ALTER TABLE `order_payment`
    ADD KEY `idx_status_updated` (`status`, `updated_at`);

-- 历史订单归档表（与热表结构一致，额外记录归档时间）
CREATE TABLE IF NOT EXISTS `order_archive` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `order_no`        VARCHAR(64)     NOT NULL,
    `merchant_id`     BIGINT UNSIGNED NOT NULL,
    `customer_id`     BIGINT UNSIGNED DEFAULT NULL,
    `order_type`      TINYINT         NOT NULL DEFAULT 1,
    `status`          TINYINT         NOT NULL DEFAULT 1,
    `total_amount`    DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    `pay_amount`      DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    `discount_amount` DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    `currency`        VARCHAR(8)      NOT NULL DEFAULT 'CNY',
    `warehouse_id`    BIGINT UNSIGNED DEFAULT NULL,
    `remark`          VARCHAR(512)    DEFAULT NULL,
    `paid_at`         DATETIME        DEFAULT NULL,
    `cancelled_at`    DATETIME        DEFAULT NULL,
    `timeout_at`      DATETIME        DEFAULT NULL,
    `version`         INT             NOT NULL DEFAULT 0,
    `deleted`         TINYINT         NOT NULL DEFAULT 0,
    `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `archived_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '归档时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_merchant` (`merchant_id`),
    KEY `idx_customer` (`customer_id`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`),
    KEY `idx_paid_at` (`paid_at`),
    KEY `idx_status_paid` (`status`, `paid_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='历史订单归档表';

CREATE TABLE IF NOT EXISTS `order_item_archive` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `order_id`      BIGINT UNSIGNED NOT NULL,
    `sku_id`        BIGINT UNSIGNED NOT NULL,
    `spu_id`        BIGINT UNSIGNED DEFAULT NULL,
    `sku_name`      VARCHAR(255)    NOT NULL,
    `quantity`      INT             NOT NULL DEFAULT 1,
    `unit_price`    DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    `total_price`   DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    `cost_amount`   DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    `batch_no`      VARCHAR(64)     DEFAULT NULL,
    `serial_no`     VARCHAR(64)     DEFAULT NULL,
    `expire_at`     DATE            DEFAULT NULL,
    `version`       INT             NOT NULL DEFAULT 0,
    `deleted`       TINYINT         NOT NULL DEFAULT 0,
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_order` (`order_id`),
    KEY `idx_sku` (`sku_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='历史订单明细归档表';

CREATE TABLE IF NOT EXISTS `order_log_archive` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `order_id`      BIGINT UNSIGNED NOT NULL,
    `from_status`   TINYINT         DEFAULT NULL,
    `to_status`     TINYINT         NOT NULL,
    `operator_id`   BIGINT UNSIGNED DEFAULT NULL,
    `operator_name` VARCHAR(64)     DEFAULT NULL,
    `remark`        VARCHAR(512)    DEFAULT NULL,
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_order` (`order_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='历史订单日志归档表';

CREATE TABLE IF NOT EXISTS `order_payment_archive` (
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `order_id`       BIGINT UNSIGNED NOT NULL,
    `payment_no`     VARCHAR(64)     NOT NULL,
    `channel`        VARCHAR(32)     NOT NULL,
    `amount`         DECIMAL(18, 2)  NOT NULL,
    `currency`       VARCHAR(8)      NOT NULL DEFAULT 'CNY',
    `status`         TINYINT         NOT NULL DEFAULT 1,
    `channel_txn_no` VARCHAR(128)    DEFAULT NULL,
    `paid_at`        DATETIME        DEFAULT NULL,
    `version`        INT             NOT NULL DEFAULT 0,
    `deleted`        TINYINT         NOT NULL DEFAULT 0,
    `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_payment_no` (`payment_no`),
    KEY `idx_order` (`order_id`),
    KEY `idx_status_updated` (`status`, `updated_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='历史订单支付归档表';

-- 每日销售报表快照（定时生成）
CREATE TABLE IF NOT EXISTS `report_daily_sales` (
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `biz_date`         DATE            NOT NULL COMMENT '业务日期',
    `order_count`      INT             NOT NULL DEFAULT 0 COMMENT '当日订单数',
    `paid_order_count` INT             NOT NULL DEFAULT 0 COMMENT '当日支付订单数',
    `paid_amount`      DECIMAL(18, 2)  NOT NULL DEFAULT 0 COMMENT '当日支付金额',
    `gross_profit`     DECIMAL(18, 2)  NOT NULL DEFAULT 0 COMMENT '当日毛利（支付金额-成本）',
    `refund_amount`    DECIMAL(18, 2)  NOT NULL DEFAULT 0 COMMENT '当日退款金额',
    `created_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_biz_date` (`biz_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='每日销售报表快照';
