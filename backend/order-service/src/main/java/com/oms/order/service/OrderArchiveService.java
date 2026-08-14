package com.oms.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.ErrorCode;
import com.oms.common.core.result.PageResult;
import com.oms.order.dto.OrderDtos.OrderItemResponse;
import com.oms.order.dto.OrderDtos.OrderLogResponse;
import com.oms.order.dto.OrderDtos.OrderResponse;
import com.oms.order.dto.OrderDtos.OrderSummaryResponse;
import com.oms.order.entity.OrderArchive;
import com.oms.order.entity.OrderItem;
import com.oms.order.entity.OrderLog;
import com.oms.order.mapper.OrderArchiveMapper;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderArchiveService {

    private final OrderArchiveMapper archiveMapper;

    @Value("${oms.order.archive-days:180}")
    private int archiveDays;

    @Value("${oms.order.archive-batch-size:200}")
    private int archiveBatchSize;

    public OrderArchiveService(OrderArchiveMapper archiveMapper) {
        this.archiveMapper = archiveMapper;
    }

    /**
     * 归档一批终态历史订单（含明细/日志/支付单），热表物理删除、归档表承接。
     * 冷热分离边界由 {@code oms.order.archive-days} 控制。
     */
    @Transactional
    public int archiveOnce() {
        List<Long> ids = archiveMapper.selectArchivableIds(archiveDays, archiveBatchSize);
        if (ids.isEmpty()) {
            return 0;
        }
        archiveMapper.archiveOrders(ids);
        archiveMapper.archiveItems(ids);
        archiveMapper.archiveLogs(ids);
        archiveMapper.archivePayments(ids);
        archiveMapper.deleteHotOrders(ids);
        archiveMapper.deleteHotItems(ids);
        archiveMapper.deleteHotLogs(ids);
        archiveMapper.deleteHotPayments(ids);
        return ids.size();
    }

    /**
     * 循环归档直至当前批次为空，返回累计归档单数。
     */
    public int archiveAll() {
        int total = 0;
        int batch;
        do {
            batch = archiveOnce();
            total += batch;
        } while (batch > 0);
        return total;
    }

    public PageResult<OrderSummaryResponse> pageArchived(Long merchantId, int page, int size) {
        LambdaQueryWrapper<OrderArchive> wrapper = new LambdaQueryWrapper<OrderArchive>()
                .eq(OrderArchive::getDeleted, 0)
                .orderByDesc(OrderArchive::getId);
        if (merchantId != null) {
            wrapper.eq(OrderArchive::getMerchantId, merchantId);
        }
        Page<OrderArchive> result = archiveMapper.selectPage(new Page<>(page, size), wrapper);
        List<OrderArchive> records = result.getRecords();
        List<Long> orderIds = records.stream().map(OrderArchive::getId).toList();
        Map<Long, Long> itemCounts = orderIds.isEmpty()
                ? Map.of()
                : orderIds.stream()
                        .collect(Collectors.toMap(
                                id -> id,
                                id -> (long) archiveMapper.itemsOf(id).size()));
        return PageResult.of(
                result.getTotal(),
                records.stream()
                        .map(o -> new OrderSummaryResponse(
                                o.getId(),
                                o.getOrderNo(),
                                o.getMerchantId(),
                                o.getOrderType(),
                                o.getStatus(),
                                o.getTotalAmount(),
                                o.getPayAmount(),
                                o.getCreatedAt(),
                                itemCounts.getOrDefault(o.getId(), 0L).intValue()))
                        .toList());
    }

    public OrderResponse getByOrderNo(String orderNo) {
        OrderArchive order = archiveMapper.findByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "订单不存在");
        }
        List<OrderItem> items = archiveMapper.itemsOf(order.getId());
        List<OrderLog> logs = archiveMapper.logsOf(order.getId());
        return new OrderResponse(
                order.getId(),
                order.getOrderNo(),
                order.getMerchantId(),
                order.getOrderType(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getPayAmount(),
                order.getCurrency(),
                order.getRemark(),
                order.getPaidAt(),
                order.getTimeoutAt(),
                order.getCreatedAt(),
                items.stream()
                        .map(item -> new OrderItemResponse(
                                item.getId(),
                                item.getSkuId(),
                                item.getSkuName(),
                                item.getQuantity(),
                                item.getUnitPrice(),
                                item.getTotalPrice()))
                        .toList(),
                logs.stream()
                        .map(logItem -> new OrderLogResponse(
                                logItem.getFromStatus(),
                                logItem.getToStatus(),
                                logItem.getOperatorName(),
                                logItem.getRemark(),
                                logItem.getCreatedAt()))
                        .toList());
    }

    /**
     * 按外部订单号（商城开放 API 幂等键）查询归档订单，未命中返回 null。
     */
    public OrderArchive findByExternalOrderNo(String externalOrderNo) {
        return archiveMapper.selectOne(new LambdaQueryWrapper<OrderArchive>()
                .eq(OrderArchive::getExternalOrderNo, externalOrderNo)
                .eq(OrderArchive::getDeleted, 0)
                .last("LIMIT 1"));
    }
}
