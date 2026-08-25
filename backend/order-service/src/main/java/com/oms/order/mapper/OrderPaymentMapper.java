package com.oms.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oms.order.entity.OrderPayment;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface OrderPaymentMapper extends BaseMapper<OrderPayment> {

    @Select("""
            SELECT COALESCE(SUM(amount), 0)
            FROM order_payment
            WHERE order_id = #{orderId} AND status = 2 AND deleted = 0
            """)
    BigDecimal sumPaidAmount(@Param("orderId") Long orderId);
}
