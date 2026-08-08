-- 用户服务：用户、RBAC、商户、资质、操作审计

CREATE TABLE IF NOT EXISTS `user` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`      VARCHAR(64)     NOT NULL COMMENT '登录账号',
    `password`      VARCHAR(128)    NOT NULL COMMENT '密码（BCrypt）',
    `real_name`     VARCHAR(64)     DEFAULT NULL COMMENT '姓名',
    `phone`         VARCHAR(32)     DEFAULT NULL COMMENT '手机号',
    `email`         VARCHAR(128)    DEFAULT NULL COMMENT '邮箱',
    `user_type`     TINYINT         NOT NULL DEFAULT 1 COMMENT '1-平台运营 2-商户 3-终端客户',
    `status`        TINYINT         NOT NULL DEFAULT 1 COMMENT '1-启用 0-停用',
    `last_login_at` DATETIME        DEFAULT NULL COMMENT '最后登录时间',
    `version`       INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `deleted`       TINYINT         NOT NULL DEFAULT 0 COMMENT '软删除：0-否 1-是',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_phone` (`phone`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='用户表';

CREATE TABLE IF NOT EXISTS `role` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `code`        VARCHAR(64)     NOT NULL COMMENT '角色编码',
    `name`        VARCHAR(64)     NOT NULL COMMENT '角色名称',
    `description` VARCHAR(255)    DEFAULT NULL,
    `version`     INT             NOT NULL DEFAULT 0,
    `deleted`     TINYINT         NOT NULL DEFAULT 0,
    `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='角色表';

CREATE TABLE IF NOT EXISTS `permission` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `code`       VARCHAR(64)     NOT NULL COMMENT '权限编码',
    `name`       VARCHAR(64)     NOT NULL COMMENT '权限名称',
    `type`       TINYINT         NOT NULL DEFAULT 1 COMMENT '1-菜单 2-按钮 3-数据',
    `parent_id`  BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父权限 ID',
    `sort_order` INT             NOT NULL DEFAULT 0,
    `version`    INT             NOT NULL DEFAULT 0,
    `deleted`    TINYINT         NOT NULL DEFAULT 0,
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='权限表';

CREATE TABLE IF NOT EXISTS `user_role` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT UNSIGNED NOT NULL,
    `role_id`    BIGINT UNSIGNED NOT NULL,
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='用户角色关联表';

CREATE TABLE IF NOT EXISTS `role_permission` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `role_id`       BIGINT UNSIGNED NOT NULL,
    `permission_id` BIGINT UNSIGNED NOT NULL,
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='角色权限关联表';

CREATE TABLE IF NOT EXISTS `merchant_info` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `merchant_no`   VARCHAR(32)     NOT NULL COMMENT '商户编号',
    `name`          VARCHAR(128)    NOT NULL COMMENT '商户名称',
    `contact_name`  VARCHAR(64)     DEFAULT NULL,
    `contact_phone` VARCHAR(32)     DEFAULT NULL,
    `status`        TINYINT         NOT NULL DEFAULT 1 COMMENT '1-待审核 2-已启用 3-已停用',
    `contract_no`   VARCHAR(64)     DEFAULT NULL COMMENT '合同编号',
    `payment_term`  INT             DEFAULT NULL COMMENT '账期（天）',
    `version`       INT             NOT NULL DEFAULT 0,
    `deleted`       TINYINT         NOT NULL DEFAULT 0,
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_merchant_no` (`merchant_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='商户信息表';

CREATE TABLE IF NOT EXISTS `qualification` (
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `merchant_id`      BIGINT UNSIGNED NOT NULL COMMENT '商户 ID',
    `qualification_no` VARCHAR(64)     NOT NULL COMMENT '证照编号',
    `qualification_type` TINYINT       NOT NULL COMMENT '1-注册证 2-生产许可证 3-经营许可证 4-备案凭证',
    `expire_at`        DATE            NOT NULL COMMENT '有效期至',
    `file_url`         VARCHAR(512)    DEFAULT NULL COMMENT '证照附件',
    `status`           TINYINT         NOT NULL DEFAULT 1 COMMENT '1-待审核 2-已通过 3-已驳回 4-已过期',
    `version`          INT             NOT NULL DEFAULT 0,
    `deleted`          TINYINT         NOT NULL DEFAULT 0,
    `created_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_merchant` (`merchant_id`),
    KEY `idx_expire` (`expire_at`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='资质证照表';

CREATE TABLE IF NOT EXISTS `operation_log` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `operator_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '操作人 ID',
    `operator_name` VARCHAR(64)   DEFAULT NULL,
    `module`      VARCHAR(64)     NOT NULL COMMENT '业务模块',
    `action`      VARCHAR(64)     NOT NULL COMMENT '操作类型：下单/改价/审核/退款等',
    `biz_id`      VARCHAR(64)     DEFAULT NULL COMMENT '业务对象 ID',
    `before_data` TEXT            DEFAULT NULL COMMENT '操作前值（JSON）',
    `after_data`  TEXT            DEFAULT NULL COMMENT '操作后值（JSON）',
    `ip`          VARCHAR(64)     DEFAULT NULL,
    `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_operator` (`operator_id`),
    KEY `idx_module_action` (`module`, `action`),
    KEY `idx_biz_id` (`biz_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='操作审计日志表';
