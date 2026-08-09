-- 阶段3：售后报表索引

ALTER TABLE `return_order`
    ADD KEY `idx_type_created` (`type`, `created_at`),
    ADD KEY `idx_status_created` (`status`, `created_at`);

ALTER TABLE `repair_order`
    ADD KEY `idx_finished_at` (`finished_at`),
    ADD KEY `idx_created_at` (`created_at`);

ALTER TABLE `refund_record`
    ADD KEY `idx_status_created` (`status`, `created_at`);
