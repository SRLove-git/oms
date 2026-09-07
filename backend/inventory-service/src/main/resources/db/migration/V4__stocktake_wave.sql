CREATE TABLE IF NOT EXISTS `stock_take` (
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `take_no`      VARCHAR(64)     NOT NULL,
    `warehouse_id` BIGINT UNSIGNED NOT NULL,
    `status`       TINYINT         NOT NULL DEFAULT 0 COMMENT '0-进行中 1-已提交 2-已审核',
    `remark`       VARCHAR(255)    DEFAULT NULL,
    `operator_id`  BIGINT UNSIGNED DEFAULT NULL,
    `version`      INT             NOT NULL DEFAULT 0,
    `deleted`      TINYINT         NOT NULL DEFAULT 0,
    `created_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_take_no` (`take_no`),
    KEY `idx_warehouse` (`warehouse_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='盘点单';

CREATE TABLE IF NOT EXISTS `stock_take_item` (
    `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `take_id`            BIGINT UNSIGNED NOT NULL,
    `sku_id`             BIGINT UNSIGNED NOT NULL,
    `expected_quantity`  INT             NOT NULL DEFAULT 0,
    `actual_quantity`    INT             DEFAULT NULL,
    `version`            INT             NOT NULL DEFAULT 0,
    `deleted`            TINYINT         NOT NULL DEFAULT 0,
    `created_at`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_take` (`take_id`),
    KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='盘点明细';

CREATE TABLE IF NOT EXISTS `pick_wave` (
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `wave_no`      VARCHAR(64)     NOT NULL,
    `order_no`     VARCHAR(64)     NOT NULL,
    `sku_id`       BIGINT UNSIGNED NOT NULL,
    `quantity`     INT             NOT NULL,
    `status`       TINYINT         NOT NULL DEFAULT 0 COMMENT '0-待拣货 1-已拣货 2-已复核 3-已打包',
    `operator_id`  BIGINT UNSIGNED DEFAULT NULL,
    `version`      INT             NOT NULL DEFAULT 0,
    `deleted`      TINYINT         NOT NULL DEFAULT 0,
    `created_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_wave_no` (`wave_no`),
    KEY `idx_order` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='拣货波次';
