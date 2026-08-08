package com.oms.order.job;

import com.oms.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TimeoutOrderScheduler {

    private static final Logger log = LoggerFactory.getLogger(TimeoutOrderScheduler.class);

    private final OrderService orderService;

    public TimeoutOrderScheduler(OrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(fixedDelayString = "${oms.order.timeout-scan-ms:60000}")
    public void scan() {
        try {
            orderService.timeoutCancelPendingOrders();
        } catch (Exception ex) {
            log.error("超时订单扫描失败", ex);
        }
    }
}
