package com.oms.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oms.inventory.entity.Inventory;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface InventoryMapper extends BaseMapper<Inventory> {

    @Select("SELECT COUNT(*) FROM inventory WHERE sku_id = #{skuId}")
    long countBySkuId(@Param("skuId") Long skuId);
}
