-- 维修管理：维修工单与维修记录

-- 售后单补充商户字段（用于商户维度过滤）
ALTER TABLE `return_order`
    ADD COLUMN `merchant_id` BIGINT UNSIGNED NULL AFTER `order_no`;

CREATE TABLE IF NOT EXISTS `repair_order` (
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `repair_no`      VARCHAR(64)     NOT NULL COMMENT '维修单号',
    `return_id`      BIGINT UNSIGNED NOT NULL COMMENT '售后单ID',
    `return_no`      VARCHAR(64)     NOT NULL,
    `order_no`       VARCHAR(64)     NOT NULL,
    `sku_id`         BIGINT UNSIGNED NOT NULL,
    `status`         TINYINT         NOT NULL DEFAULT 1 COMMENT '1-待维修 2-维修中 3-待验收 4-已完成 5-已取消',
    `fault_desc`     VARCHAR(512)    DEFAULT NULL COMMENT '故障描述',
    `repair_fee`     DECIMAL(18, 2)  NOT NULL DEFAULT 0 COMMENT '维修费用',
    `assigned_to`    VARCHAR(64)     DEFAULT NULL COMMENT '维修人',
    `finished_at`    DATETIME        DEFAULT NULL,
    `version`        INT             NOT NULL DEFAULT 0,
    `deleted`        TINYINT         NOT NULL DEFAULT 0,
    `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_repair_no` (`repair_no`),
    KEY `idx_return` (`return_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='维修工单表';

CREATE TABLE IF NOT EXISTS `repair_log` (
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `repair_id`      BIGINT UNSIGNED NOT NULL,
    `action`         VARCHAR(32)     NOT NULL COMMENT '动作：assign/start/progress/fee/complete/cancel',
    `content`        VARCHAR(512)    DEFAULT NULL,
    `operator_name`  VARCHAR(64)     DEFAULT NULL,
    `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_repair` (`repair_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='维修记录表';
