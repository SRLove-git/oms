package com.oms.order.job;

import com.oms.order.service.OrderReportService;
import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReportSnapshotScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReportSnapshotScheduler.class);

    private final OrderReportService orderReportService;

    public ReportSnapshotScheduler(OrderReportService orderReportService) {
        this.orderReportService = orderReportService;
    }

    @Scheduled(cron = "${oms.report.daily-cron:0 10 1 * * ?}")
    public void snapshotYesterday() {
        try {
            orderReportService.refreshDaily(LocalDate.now().minusDays(1));
            log.info("每日销售报表快照已生成");
        } catch (Exception ex) {
            log.error("每日销售报表快照生成失败", ex);
        }
    }

    @PostConstruct
    public void backfillRecent() {
        try {
            orderReportService.backfill(LocalDate.now().minusDays(6), LocalDate.now().plusDays(1));
            log.info("近 7 天销售报表快照回填完成");
        } catch (Exception ex) {
            log.warn("销售报表快照回填失败（下次定时任务自动补齐）", ex);
        }
    }
}
