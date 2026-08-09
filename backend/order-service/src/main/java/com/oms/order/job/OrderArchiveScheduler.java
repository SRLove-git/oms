package com.oms.order.job;

import com.oms.order.service.OrderArchiveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderArchiveScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderArchiveScheduler.class);

    private final OrderArchiveService orderArchiveService;

    public OrderArchiveScheduler(OrderArchiveService orderArchiveService) {
        this.orderArchiveService = orderArchiveService;
    }

    @Scheduled(fixedDelayString = "${oms.order.archive-scan-ms:3600000}")
    public void archiveTerminalOrders() {
        try {
            int total = orderArchiveService.archiveAll();
            if (total > 0) {
                log.info("历史订单归档完成，共归档 {} 单", total);
            }
        } catch (Exception ex) {
            log.error("历史订单归档失败", ex);
        }
    }
}
