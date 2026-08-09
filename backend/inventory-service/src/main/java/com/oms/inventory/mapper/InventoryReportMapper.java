package com.oms.inventory.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 库存报表统计。
 */
public interface InventoryReportMapper {

    @Select("""
            SELECT w.id AS warehouse_id,
                   w.name AS warehouse_name,
                   COUNT(DISTINCT i.sku_id) AS sku_count,
                   COALESCE(SUM(i.quantity), 0) AS total_quantity,
                   COALESCE(SUM(i.reserved_quantity), 0) AS reserved_quantity,
                   COALESCE(SUM(i.frozen_quantity), 0) AS frozen_quantity
            FROM warehouse w
            LEFT JOIN inventory i ON i.warehouse_id = w.id AND i.deleted = 0
            WHERE w.deleted = 0
            GROUP BY w.id, w.name
            ORDER BY total_quantity DESC
            """)
    List<Map<String, Object>> warehouseStock();

    @Select("""
            SELECT
                CASE
                    WHEN i.expire_at IS NULL THEN '未知'
                    WHEN i.expire_at < CURDATE() THEN '已过期'
                    WHEN DATEDIFF(i.expire_at, CURDATE()) <= 90 THEN '0-90天'
                    WHEN DATEDIFF(i.expire_at, CURDATE()) <= 180 THEN '91-180天'
                    WHEN DATEDIFF(i.expire_at, CURDATE()) <= 365 THEN '181-365天'
                    ELSE '365天以上'
                END AS bucket,
                COUNT(DISTINCT i.sku_id) AS sku_count,
                COALESCE(SUM(i.quantity + i.reserved_quantity + i.frozen_quantity), 0) AS quantity
            FROM inventory i
            WHERE i.deleted = 0
            GROUP BY bucket
            ORDER BY FIELD(bucket, '已过期', '0-90天', '91-180天', '181-365天', '365天以上', '未知')
            """)
    List<Map<String, Object>> expiryDistribution();

    @Select("""
            SELECT t.sku_id,
                   s.sku_no,
                   s.name AS sku_name,
                   COALESCE(SUM(-t.change_quantity), 0) AS outbound_quantity,
                   COALESCE(cur.current_stock, 0) AS current_stock
            FROM inventory_transaction t
            JOIN sku s ON s.id = t.sku_id AND s.deleted = 0
            LEFT JOIN (
                SELECT sku_id, SUM(quantity) AS current_stock
                FROM inventory
                WHERE deleted = 0
                GROUP BY sku_id
            ) cur ON cur.sku_id = t.sku_id
            WHERE t.biz_type = 3 AND t.created_at >= #{start} AND t.created_at < #{end}
            GROUP BY t.sku_id, s.sku_no, s.name, cur.current_stock
            ORDER BY outbound_quantity DESC
            LIMIT #{topN}
            """)
    List<Map<String, Object>> turnover(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("topN") int topN);

    @Select("""
            SELECT i.sku_id,
                   s.sku_no,
                   s.name AS sku_name,
                   SUM(i.quantity) AS current_stock,
                   MAX(t.last_at) AS last_sale_at
            FROM inventory i
            JOIN sku s ON s.id = i.sku_id AND s.deleted = 0
            LEFT JOIN (
                SELECT sku_id, MAX(created_at) AS last_at
                FROM inventory_transaction
                WHERE biz_type IN (3, 6)
                GROUP BY sku_id
            ) t ON t.sku_id = i.sku_id
            WHERE i.deleted = 0
              AND (t.last_at IS NULL OR t.last_at < DATE_SUB(NOW(), INTERVAL #{days} DAY))
            GROUP BY i.sku_id, s.sku_no, s.name
            HAVING current_stock > 0
            ORDER BY last_sale_at ASC, current_stock DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> slowMoving(@Param("days") int days, @Param("limit") int limit);
}
