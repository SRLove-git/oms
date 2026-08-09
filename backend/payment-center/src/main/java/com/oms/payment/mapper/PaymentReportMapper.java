package com.oms.payment.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 支付报表统计。
 */
public interface PaymentReportMapper {

    @Select("""
            SELECT channel,
                   COUNT(*) AS total_count,
                   SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END) AS success_count,
                   COALESCE(SUM(CASE WHEN status = 2 THEN amount ELSE 0 END), 0) AS success_amount,
                   SUM(CASE WHEN status = 3 THEN 1 ELSE 0 END) AS fail_count,
                   SUM(CASE WHEN status = 5 THEN 1 ELSE 0 END) AS refund_count,
                   COALESCE(SUM(CASE WHEN status = 5 THEN amount ELSE 0 END), 0) AS refund_amount
            FROM payment_transaction
            WHERE deleted = 0 AND created_at >= #{start} AND created_at < #{end}
            GROUP BY channel
            ORDER BY success_amount DESC
            """)
    List<Map<String, Object>> channelStats(
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("""
            SELECT channel,
                   status,
                   COUNT(*) AS record_count,
                   COALESCE(SUM(channel_amount), 0) AS channel_amount,
                   COALESCE(SUM(local_amount), 0) AS local_amount,
                   COALESCE(SUM(diff_count), 0) AS diff_count
            FROM reconciliation_record
            WHERE biz_date >= #{start} AND biz_date <= #{end}
            GROUP BY channel, status
            ORDER BY channel, status
            """)
    List<Map<String, Object>> reconciliationStats(
            @Param("start") java.time.LocalDate start, @Param("end") java.time.LocalDate end);
}
