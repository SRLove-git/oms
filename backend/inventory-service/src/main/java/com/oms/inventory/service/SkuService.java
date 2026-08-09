package com.oms.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.ErrorCode;
import com.oms.common.core.result.PageResult;
import com.oms.inventory.dto.SkuDtos.SkuCreateRequest;
import com.oms.inventory.dto.SkuDtos.SkuResponse;
import com.oms.inventory.entity.Sku;
import com.oms.inventory.entity.Spu;
import com.oms.inventory.mapper.SkuMapper;
import com.oms.inventory.mapper.SpuMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SkuService {

    private final SkuMapper skuMapper;
    private final SpuMapper spuMapper;

    public SkuService(SkuMapper skuMapper, SpuMapper spuMapper) {
        this.skuMapper = skuMapper;
        this.spuMapper = spuMapper;
    }

    public Long createSku(SkuCreateRequest request) {
        Spu spu = spuMapper.selectOne(new LambdaQueryWrapper<Spu>()
                .eq(Spu::getSpuNo, request.spuNo())
                .eq(Spu::getDeleted, 0)
                .last("LIMIT 1"));
        if (spu == null) {
            spu = new Spu();
            spu.setSpuNo(request.spuNo());
            spu.setName(request.spuName());
            spu.setStatus(1);
            spuMapper.insert(spu);
        }

        Long exists = skuMapper.selectCount(new LambdaQueryWrapper<Sku>()
                .eq(Sku::getSkuNo, request.skuNo())
                .eq(Sku::getDeleted, 0));
        if (exists > 0) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "SKU 编码已存在");
        }

        Sku sku = new Sku();
        sku.setSpuId(spu.getId());
        sku.setSkuNo(request.skuNo());
        sku.setName(request.name());
        sku.setSpec(request.spec());
        sku.setBarcode(request.barcode());
        sku.setUdi(request.udi());
        sku.setRegistrationNo(request.registrationNo());
        sku.setPrice(request.price() == null ? BigDecimal.ZERO : request.price());
        sku.setCostPrice(request.costPrice() == null ? BigDecimal.ZERO : request.costPrice());
        sku.setStatus(1);
        skuMapper.insert(sku);
        return sku.getId();
    }

    public void updateStatus(Long skuId, Integer status) {
        Sku sku = skuMapper.selectById(skuId);
        if (sku == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "status 仅支持 0/1");
        }
        sku.setStatus(status);
        skuMapper.updateById(sku);
    }

    public SkuResponse get(Long skuId) {
        Sku sku = skuMapper.selectById(skuId);
        if (sku == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        Spu spu = spuMapper.selectById(sku.getSpuId());
        return toResponse(sku, spu == null ? null : spu.getSpuNo());
    }

    public PageResult<SkuResponse> page(String keyword, int page, int size) {
        LambdaQueryWrapper<Sku> wrapper = new LambdaQueryWrapper<Sku>()
                .eq(Sku::getDeleted, 0)
                .orderByDesc(Sku::getId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Sku::getName, keyword).or().like(Sku::getSkuNo, keyword));
        }
        Page<Sku> result = skuMapper.selectPage(new Page<>(page, size), wrapper);
        List<Sku> records = result.getRecords();
        Map<Long, Spu> spuMap = records.isEmpty()
                ? Map.of()
                : spuMapper.selectBatchIds(records.stream().map(Sku::getSpuId).distinct().toList()).stream()
                        .collect(Collectors.toMap(Spu::getId, Function.identity()));
        return PageResult.of(
                result.getTotal(),
                records.stream()
                        .map(sku -> {
                            Spu spu = spuMap.get(sku.getSpuId());
                            return toResponse(sku, spu == null ? null : spu.getSpuNo());
                        })
                        .toList());
    }

    private SkuResponse toResponse(Sku sku, String spuNo) {
        return new SkuResponse(
                sku.getId(),
                sku.getSpuId(),
                spuNo,
                sku.getSkuNo(),
                sku.getName(),
                sku.getSpec(),
                sku.getBarcode(),
                sku.getUdi(),
                sku.getRegistrationNo(),
                sku.getPrice(),
                sku.getCostPrice(),
                sku.getStatus());
    }
}
