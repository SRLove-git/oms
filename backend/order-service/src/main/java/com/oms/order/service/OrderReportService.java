package com.oms.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oms.common.redis.cache.ReportCache;
import com.oms.order.dto.OrderReportDtos.DailySalesSnapshot;
import com.oms.order.dto.OrderReportDtos.OrderSourceItem;
import com.oms.order.dto.OrderReportDtos.SalesSummary;
import com.oms.order.dto.OrderReportDtos.SalesTrendItem;
import com.oms.order.entity.ReportDailySales;
import com.oms.order.mapper.OrderReportMapper;
import com.oms.order.mapper.ReportDailySalesMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OrderReportService {

    private static final Logger log = LoggerFactory.getLogger(OrderReportService.class);

    private final OrderReportMapper reportMapper;
    private final ReportDailySalesMapper dailyMapper;
    private final ReportCache reportCache;

    @Value("${oms.report.cache-ttl-seconds:300}")
    private long cacheTtlSeconds;

    public OrderReportService(
            OrderReportMapper reportMapper,
            ReportDailySalesMapper dailyMapper,
            ReportCache reportCache) {
        this.reportMapper = reportMapper;
        this.dailyMapper = dailyMapper;
        this.reportCache = reportCache;
    }

    public SalesSummary summary(LocalDate startDate, LocalDate endDate, Long merchantId) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();
        String key = reportCache.key("sales", "summary", start.toString(), end.toString(), "m" + merchantId);
        return cached(key, SalesSummary.class, () -> computeSummary(start, end, merchantId));
    }

    public List<SalesTrendItem> trend(LocalDate startDate, LocalDate endDate, Long merchantId) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();
        String key = reportCache.key("sales", "trend", start.toString(), end.toString(), "m" + merchantId);
        return cached(key, List.class, () -> reportMapper.salesTrend(start, end, merchantId).stream()
                .map(row -> new SalesTrendItem(
                        LocalDate.parse(String.valueOf(row.get("biz_date"))),
                        toLong(row.get("paid_order_count")),
                        toDecimal(row.get("paid_amount"))))
                .toList());
    }

    public List<OrderSourceItem> source(LocalDate startDate, LocalDate endDate, Long merchantId) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();
        String key = reportCache.key("sales", "source", start.toString(), end.toString(), "m" + merchantId);
        return cached(key, List.class, () -> reportMapper.orderSource(start, end, merchantId).stream()
                .map(row -> new OrderSourceItem(
                        ((Number) row.get("order_type")).intValue(),
                        toLong(row.get("order_count")),
                        toDecimal(row.get("paid_amount"))))
                .toList());
    }

    public List<DailySalesSnapshot> daily(LocalDate startDate, LocalDate endDate) {
        List<ReportDailySales> rows = dailyMapper.selectList(new LambdaQueryWrapper<ReportDailySales>()
                .ge(ReportDailySales::getBizDate, startDate)
                .le(ReportDailySales::getBizDate, endDate)
                .orderByAsc(ReportDailySales::getBizDate));
        return rows.stream()
                .map(r -> new DailySalesSnapshot(
                        r.getBizDate(),
                        r.getOrderCount() == null ? 0 : r.getOrderCount(),
                        r.getPaidOrderCount() == null ? 0 : r.getPaidOrderCount(),
                        r.getPaidAmount() == null ? BigDecimal.ZERO : r.getPaidAmount(),
                        r.getGrossProfit() == null ? BigDecimal.ZERO : r.getGrossProfit(),
                        r.getRefundAmount() == null ? BigDecimal.ZERO : r.getRefundAmount()))
                .toList();
    }

    public long completedCount(LocalDate startDate, LocalDate endDate) {
        return reportMapper.completedOrderCount(startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay());
    }

    public void refreshDaily(LocalDate bizDate) {
        SalesSummary summary = computeSummary(bizDate.atStartOfDay(), bizDate.plusDays(1).atStartOfDay(), null);
        ReportDailySales row = dailyMapper.selectOne(new LambdaQueryWrapper<ReportDailySales>()
                .eq(ReportDailySales::getBizDate, bizDate)
                .last("LIMIT 1"));
        if (row == null) {
            row = new ReportDailySales();
            row.setBizDate(bizDate);
        }
        row.setOrderCount((int) summary.orderCount());
        row.setPaidOrderCount((int) summary.paidOrderCount());
        row.setPaidAmount(summary.paidAmount());
        row.setGrossProfit(summary.grossProfit());
        row.setRefundAmount(summary.refundAmount());
        if (row.getId() == null) {
            dailyMapper.insert(row);
        } else {
            dailyMapper.updateById(row);
        }
    }

    public void backfill(LocalDate startDate, LocalDate endDateExclusive) {
        LocalDate cursor = startDate;
        while (cursor.isBefore(endDateExclusive)) {
            refreshDaily(cursor);
            cursor = cursor.plusDays(1);
        }
    }

    private SalesSummary computeSummary(LocalDateTime start, LocalDateTime end, Long merchantId) {
        long orderCount = reportMapper.countOrders(start, end, merchantId);
        Map<String, Object> paid = reportMapper.paidSummary(start, end, merchantId);
        long paidOrderCount = toLong(paid.get("paid_order_count"));
        BigDecimal paidAmount = toDecimal(paid.get("paid_amount"));

        Map<String, Object> gp = reportMapper.grossProfit(start, end, merchantId);
        BigDecimal grossProfit = toDecimal(gp.get("paid_amount")).subtract(toDecimal(gp.get("cost_amount")));
        BigDecimal refundAmount = reportMapper.refundAmount(start, end);

        long totalCustomers = reportMapper.repurchaseTotalCustomers(start, end, merchantId);
        long repeatCustomers = reportMapper.repurchaseRepeatCustomers(start, end, merchantId);
        BigDecimal avgOrderValue = paidOrderCount == 0
                ? BigDecimal.ZERO
                : paidAmount.divide(BigDecimal.valueOf(paidOrderCount), 2, RoundingMode.HALF_UP);
        BigDecimal repurchaseRate = totalCustomers == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(repeatCustomers)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalCustomers), 2, RoundingMode.HALF_UP);
        return new SalesSummary(
                orderCount,
                paidOrderCount,
                paidAmount,
                avgOrderValue,
                grossProfit,
                refundAmount,
                totalCustomers,
                repeatCustomers,
                repurchaseRate);
    }

    private <T> T cached(String key, Class<T> type, Supplier<T> loader) {
        try {
            T hit = reportCache.get(key);
            if (hit != null) {
                return hit;
            }
        } catch (Exception ex) {
            log.warn("报表缓存读取失败，回退数据库查询 key={}", key);
        }
        T value = loader.get();
        try {
            reportCache.set(key, value, Duration.ofSeconds(cacheTtlSeconds));
        } catch (Exception ex) {
            log.warn("报表缓存写入失败 key={}", key);
        }
        return value;
    }

    private long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    private BigDecimal toDecimal(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value));
    }
}
