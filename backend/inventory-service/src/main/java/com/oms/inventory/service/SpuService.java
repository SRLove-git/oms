package com.oms.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.ErrorCode;
import com.oms.inventory.dto.SpuDtos.SpuCreateRequest;
import com.oms.inventory.dto.SpuDtos.SpuResponse;
import com.oms.inventory.entity.Spu;
import com.oms.inventory.mapper.SpuMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SpuService {

    private final SpuMapper spuMapper;

    public SpuService(SpuMapper spuMapper) {
        this.spuMapper = spuMapper;
    }

    public Long create(SpuCreateRequest request) {
        Long exists = spuMapper.selectCount(new LambdaQueryWrapper<Spu>()
                .eq(Spu::getSpuNo, request.spuNo())
                .eq(Spu::getDeleted, 0));
        if (exists > 0) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "SPU 编码已存在");
        }
        Spu spu = new Spu();
        spu.setSpuNo(request.spuNo());
        spu.setName(request.name());
        spu.setCategoryId(request.categoryId());
        spu.setBrand(request.brand());
        spu.setStatus(1);
        spuMapper.insert(spu);
        return spu.getId();
    }

    public List<SpuResponse> list() {
        return spuMapper
                .selectList(new LambdaQueryWrapper<Spu>()
                        .eq(Spu::getDeleted, 0)
                        .orderByDesc(Spu::getId))
                .stream()
                .map(s -> new SpuResponse(s.getId(), s.getSpuNo(), s.getName(), s.getBrand(), s.getStatus()))
                .toList();
    }
}
