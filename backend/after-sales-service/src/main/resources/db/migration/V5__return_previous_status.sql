ALTER TABLE `return_order`
    ADD COLUMN `previous_status` TINYINT NULL COMMENT '售后前订单状态' AFTER `status`;
