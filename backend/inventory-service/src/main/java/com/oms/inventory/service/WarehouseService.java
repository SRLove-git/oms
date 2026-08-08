package com.oms.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.ErrorCode;
import com.oms.inventory.dto.WarehouseDtos.WarehouseCreateRequest;
import com.oms.inventory.dto.WarehouseDtos.WarehouseResponse;
import com.oms.inventory.entity.Warehouse;
import com.oms.inventory.mapper.WarehouseMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WarehouseService {

    private final WarehouseMapper warehouseMapper;

    public WarehouseService(WarehouseMapper warehouseMapper) {
        this.warehouseMapper = warehouseMapper;
    }

    public Long create(WarehouseCreateRequest request) {
        Long exists = warehouseMapper.selectCount(new LambdaQueryWrapper<Warehouse>()
                .eq(Warehouse::getCode, request.code())
                .eq(Warehouse::getDeleted, 0));
        if (exists > 0) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "仓库编码已存在");
        }
        Warehouse warehouse = new Warehouse();
        warehouse.setCode(request.code());
        warehouse.setName(request.name());
        warehouse.setAddress(request.address());
        warehouse.setStatus(1);
        warehouseMapper.insert(warehouse);
        return warehouse.getId();
    }

    public List<WarehouseResponse> list() {
        return warehouseMapper
                .selectList(new LambdaQueryWrapper<Warehouse>()
                        .eq(Warehouse::getDeleted, 0)
                        .orderByAsc(Warehouse::getId))
                .stream()
                .map(w -> new WarehouseResponse(w.getId(), w.getCode(), w.getName(), w.getAddress(), w.getStatus()))
                .toList();
    }
}
