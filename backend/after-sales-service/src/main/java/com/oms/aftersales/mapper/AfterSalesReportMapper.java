package com.oms.aftersales.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 售后报表统计。
 */
public interface AfterSalesReportMapper {

    @Select("""
            SELECT r.type,
                   COUNT(*) AS count,
                   COALESCE(SUM(r.total_amount), 0) AS total_amount,
                   SUM(CASE WHEN r.status = 6 THEN 1 ELSE 0 END) AS completed_count
            FROM return_order r
            WHERE r.deleted = 0 AND r.created_at >= #{start} AND r.created_at < #{end}
            GROUP BY r.type
            ORDER BY r.type
            """)
    List<Map<String, Object>> typeStats(
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("""
            SELECT r.type,
                   COALESCE(SUM(f.amount), 0) AS refunded_amount
            FROM return_order r
            LEFT JOIN refund_record f ON f.return_id = r.id AND f.status = 3
            WHERE r.deleted = 0 AND r.created_at >= #{start} AND r.created_at < #{end}
            GROUP BY r.type
            ORDER BY r.type
            """)
    List<Map<String, Object>> refundedByType(
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("""
            SELECT COALESCE(NULLIF(r.reason, ''), '未填写') AS reason,
                   COUNT(*) AS count
            FROM return_order r
            WHERE r.deleted = 0 AND r.created_at >= #{start} AND r.created_at < #{end}
            GROUP BY reason
            ORDER BY count DESC
            LIMIT #{topN}
            """)
    List<Map<String, Object>> reasonDistribution(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("topN") int topN);

    @Select("""
            SELECT COUNT(*) AS repair_count,
                   COALESCE(AVG(TIMESTAMPDIFF(MINUTE, created_at, finished_at)), 0) AS avg_minutes,
                   COALESCE(MIN(TIMESTAMPDIFF(MINUTE, created_at, finished_at)), 0) AS min_minutes,
                   COALESCE(MAX(TIMESTAMPDIFF(MINUTE, created_at, finished_at)), 0) AS max_minutes
            FROM repair_order
            WHERE status = 4 AND deleted = 0
              AND finished_at IS NOT NULL
              AND created_at >= #{start} AND created_at < #{end}
            """)
    Map<String, Object> repairDuration(
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
