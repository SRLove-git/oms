-- 集成网关服务：第三方平台订单映射、物流轨迹

CREATE TABLE IF NOT EXISTS `external_order_mapping` (
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `platform`         VARCHAR(32)     NOT NULL COMMENT '平台：tmall/jd/...',
    `platform_order_no` VARCHAR(128)   NOT NULL COMMENT '平台订单号',
    `order_no`         VARCHAR(64)     DEFAULT NULL COMMENT 'OMS 订单号',
    `status`           TINYINT         NOT NULL DEFAULT 1 COMMENT '1-已拉取 2-已建单 3-已回传 4-异常',
    `raw_data`         TEXT            DEFAULT NULL COMMENT '平台原始报文',
    `error_message`    VARCHAR(512)    DEFAULT NULL,
    `version`          INT             NOT NULL DEFAULT 0,
    `deleted`          TINYINT         NOT NULL DEFAULT 0,
    `created_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_platform_order` (`platform`, `platform_order_no`),
    KEY `idx_order_no` (`order_no`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='外部平台订单映射表';

CREATE TABLE IF NOT EXISTS `logistics_tracking` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `order_no`        VARCHAR(64)     NOT NULL,
    `tracking_no`     VARCHAR(64)     NOT NULL COMMENT '运单号',
    `carrier`         VARCHAR(32)     NOT NULL COMMENT '承运商：sf/sto/...',
    `status`          VARCHAR(32)     NOT NULL COMMENT '物流状态：picked_up/in_transit/signed/exception',
    `trace_info`      TEXT            DEFAULT NULL COMMENT '轨迹信息（JSON）',
    `pushed`          TINYINT         NOT NULL DEFAULT 0 COMMENT '是否已推送',
    `version`         INT             NOT NULL DEFAULT 0,
    `deleted`         TINYINT         NOT NULL DEFAULT 0,
    `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tracking` (`carrier`, `tracking_no`),
    KEY `idx_order_no` (`order_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='物流轨迹表';
