-- 售后服务：退货/换货/维修单、售后明细、退款记录

CREATE TABLE IF NOT EXISTS `return_order` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `return_no`     VARCHAR(64)     NOT NULL COMMENT '售后单号',
    `order_id`      BIGINT UNSIGNED NOT NULL,
    `order_no`      VARCHAR(64)     NOT NULL,
    `type`          TINYINT         NOT NULL COMMENT '1-退货 2-换货 3-维修',
    `status`        TINYINT         NOT NULL DEFAULT 1 COMMENT '1-待审核 2-已通过 3-已驳回 4-收货质检 5-退款中 6-已完成 7-已取消',
    `reason`        VARCHAR(255)    DEFAULT NULL,
    `total_amount`  DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    `version`       INT             NOT NULL DEFAULT 0,
    `deleted`       TINYINT         NOT NULL DEFAULT 0,
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_return_no` (`return_no`),
    KEY `idx_order` (`order_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='售后单表';

CREATE TABLE IF NOT EXISTS `return_item` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `return_id`     BIGINT UNSIGNED NOT NULL,
    `order_item_id` BIGINT UNSIGNED NOT NULL,
    `sku_id`        BIGINT UNSIGNED NOT NULL,
    `quantity`      INT             NOT NULL,
    `unit_amount`   DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    `version`       INT             NOT NULL DEFAULT 0,
    `deleted`       TINYINT         NOT NULL DEFAULT 0,
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_return` (`return_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='售后明细表';

CREATE TABLE IF NOT EXISTS `refund_record` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `refund_no`     VARCHAR(64)     NOT NULL COMMENT '退款单号',
    `return_id`     BIGINT UNSIGNED DEFAULT NULL,
    `order_id`      BIGINT UNSIGNED NOT NULL,
    `payment_no`    VARCHAR(64)     DEFAULT NULL COMMENT '原支付单号',
    `amount`        DECIMAL(18, 2)  NOT NULL,
    `currency`      VARCHAR(8)      NOT NULL DEFAULT 'CNY',
    `method`        TINYINT         NOT NULL DEFAULT 1 COMMENT '1-原路退回 2-人工转账',
    `status`        TINYINT         NOT NULL DEFAULT 1 COMMENT '1-待审核 2-退款中 3-成功 4-失败',
    `channel_txn_no` VARCHAR(128)   DEFAULT NULL,
    `refunded_at`   DATETIME        DEFAULT NULL,
    `version`       INT             NOT NULL DEFAULT 0,
    `deleted`       TINYINT         NOT NULL DEFAULT 0,
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_refund_no` (`refund_no`),
    KEY `idx_return` (`return_id`),
    KEY `idx_order` (`order_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='退款记录表';
