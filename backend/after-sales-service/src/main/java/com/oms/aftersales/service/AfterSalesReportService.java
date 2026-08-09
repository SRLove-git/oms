package com.oms.aftersales.service;

import com.oms.aftersales.client.OrderClient;
import com.oms.aftersales.dto.AfterSalesReportDtos.ReasonDistribution;
import com.oms.aftersales.dto.AfterSalesReportDtos.RepairDuration;
import com.oms.aftersales.dto.AfterSalesReportDtos.ReturnRate;
import com.oms.aftersales.dto.AfterSalesReportDtos.TypeStats;
import com.oms.aftersales.mapper.AfterSalesReportMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AfterSalesReportService {

    private final AfterSalesReportMapper reportMapper;
    private final OrderClient orderClient;

    public AfterSalesReportService(AfterSalesReportMapper reportMapper, OrderClient orderClient) {
        this.reportMapper = reportMapper;
        this.orderClient = orderClient;
    }

    public List<TypeStats> typeStats(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();
        Map<Integer, BigDecimal> refunded = reportMapper.refundedByType(start, end).stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row.get("type")).intValue(),
                        row -> toDecimal(row.get("refunded_amount"))));
        return reportMapper.typeStats(start, end).stream()
                .map(row -> {
                    int type = ((Number) row.get("type")).intValue();
                    return new TypeStats(
                            type,
                            toLong(row.get("count")),
                            toDecimal(row.get("total_amount")),
                            toLong(row.get("completed_count")),
                            refunded.getOrDefault(type, BigDecimal.ZERO));
                })
                .toList();
    }

    public List<ReasonDistribution> reasonDistribution(LocalDate startDate, LocalDate endDate, int topN) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();
        return reportMapper.reasonDistribution(start, end, Math.min(Math.max(topN, 1), 100)).stream()
                .map(row -> new ReasonDistribution(
                        String.valueOf(row.get("reason")), toLong(row.get("count"))))
                .toList();
    }

    public RepairDuration repairDuration(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();
        Map<String, Object> row = reportMapper.repairDuration(start, end);
        return new RepairDuration(
                toLong(row.get("repair_count")),
                toLong(row.get("avg_minutes")),
                toLong(row.get("min_minutes")),
                toLong(row.get("max_minutes")));
    }

    public ReturnRate returnRate(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();
        long returnCount = reportMapper.typeStats(start, end).stream()
                .filter(row -> ((Number) row.get("type")).intValue() == 1)
                .mapToLong(row -> toLong(row.get("count")))
                .sum();
        long completedOrderCount = 0;
        try {
            completedOrderCount = orderClient
                    .completedCount(startDate.toString(), endDate.toString())
                    .data()
                    .completedCount();
        } catch (Exception ex) {
            // 订单服务不可用时返回 0，避免报表接口被远程依赖拖垮
        }
        BigDecimal rate = completedOrderCount == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(returnCount)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(completedOrderCount), 2, RoundingMode.HALF_UP);
        return new ReturnRate(returnCount, completedOrderCount, rate);
    }

    private long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    private BigDecimal toDecimal(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value));
    }
}
