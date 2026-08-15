-- 默认币种切换：CNY → SGD（已有数据不受影响，仅调整列默认值）

ALTER TABLE `refund_record`
    MODIFY COLUMN `currency` VARCHAR(8) NOT NULL DEFAULT 'SGD';
