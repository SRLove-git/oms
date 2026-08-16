package com.oms.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oms.order.entity.OrderItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface OrderItemMapper extends BaseMapper<OrderItem> {

    @Select("SELECT COUNT(*) FROM order_item WHERE sku_id = #{skuId}")
    long countBySkuId(@Param("skuId") Long skuId);
}
