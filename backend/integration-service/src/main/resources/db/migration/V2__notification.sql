-- 消息通知：模板与发送记录

CREATE TABLE IF NOT EXISTS `notification_template` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `code`            VARCHAR(64)     NOT NULL COMMENT '模板编码',
    `name`            VARCHAR(128)    NOT NULL,
    `channel`         VARCHAR(16)     NOT NULL COMMENT '渠道：sms/email/in_app/wechat',
    `scene`           VARCHAR(64)     NOT NULL COMMENT '场景编码',
    `title_template`  VARCHAR(255)    DEFAULT NULL,
    `content_template` VARCHAR(1024)  NOT NULL,
    `status`          TINYINT         NOT NULL DEFAULT 1 COMMENT '1-启用 0-停用',
    `version`         INT             NOT NULL DEFAULT 0,
    `deleted`         TINYINT         NOT NULL DEFAULT 0,
    `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code_channel` (`code`, `channel`),
    KEY `idx_scene` (`scene`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='通知模板表';

CREATE TABLE IF NOT EXISTS `notification_message` (
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `message_no`     VARCHAR(64)     NOT NULL,
    `channel`        VARCHAR(16)     NOT NULL,
    `scene`          VARCHAR(64)     DEFAULT NULL,
    `receiver`       VARCHAR(128)    NOT NULL,
    `title`          VARCHAR(255)    DEFAULT NULL,
    `content`        VARCHAR(2048)   NOT NULL,
    `status`         TINYINT         NOT NULL DEFAULT 1 COMMENT '1-成功 2-失败 3-重试中',
    `retry_count`    INT             NOT NULL DEFAULT 0,
    `error_message`  VARCHAR(512)    DEFAULT NULL,
    `sent_at`        DATETIME        DEFAULT NULL,
    `version`        INT             NOT NULL DEFAULT 0,
    `deleted`        TINYINT         NOT NULL DEFAULT 0,
    `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_message_no` (`message_no`),
    KEY `idx_receiver` (`receiver`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='通知发送记录表';
