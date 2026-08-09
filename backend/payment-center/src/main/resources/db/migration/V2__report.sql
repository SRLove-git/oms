-- 阶段3：支付报表索引

ALTER TABLE `payment_transaction`
    ADD KEY `idx_channel_created` (`channel`, `created_at`),
    ADD KEY `idx_status_created` (`status`, `created_at`);

ALTER TABLE `reconciliation_record`
    ADD KEY `idx_biz_date` (`biz_date`);
