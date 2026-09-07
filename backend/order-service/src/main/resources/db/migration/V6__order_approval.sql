CREATE TABLE IF NOT EXISTS `order_approval` (
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `order_no`       VARCHAR(64)     NOT NULL,
    `approval_type`  TINYINT         NOT NULL COMMENT '1-改价 2-改地址 3-改物流 4-部分退款',
    `request_payload` TEXT           NOT NULL,
    `status`         TINYINT         NOT NULL DEFAULT 0 COMMENT '0-待审核 1-通过 2-驳回',
    `operator_id`    BIGINT UNSIGNED DEFAULT NULL,
    `operator_name`  VARCHAR(64)     DEFAULT NULL,
    `version`        INT             NOT NULL DEFAULT 0,
    `deleted`        TINYINT         NOT NULL DEFAULT 0,
    `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_order` (`order_no`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单变更审批';
