-- 阶段3：成本价与报表索引

ALTER TABLE `sku`
    ADD COLUMN `cost_price` DECIMAL(18, 2) NOT NULL DEFAULT 0 COMMENT '成本价' AFTER `price`;

ALTER TABLE `inventory`
    ADD KEY `idx_warehouse` (`warehouse_id`),
    ADD KEY `idx_sku` (`sku_id`);

ALTER TABLE `inventory_transaction`
    ADD KEY `idx_sku_created` (`sku_id`, `created_at`),
    ADD KEY `idx_biz_type_created` (`biz_type`, `created_at`);
