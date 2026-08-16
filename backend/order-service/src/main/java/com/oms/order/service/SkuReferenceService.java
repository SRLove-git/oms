package com.oms.order.service;

import com.oms.order.mapper.OrderArchiveMapper;
import com.oms.order.mapper.OrderItemMapper;
import org.springframework.stereotype.Service;

@Service
public class SkuReferenceService {

    private final OrderItemMapper orderItemMapper;
    private final OrderArchiveMapper orderArchiveMapper;

    public SkuReferenceService(OrderItemMapper orderItemMapper, OrderArchiveMapper orderArchiveMapper) {
        this.orderItemMapper = orderItemMapper;
        this.orderArchiveMapper = orderArchiveMapper;
    }

    public SkuReferenceCheck check(Long skuId) {
        long activeCount = orderItemMapper.countBySkuId(skuId);
        long archivedCount = orderArchiveMapper.countItemsBySkuId(skuId);
        return new SkuReferenceCheck(activeCount + archivedCount > 0, activeCount, archivedCount);
    }

    public record SkuReferenceCheck(boolean hasOrders, long activeCount, long archivedCount) {
    }
}
