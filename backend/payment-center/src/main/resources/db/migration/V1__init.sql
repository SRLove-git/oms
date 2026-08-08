-- 支付中心：支付渠道、支付流水、回调日志、对账差异

CREATE TABLE IF NOT EXISTS `payment_channel` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `code`          VARCHAR(32)     NOT NULL COMMENT '渠道编码：wechat/alipay/visa/mastercard/balance',
    `name`          VARCHAR(64)     NOT NULL,
    `config_json`   TEXT            DEFAULT NULL COMMENT '渠道配置（JSON，敏感项加密）',
    `status`        TINYINT         NOT NULL DEFAULT 1 COMMENT '1-启用 0-停用',
    `version`       INT             NOT NULL DEFAULT 0,
    `deleted`       TINYINT         NOT NULL DEFAULT 0,
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='支付渠道表';

CREATE TABLE IF NOT EXISTS `payment_transaction` (
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `payment_no`     VARCHAR(64)     NOT NULL COMMENT '支付单号',
    `order_no`       VARCHAR(64)     NOT NULL,
    `channel`        VARCHAR(32)     NOT NULL,
    `amount`         DECIMAL(18, 2)  NOT NULL,
    `currency`       VARCHAR(8)      NOT NULL DEFAULT 'CNY',
    `status`         TINYINT         NOT NULL DEFAULT 1 COMMENT '1-待支付 2-成功 3-失败 4-已关闭 5-已退款',
    `channel_txn_no` VARCHAR(128)    DEFAULT NULL,
    `idempotency_key` VARCHAR(64)    DEFAULT NULL COMMENT '幂等键',
    `notify_count`   INT             NOT NULL DEFAULT 0 COMMENT '回调重试次数',
    `notify_at`      DATETIME        DEFAULT NULL,
    `paid_at`        DATETIME        DEFAULT NULL,
    `version`        INT             NOT NULL DEFAULT 0,
    `deleted`        TINYINT         NOT NULL DEFAULT 0,
    `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_payment_no` (`payment_no`),
    UNIQUE KEY `uk_idempotency` (`idempotency_key`),
    KEY `idx_order_no` (`order_no`),
    KEY `idx_channel_txn` (`channel_txn_no`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='支付流水表';

CREATE TABLE IF NOT EXISTS `payment_notify_log` (
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `payment_no`     VARCHAR(64)     NOT NULL,
    `channel`        VARCHAR(32)     NOT NULL,
    `request_body`   TEXT            DEFAULT NULL COMMENT '回调报文',
    `verify_result`  TINYINT         NOT NULL DEFAULT 0 COMMENT '验签结果：0-失败 1-成功',
    `handle_result`  VARCHAR(255)    DEFAULT NULL,
    `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_payment_no` (`payment_no`),
    KEY `idx_created_at` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='支付回调日志表';

CREATE TABLE IF NOT EXISTS `reconciliation_record` (
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `biz_date`       DATE            NOT NULL COMMENT '对账日期',
    `channel`        VARCHAR(32)     NOT NULL,
    `channel_amount` DECIMAL(18, 2)  NOT NULL DEFAULT 0 COMMENT '渠道账单金额',
    `local_amount`   DECIMAL(18, 2)  NOT NULL DEFAULT 0 COMMENT '本地流水金额',
    `diff_count`     INT             NOT NULL DEFAULT 0 COMMENT '差异笔数',
    `status`         TINYINT         NOT NULL DEFAULT 1 COMMENT '1-差异待处理 2-已处理 3-一致',
    `detail_json`    TEXT            DEFAULT NULL,
    `handled_at`     DATETIME        DEFAULT NULL,
    `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_biz_date_channel` (`biz_date`, `channel`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='对账差异记录表';
