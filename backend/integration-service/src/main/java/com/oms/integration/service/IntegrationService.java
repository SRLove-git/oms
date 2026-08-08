package com.oms.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.ErrorCode;
import com.oms.common.core.result.PageResult;
import com.oms.integration.dto.IntegrationDtos.ExternalOrderMappingResponse;
import com.oms.integration.dto.IntegrationDtos.ExternalOrderPullRequest;
import com.oms.integration.dto.IntegrationDtos.SyncAfterSalesRequest;
import com.oms.integration.dto.IntegrationDtos.SyncShipmentRequest;
import com.oms.integration.dto.IntegrationDtos.SyncStockRequest;
import com.oms.integration.entity.ExternalOrderMapping;
import com.oms.integration.mapper.ExternalOrderMappingMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IntegrationService {

    private static final int STATUS_PULLED = 1;
    private static final int STATUS_MAPPED = 2;
    private static final int STATUS_SYNCED = 3;
    private static final int STATUS_ERROR = 4;

    private final ExternalOrderMappingMapper mappingMapper;

    public IntegrationService(ExternalOrderMappingMapper mappingMapper) {
        this.mappingMapper = mappingMapper;
    }

    @Transactional
    public ExternalOrderMappingResponse pullOrder(ExternalOrderPullRequest request) {
        if (request.platform() == null || request.platformOrderNo() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "platform 与 platformOrderNo 必填");
        }
        ExternalOrderMapping existing = mappingMapper.selectOne(new LambdaQueryWrapper<ExternalOrderMapping>()
                .eq(ExternalOrderMapping::getPlatform, request.platform())
                .eq(ExternalOrderMapping::getPlatformOrderNo, request.platformOrderNo())
                .eq(ExternalOrderMapping::getDeleted, 0)
                .last("LIMIT 1"));
        if (existing != null) {
            if (request.orderNo() != null) {
                existing.setOrderNo(request.orderNo());
                existing.setStatus(STATUS_MAPPED);
                mappingMapper.updateById(existing);
            }
            return toResponse(existing);
        }
        ExternalOrderMapping mapping = new ExternalOrderMapping();
        mapping.setPlatform(request.platform());
        mapping.setPlatformOrderNo(request.platformOrderNo());
        mapping.setOrderNo(request.orderNo());
        mapping.setStatus(request.orderNo() == null ? STATUS_PULLED : STATUS_MAPPED);
        mapping.setRawData(request.rawData());
        mappingMapper.insert(mapping);
        return toResponse(mapping);
    }

    public PageResult<ExternalOrderMappingResponse> pageMappings(
            String platform, Integer status, int page, int size) {
        LambdaQueryWrapper<ExternalOrderMapping> wrapper = new LambdaQueryWrapper<ExternalOrderMapping>()
                .eq(ExternalOrderMapping::getDeleted, 0)
                .orderByDesc(ExternalOrderMapping::getId);
        if (platform != null) {
            wrapper.eq(ExternalOrderMapping::getPlatform, platform);
        }
        if (status != null) {
            wrapper.eq(ExternalOrderMapping::getStatus, status);
        }
        Page<ExternalOrderMapping> result = mappingMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(
                result.getTotal(), result.getRecords().stream().map(this::toResponse).toList());
    }

    @Transactional
    public void syncShipment(SyncShipmentRequest request) {
        ExternalOrderMapping mapping = findMapping(request.platform(), request.platformOrderNo());
        mapping.setStatus(STATUS_SYNCED);
        mappingMapper.updateById(mapping);
    }

    @Transactional
    public void syncAfterSales(SyncAfterSalesRequest request) {
        ExternalOrderMapping mapping = findMapping(request.platform(), request.platformOrderNo());
        mapping.setStatus(STATUS_SYNCED);
        mappingMapper.updateById(mapping);
    }

    public void syncStock(SyncStockRequest request) {
        // 第三方平台库存同步为演示接口：真实场景对接平台库存 API
        if (request.skuNo() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "skuNo 必填");
        }
    }

    private ExternalOrderMapping findMapping(String platform, String platformOrderNo) {
        ExternalOrderMapping mapping = mappingMapper.selectOne(new LambdaQueryWrapper<ExternalOrderMapping>()
                .eq(ExternalOrderMapping::getPlatform, platform)
                .eq(ExternalOrderMapping::getPlatformOrderNo, platformOrderNo)
                .eq(ExternalOrderMapping::getDeleted, 0)
                .last("LIMIT 1"));
        if (mapping == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "平台订单映射不存在");
        }
        return mapping;
    }

    private ExternalOrderMappingResponse toResponse(ExternalOrderMapping mapping) {
        return new ExternalOrderMappingResponse(
                mapping.getId(),
                mapping.getPlatform(),
                mapping.getPlatformOrderNo(),
                mapping.getOrderNo(),
                mapping.getStatus(),
                mapping.getErrorMessage(),
                mapping.getCreatedAt());
    }
}
