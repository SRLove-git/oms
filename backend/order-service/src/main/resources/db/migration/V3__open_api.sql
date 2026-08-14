-- 商城开放 API：外部订单号幂等映射与订单来源

ALTER TABLE `order`
    ADD COLUMN `external_order_no` VARCHAR(64) DEFAULT NULL COMMENT '外部平台订单号（商城开放 API 幂等键）' AFTER `order_no`,
    ADD COLUMN `source`            VARCHAR(32) NOT NULL DEFAULT 'OMS' COMMENT '订单来源 OMS/OPEN_API' AFTER `order_type`,
    ADD UNIQUE KEY `uk_external_order_no` (`external_order_no`);

ALTER TABLE `order_archive`
    ADD COLUMN `external_order_no` VARCHAR(64) DEFAULT NULL COMMENT '外部平台订单号（商城开放 API 幂等键）' AFTER `order_no`,
    ADD COLUMN `source`            VARCHAR(32) NOT NULL DEFAULT 'OMS' COMMENT '订单来源 OMS/OPEN_API' AFTER `order_type`,
    ADD UNIQUE KEY `uk_external_order_no` (`external_order_no`);
