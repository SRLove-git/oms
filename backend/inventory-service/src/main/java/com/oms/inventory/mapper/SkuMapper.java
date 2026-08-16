package com.oms.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oms.inventory.entity.Sku;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface SkuMapper extends BaseMapper<Sku> {

    @Delete("DELETE FROM sku WHERE id = #{id}")
    int deletePhysically(@Param("id") Long id);
}
