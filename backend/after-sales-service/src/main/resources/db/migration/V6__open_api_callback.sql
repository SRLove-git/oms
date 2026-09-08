-- 商城事件回调 outbox：售后状态变化先落库，再由调度任务带退避重试投递给商城。
-- status: 0-待投递 1-已投递 2-失败(死信)

CREATE TABLE IF NOT EXISTS `open_api_event` (
    `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `event_id`          VARCHAR(64)     NOT NULL COMMENT '全局唯一事件 ID（商城幂等键）',
    `event_type`        VARCHAR(64)     NOT NULL COMMENT '事件类型',
    `order_no`          VARCHAR(64)     DEFAULT NULL COMMENT 'OMS 订单号',
    `external_order_no` VARCHAR(64)     DEFAULT NULL COMMENT '商城外部订单号',
    `payload`           TEXT            NOT NULL COMMENT '回调报文 JSON',
    `status`            TINYINT         NOT NULL DEFAULT 0 COMMENT '0-待投递 1-已投递 2-失败(死信)',
    `retry_count`       INT             NOT NULL DEFAULT 0,
    `next_retry_at`     DATETIME        DEFAULT NULL,
    `last_error`        VARCHAR(500)    DEFAULT NULL,
    `created_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_event_id` (`event_id`),
    KEY `idx_dispatch` (`status`, `next_retry_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='商城回调事件队列表';
