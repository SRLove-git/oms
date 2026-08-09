package com.oms.order.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oms.order.entity.OrderArchive;
import com.oms.order.entity.OrderItem;
import com.oms.order.entity.OrderLog;
import com.oms.order.entity.OrderPayment;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 历史订单归档：写入、读取与归档批次扫描。
 */
public interface OrderArchiveMapper extends BaseMapper<OrderArchive> {

    @Select("""
            SELECT id FROM `order`
            WHERE deleted = 0 AND status IN (6, 7)
              AND updated_at < DATE_SUB(NOW(), INTERVAL #{days} DAY)
            ORDER BY id
            LIMIT #{limit}
            """)
    List<Long> selectArchivableIds(@Param("days") int days, @Param("limit") int limit);

    @Insert("""
            <script>
            INSERT INTO order_archive (
                id, order_no, merchant_id, customer_id, order_type, status,
                total_amount, pay_amount, discount_amount, currency, warehouse_id, remark,
                paid_at, cancelled_at, timeout_at, version, deleted, created_at, updated_at, archived_at)
            SELECT
                id, order_no, merchant_id, customer_id, order_type, status,
                total_amount, pay_amount, discount_amount, currency, warehouse_id, remark,
                paid_at, cancelled_at, timeout_at, version, deleted, created_at, updated_at, NOW()
            FROM `order`
            WHERE id IN
            <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            </script>
            """)
    int archiveOrders(@Param("ids") List<Long> ids);

    @Insert("""
            <script>
            INSERT INTO order_item_archive (
                id, order_id, sku_id, spu_id, sku_name, quantity, unit_price,
                total_price, cost_amount, batch_no, serial_no, expire_at,
                version, deleted, created_at, updated_at)
            SELECT
                id, order_id, sku_id, spu_id, sku_name, quantity, unit_price,
                total_price, cost_amount, batch_no, serial_no, expire_at,
                version, deleted, created_at, updated_at
            FROM order_item
            WHERE order_id IN
            <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            </script>
            """)
    int archiveItems(@Param("ids") List<Long> ids);

    @Insert("""
            <script>
            INSERT INTO order_log_archive (
                id, order_id, from_status, to_status, operator_id, operator_name, remark, created_at)
            SELECT
                id, order_id, from_status, to_status, operator_id, operator_name, remark, created_at
            FROM order_log
            WHERE order_id IN
            <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            </script>
            """)
    int archiveLogs(@Param("ids") List<Long> ids);

    @Insert("""
            <script>
            INSERT INTO order_payment_archive (
                id, order_id, payment_no, channel, amount, currency, status,
                channel_txn_no, paid_at, version, deleted, created_at, updated_at)
            SELECT
                id, order_id, payment_no, channel, amount, currency, status,
                channel_txn_no, paid_at, version, deleted, created_at, updated_at
            FROM order_payment
            WHERE order_id IN
            <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            </script>
            """)
    int archivePayments(@Param("ids") List<Long> ids);

    @Delete("""
            <script>
            DELETE FROM `order` WHERE id IN
            <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            </script>
            """)
    int deleteHotOrders(@Param("ids") List<Long> ids);

    @Delete("""
            <script>
            DELETE FROM order_item WHERE order_id IN
            <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            </script>
            """)
    int deleteHotItems(@Param("ids") List<Long> ids);

    @Delete("""
            <script>
            DELETE FROM order_log WHERE order_id IN
            <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            </script>
            """)
    int deleteHotLogs(@Param("ids") List<Long> ids);

    @Delete("""
            <script>
            DELETE FROM order_payment WHERE order_id IN
            <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            </script>
            """)
    int deleteHotPayments(@Param("ids") List<Long> ids);

    @Select("SELECT * FROM order_archive WHERE order_no = #{orderNo} AND deleted = 0 LIMIT 1")
    OrderArchive findByOrderNo(@Param("orderNo") String orderNo);

    @Select("SELECT * FROM order_archive WHERE deleted = 0 ORDER BY id DESC")
    Page<OrderArchive> pageArchived(Page<OrderArchive> page);

    @Select("SELECT * FROM order_item_archive WHERE order_id = #{orderId} ORDER BY id")
    List<OrderItem> itemsOf(@Param("orderId") Long orderId);

    @Select("SELECT * FROM order_log_archive WHERE order_id = #{orderId} ORDER BY id")
    List<OrderLog> logsOf(@Param("orderId") Long orderId);

    @Select("SELECT * FROM order_payment_archive WHERE order_id = #{orderId} ORDER BY id")
    List<OrderPayment> paymentsOf(@Param("orderId") Long orderId);
}
