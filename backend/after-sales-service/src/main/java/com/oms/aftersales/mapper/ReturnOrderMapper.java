package com.oms.aftersales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oms.aftersales.entity.ReturnOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ReturnOrderMapper extends BaseMapper<ReturnOrder> {

    @Select("SELECT * FROM return_order WHERE return_no = #{returnNo} AND deleted = 0 FOR UPDATE")
    ReturnOrder selectByReturnNoForUpdate(@Param("returnNo") String returnNo);
}
