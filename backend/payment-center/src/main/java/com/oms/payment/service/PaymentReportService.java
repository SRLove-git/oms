package com.oms.payment.service;

import com.oms.common.redis.cache.ReportCache;
import com.oms.payment.dto.PaymentReportDtos.ChannelStats;
import com.oms.payment.dto.PaymentReportDtos.ReconciliationStats;
import com.oms.payment.mapper.PaymentReportMapper;
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
public class PaymentReportService {

    private static final Logger log = LoggerFactory.getLogger(PaymentReportService.class);

    private final PaymentReportMapper reportMapper;
    private final ReportCache reportCache;

    @Value("${oms.report.cache-ttl-seconds:300}")
    private long cacheTtlSeconds;

    public PaymentReportService(PaymentReportMapper reportMapper, ReportCache reportCache) {
        this.reportMapper = reportMapper;
        this.reportCache = reportCache;
    }

    public List<ChannelStats> channelStats(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();
        String key = "channel-stats:" + start + ":" + end;
        return cached(key, List.class, () -> reportMapper.channelStats(start, end).stream()
                .map(row -> {
                    long successCount = toLong(row.get("success_count"));
                    long refundCount = toLong(row.get("refund_count"));
                    BigDecimal successAmount = toDecimal(row.get("success_amount"));
                    BigDecimal refundAmount = toDecimal(row.get("refund_amount"));
                    BigDecimal refundRate = successAmount.signum() == 0
                            ? BigDecimal.ZERO
                            : refundAmount
                                    .multiply(BigDecimal.valueOf(100))
                                    .divide(successAmount, 2, RoundingMode.HALF_UP);
                    return new ChannelStats(
                            String.valueOf(row.get("channel")),
                            toLong(row.get("total_count")),
                            successCount,
                            successAmount,
                            toLong(row.get("fail_count")),
                            refundCount,
                            refundAmount,
                            refundRate);
                })
                .toList());
    }

    public List<ReconciliationStats> reconciliationStats(LocalDate startDate, LocalDate endDate) {
        String key = "reconciliation-stats:" + startDate + ":" + endDate;
        return cached(key, List.class, () -> reportMapper.reconciliationStats(startDate, endDate).stream()
                .map(row -> new ReconciliationStats(
                        String.valueOf(row.get("channel")),
                        ((Number) row.get("status")).intValue(),
                        toLong(row.get("record_count")),
                        toDecimal(row.get("channel_amount")),
                        toDecimal(row.get("local_amount")),
                        toLong(row.get("diff_count"))))
                .toList());
    }

    private <T> T cached(String key, Class<T> type, Supplier<T> loader) {
        try {
            T hit = reportCache.get(reportCache.key("payments", key));
            if (hit != null) {
                return hit;
            }
        } catch (Exception ex) {
            log.warn("支付报表缓存读取失败，回退数据库查询 key={}", key);
        }
        T value = loader.get();
        try {
            reportCache.set(reportCache.key("payments", key), value, Duration.ofSeconds(cacheTtlSeconds));
        } catch (Exception ex) {
            log.warn("支付报表缓存写入失败 key={}", key);
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
