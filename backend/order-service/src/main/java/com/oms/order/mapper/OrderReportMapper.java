package com.oms.order.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 订单报表统计：热表与归档表 UNION 聚合，保证长周期报表口径完整。
 */
public interface OrderReportMapper {

    String PAID_FILTER = "o.status NOT IN (1, 7)";
    String MERCHANT_FILTER = "(#{merchantId} IS NULL OR o.merchant_id = #{merchantId})";

    String ORDER_UNION = """
            (
                SELECT id, merchant_id, order_type, status, pay_amount, paid_at
                FROM `order` WHERE deleted = 0
                UNION ALL
                SELECT id, merchant_id, order_type, status, pay_amount, paid_at
                FROM order_archive
            ) o
            """;

    @Select("""
            SELECT COUNT(*) AS order_count
            FROM (
                SELECT id, merchant_id, created_at FROM `order` WHERE deleted = 0
                UNION ALL
                SELECT id, merchant_id, created_at FROM order_archive
            ) o
            WHERE o.created_at >= #{start} AND o.created_at < #{end} """
            + " AND "
            + MERCHANT_FILTER)
    long countOrders(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("merchantId") Long merchantId);

    @Select("""
            SELECT
                COUNT(*) AS paid_order_count,
                COALESCE(SUM(o.pay_amount), 0) AS paid_amount
            FROM """
            + ORDER_UNION
            + """
            WHERE o.paid_at >= #{start} AND o.paid_at < #{end} """
            + " AND "
            + PAID_FILTER
            + " AND "
            + MERCHANT_FILTER)
    Map<String, Object> paidSummary(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("merchantId") Long merchantId);

    @Select("""
            SELECT
                COALESCE(SUM(o.pay_amount), 0) AS paid_amount,
                COALESCE(SUM(oi.cost_amount), 0) AS cost_amount
            FROM """
            + ORDER_UNION
            + """
            JOIN (
                SELECT order_id, cost_amount FROM order_item WHERE deleted = 0
                UNION ALL
                SELECT order_id, cost_amount FROM order_item_archive
            ) oi ON oi.order_id = o.id
            WHERE o.paid_at >= #{start} AND o.paid_at < #{end} """
            + " AND "
            + PAID_FILTER
            + " AND "
            + MERCHANT_FILTER)
    Map<String, Object> grossProfit(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("merchantId") Long merchantId);

    @Select("""
            SELECT COALESCE(SUM(amount), 0) AS refund_amount
            FROM (
                SELECT amount, updated_at FROM order_payment WHERE status = 4
                UNION ALL
                SELECT amount, updated_at FROM order_payment_archive WHERE status = 4
            ) p
            WHERE p.updated_at >= #{start} AND p.updated_at < #{end}
            """)
    BigDecimal refundAmount(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("""
            SELECT COUNT(*) AS total_customers
            FROM (
                SELECT customer_id FROM `order`
                WHERE deleted = 0 AND customer_id IS NOT NULL AND status NOT IN (1, 7)
                  AND paid_at >= #{start} AND paid_at < #{end}
                  AND (#{merchantId} IS NULL OR merchant_id = #{merchantId})
                UNION ALL
                SELECT customer_id FROM order_archive
                WHERE customer_id IS NOT NULL AND status NOT IN (1, 7)
                  AND paid_at >= #{start} AND paid_at < #{end}
                  AND (#{merchantId} IS NULL OR merchant_id = #{merchantId})
            ) t
            """)
    long repurchaseTotalCustomers(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("merchantId") Long merchantId);

    @Select("""
            SELECT COUNT(*) AS repeat_customers
            FROM (
                SELECT customer_id FROM (
                    SELECT customer_id FROM `order`
                    WHERE deleted = 0 AND customer_id IS NOT NULL AND status NOT IN (1, 7)
                      AND paid_at >= #{start} AND paid_at < #{end}
                      AND (#{merchantId} IS NULL OR merchant_id = #{merchantId})
                    UNION ALL
                    SELECT customer_id FROM order_archive
                    WHERE customer_id IS NOT NULL AND status NOT IN (1, 7)
                      AND paid_at >= #{start} AND paid_at < #{end}
                      AND (#{merchantId} IS NULL OR merchant_id = #{merchantId})
                ) x
                GROUP BY customer_id
                HAVING COUNT(*) >= 2
            ) y
            """)
    long repurchaseRepeatCustomers(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("merchantId") Long merchantId);

    @Select("""
            SELECT DATE(o.paid_at) AS biz_date,
                   COUNT(*)        AS paid_order_count,
                   COALESCE(SUM(o.pay_amount), 0) AS paid_amount
            FROM """
            + ORDER_UNION
            + """
            WHERE o.paid_at >= #{start} AND o.paid_at < #{end} """
            + " AND "
            + PAID_FILTER
            + " AND "
            + MERCHANT_FILTER
            + """
            GROUP BY DATE(o.paid_at)
            ORDER BY biz_date
            """)
    List<Map<String, Object>> salesTrend(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("merchantId") Long merchantId);

    @Select("""
            SELECT o.order_type,
                   COUNT(*) AS order_count,
                   COALESCE(SUM(o.pay_amount), 0) AS paid_amount
            FROM """
            + ORDER_UNION
            + """
            WHERE o.paid_at >= #{start} AND o.paid_at < #{end} """
            + " AND "
            + PAID_FILTER
            + " AND "
            + MERCHANT_FILTER
            + """
            GROUP BY o.order_type
            ORDER BY paid_amount DESC
            """)
    List<Map<String, Object>> orderSource(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("merchantId") Long merchantId);

    @Select("""
            SELECT COUNT(*) AS completed_count
            FROM (
                SELECT id, merchant_id, status, updated_at FROM `order` WHERE deleted = 0
                UNION ALL
                SELECT id, merchant_id, status, updated_at FROM order_archive
            ) o
            WHERE o.status = 6 AND o.updated_at >= #{start} AND o.updated_at < #{end}
            """)
    long completedOrderCount(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
