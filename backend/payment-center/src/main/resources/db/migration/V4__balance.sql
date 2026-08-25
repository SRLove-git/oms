-- 余额账户与流水：支持储值余额支付与退款原路/余额退回

CREATE TABLE IF NOT EXISTS `balance_account` (
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `merchant_id`      BIGINT UNSIGNED NOT NULL COMMENT '商户 ID',
    `available_amount` DECIMAL(18, 2)  NOT NULL DEFAULT 0 COMMENT '可用余额',
    `frozen_amount`    DECIMAL(18, 2)  NOT NULL DEFAULT 0 COMMENT '冻结余额',
    `version`          INT             NOT NULL DEFAULT 0,
    `deleted`          TINYINT         NOT NULL DEFAULT 0,
    `created_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_merchant_id` (`merchant_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='商户余额账户表';

CREATE TABLE IF NOT EXISTS `balance_transaction` (
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `merchant_id`    BIGINT UNSIGNED NOT NULL,
    `account_id`     BIGINT UNSIGNED NOT NULL,
    `biz_no`         VARCHAR(64)     NOT NULL COMMENT '余额流水号',
    `type`           TINYINT         NOT NULL COMMENT '1-充值 2-支付 3-退款入账',
    `amount`         DECIMAL(18, 2)  NOT NULL,
    `before_amount`  DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    `after_amount`   DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    `remark`         VARCHAR(255)    DEFAULT NULL,
    `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_biz_no` (`biz_no`),
    KEY `idx_merchant_id` (`merchant_id`),
    KEY `idx_account_id` (`account_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='余额流水表';
