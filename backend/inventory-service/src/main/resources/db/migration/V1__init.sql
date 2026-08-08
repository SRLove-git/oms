-- 库存服务：SPU/SKU、仓库、库存、库存流水

CREATE TABLE IF NOT EXISTS `spu` (
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `spu_no`         VARCHAR(64)     NOT NULL COMMENT 'SPU 编码',
    `name`           VARCHAR(255)    NOT NULL,
    `category_id`    BIGINT UNSIGNED DEFAULT NULL,
    `brand`          VARCHAR(128)    DEFAULT NULL,
    `qualification_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联资质证照 ID',
    `status`         TINYINT         NOT NULL DEFAULT 1 COMMENT '1-上架 0-下架',
    `version`        INT             NOT NULL DEFAULT 0,
    `deleted`        TINYINT         NOT NULL DEFAULT 0,
    `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_spu_no` (`spu_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='SPU 表';

CREATE TABLE IF NOT EXISTS `sku` (
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `spu_id`           BIGINT UNSIGNED NOT NULL,
    `sku_no`           VARCHAR(64)     NOT NULL COMMENT 'SKU 编码',
    `name`             VARCHAR(255)    NOT NULL,
    `spec`             VARCHAR(255)    DEFAULT NULL COMMENT '规格',
    `barcode`          VARCHAR(64)     DEFAULT NULL,
    `udi`              VARCHAR(128)    DEFAULT NULL COMMENT '唯一器械标识',
    `registration_no`  VARCHAR(64)     DEFAULT NULL COMMENT '注册证号',
    `price`            DECIMAL(18, 2)  NOT NULL DEFAULT 0 COMMENT '渠道价',
    `status`           TINYINT         NOT NULL DEFAULT 1 COMMENT '1-可售 0-禁售',
    `version`          INT             NOT NULL DEFAULT 0,
    `deleted`          TINYINT         NOT NULL DEFAULT 0,
    `created_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sku_no` (`sku_no`),
    KEY `idx_spu` (`spu_id`),
    KEY `idx_udi` (`udi`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='SKU 表';

CREATE TABLE IF NOT EXISTS `warehouse` (
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `code`         VARCHAR(32)     NOT NULL COMMENT '仓库编码',
    `name`         VARCHAR(128)    NOT NULL,
    `address`      VARCHAR(512)    DEFAULT NULL,
    `status`       TINYINT         NOT NULL DEFAULT 1 COMMENT '1-启用 0-停用',
    `version`      INT             NOT NULL DEFAULT 0,
    `deleted`      TINYINT         NOT NULL DEFAULT 0,
    `created_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='仓库表';

CREATE TABLE IF NOT EXISTS `inventory` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `warehouse_id`    BIGINT UNSIGNED NOT NULL,
    `sku_id`          BIGINT UNSIGNED NOT NULL,
    `batch_no`        VARCHAR(64)     DEFAULT NULL,
    `quantity`        INT             NOT NULL DEFAULT 0 COMMENT '可用库存',
    `reserved_quantity` INT          NOT NULL DEFAULT 0 COMMENT '预占库存',
    `frozen_quantity` INT            NOT NULL DEFAULT 0 COMMENT '冻结库存',
    `expire_at`       DATE            DEFAULT NULL COMMENT '效期',
    `version`         INT             NOT NULL DEFAULT 0,
    `deleted`         TINYINT         NOT NULL DEFAULT 0,
    `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_wh_sku_batch` (`warehouse_id`, `sku_id`, `batch_no`),
    KEY `idx_expire` (`expire_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='库存表';

CREATE TABLE IF NOT EXISTS `inventory_transaction` (
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `warehouse_id`   BIGINT UNSIGNED NOT NULL,
    `sku_id`         BIGINT UNSIGNED NOT NULL,
    `batch_no`       VARCHAR(64)     DEFAULT NULL,
    `biz_type`       TINYINT         NOT NULL COMMENT '1-预占 2-释放 3-扣减 4-回补 5-入库 6-出库 7-冻结 8-解冻 9-盘盈 10-盘亏 11-报废',
    `biz_no`         VARCHAR(64)     NOT NULL COMMENT '业务单号（订单号/盘点单等）',
    `change_quantity` INT            NOT NULL COMMENT '变动数量（正负）',
    `before_quantity` INT            NOT NULL,
    `after_quantity`  INT            NOT NULL,
    `operator_id`    BIGINT UNSIGNED DEFAULT NULL,
    `remark`         VARCHAR(255)    DEFAULT NULL,
    `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_sku` (`sku_id`),
    KEY `idx_biz_no` (`biz_no`),
    KEY `idx_created_at` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='库存流水表';
