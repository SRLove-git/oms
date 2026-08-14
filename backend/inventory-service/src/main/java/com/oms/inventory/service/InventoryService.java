package com.oms.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.ErrorCode;
import com.oms.common.core.result.PageResult;
import com.oms.inventory.dto.InventoryDtos.InboundRequest;
import com.oms.inventory.dto.InventoryDtos.InventoryResponse;
import com.oms.inventory.dto.InventoryDtos.ReserveItem;
import com.oms.inventory.dto.InventoryDtos.ReserveRequest;
import com.oms.inventory.dto.InventoryDtos.TransactionResponse;
import com.oms.inventory.entity.Inventory;
import com.oms.inventory.entity.InventoryTransaction;
import com.oms.inventory.entity.Sku;
import com.oms.inventory.mapper.InventoryMapper;
import com.oms.inventory.mapper.InventoryTransactionMapper;
import com.oms.inventory.mapper.SkuMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class InventoryService {

    private static final int BIZ_INBOUND = 5;
    private static final int BIZ_RESERVE = 1;
    private static final int BIZ_RELEASE = 2;
    private static final int BIZ_DEDUCT = 3;
    private static final int BIZ_RESTORE = 4;

    private final InventoryMapper inventoryMapper;
    private final InventoryTransactionMapper transactionMapper;
    private final SkuMapper skuMapper;

    public InventoryService(
            InventoryMapper inventoryMapper,
            InventoryTransactionMapper transactionMapper,
            SkuMapper skuMapper) {
        this.inventoryMapper = inventoryMapper;
        this.transactionMapper = transactionMapper;
        this.skuMapper = skuMapper;
    }

    @Transactional
    public void inbound(InboundRequest request, Long operatorId) {
        Inventory row = findRow(request.warehouseId(), request.skuId(), request.batchNo());
        int before = row.getQuantity();
        int affected = inventoryMapper.update(
                null,
                new LambdaUpdateWrapper<Inventory>()
                        .eq(Inventory::getId, row.getId())
                        .setSql("quantity = quantity + " + request.quantity()));
        if (affected == 0) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "库存更新失败");
        }
        recordTransaction(row, BIZ_INBOUND, request.quantity(), operatorId, "INBOUND", request.remark());
    }

    @Transactional
    public void reserve(ReserveRequest request, Long operatorId) {
        for (ReserveItem item : request.items()) {
            int needed = item.quantity();
            List<Inventory> rows = availableRows(item.skuId());
            for (Inventory row : rows) {
                if (needed <= 0) {
                    break;
                }
                int take = Math.min(needed, row.getQuantity());
                int affected = inventoryMapper.update(
                        null,
                        new LambdaUpdateWrapper<Inventory>()
                                .eq(Inventory::getId, row.getId())
                                .ge(Inventory::getQuantity, take)
                                .setSql("quantity = quantity - " + take)
                                .setSql("reserved_quantity = reserved_quantity + " + take));
                if (affected > 0) {
                    needed -= take;
                    recordTransaction(row, BIZ_RESERVE, -take, operatorId, request.orderNo(), "下单预占");
                }
            }
            if (needed > 0) {
                throw new BusinessException(ErrorCode.CONFLICT.getCode(), "SKU " + item.skuId() + " 库存不足");
            }
        }
    }

    @Transactional
    public void release(ReserveRequest request, Long operatorId) {
        for (ReserveItem item : request.items()) {
            int needed = item.quantity();
            List<Inventory> rows = reservedRows(item.skuId());
            for (Inventory row : rows) {
                if (needed <= 0) {
                    break;
                }
                int take = Math.min(needed, row.getReservedQuantity());
                int affected = inventoryMapper.update(
                        null,
                        new LambdaUpdateWrapper<Inventory>()
                                .eq(Inventory::getId, row.getId())
                                .ge(Inventory::getReservedQuantity, take)
                                .setSql("quantity = quantity + " + take)
                                .setSql("reserved_quantity = reserved_quantity - " + take));
                if (affected > 0) {
                    needed -= take;
                    recordTransaction(row, BIZ_RELEASE, take, operatorId, request.orderNo(), "取消/超时释放");
                }
            }
            if (needed > 0) {
                throw new BusinessException(ErrorCode.CONFLICT.getCode(), "SKU " + item.skuId() + " 预占数量不足");
            }
        }
    }

    @Transactional
    public void deduct(ReserveRequest request, Long operatorId) {
        for (ReserveItem item : request.items()) {
            int needed = item.quantity();
            List<Inventory> rows = reservedRows(item.skuId());
            for (Inventory row : rows) {
                if (needed <= 0) {
                    break;
                }
                int take = Math.min(needed, row.getReservedQuantity());
                int beforeReserved = row.getReservedQuantity();
                int affected = inventoryMapper.update(
                        null,
                        new LambdaUpdateWrapper<Inventory>()
                                .eq(Inventory::getId, row.getId())
                                .ge(Inventory::getReservedQuantity, take)
                                .setSql("reserved_quantity = reserved_quantity - " + take));
                if (affected > 0) {
                    needed -= take;
                    recordDeductTransaction(row, take, beforeReserved, operatorId, request.orderNo());
                }
            }
            if (needed > 0) {
                throw new BusinessException(ErrorCode.CONFLICT.getCode(), "SKU " + item.skuId() + " 预占数量不足");
            }
        }
    }

    @Transactional
    public void restore(ReserveRequest request, Long operatorId) {
        for (ReserveItem item : request.items()) {
            int take = item.quantity();
            List<Inventory> rows = anyRows(item.skuId());
            for (Inventory row : rows) {
                if (take <= 0) {
                    break;
                }
                int affected = inventoryMapper.update(
                        null,
                        new LambdaUpdateWrapper<Inventory>()
                                .eq(Inventory::getId, row.getId())
                                .setSql("quantity = quantity + " + take));
                if (affected > 0) {
                    recordTransaction(row, BIZ_RESTORE, take, operatorId, request.orderNo(), "退货/取消回补");
                    take = 0;
                }
            }
        }
    }

    public PageResult<InventoryResponse> page(Long warehouseId, Long skuId, int page, int size) {
        LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getDeleted, 0)
                .orderByDesc(Inventory::getId);
        if (warehouseId != null) {
            wrapper.eq(Inventory::getWarehouseId, warehouseId);
        }
        if (skuId != null) {
            wrapper.eq(Inventory::getSkuId, skuId);
        }
        Page<Inventory> result = inventoryMapper.selectPage(new Page<>(page, size), wrapper);
        List<Inventory> records = result.getRecords();
        Map<Long, Sku> skuMap = records.isEmpty()
                ? Map.of()
                : skuMapper.selectBatchIds(records.stream().map(Inventory::getSkuId).distinct().toList()).stream()
                        .collect(Collectors.toMap(Sku::getId, Function.identity()));
        return PageResult.of(
                result.getTotal(),
                records.stream()
                        .map(row -> toResponse(row, skuMap.get(row.getSkuId())))
                        .toList());
    }

    /**
     * 可售库存：多批次 quantity 合计（预占已在 quantity 上扣减，reserved 仅作标记）。
     */
    public int availableStock(Long skuId) {
        return inventoryMapper.selectList(new LambdaQueryWrapper<Inventory>()
                        .eq(Inventory::getSkuId, skuId)
                        .eq(Inventory::getDeleted, 0))
                .stream()
                .mapToInt(Inventory::getQuantity)
                .sum();
    }

    /**
     * 批量可售库存：商城开放 API 商品列表使用。
     */
    public Map<Long, Integer> availableStocks(List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return Map.of();
        }
        return inventoryMapper.selectList(new LambdaQueryWrapper<Inventory>()
                        .in(Inventory::getSkuId, skuIds)
                        .eq(Inventory::getDeleted, 0))
                .stream()
                .collect(Collectors.groupingBy(
                        Inventory::getSkuId, Collectors.summingInt(Inventory::getQuantity)));
    }

    public PageResult<TransactionResponse> pageTransactions(Long skuId, String bizNo, int page, int size) {        LambdaQueryWrapper<InventoryTransaction> wrapper = new LambdaQueryWrapper<InventoryTransaction>()
                .orderByDesc(InventoryTransaction::getId);
        if (skuId != null) {
            wrapper.eq(InventoryTransaction::getSkuId, skuId);
        }
        if (StringUtils.hasText(bizNo)) {
            wrapper.eq(InventoryTransaction::getBizNo, bizNo);
        }
        Page<InventoryTransaction> result = transactionMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(
                result.getTotal(),
                result.getRecords().stream()
                        .map(tx -> new TransactionResponse(
                                tx.getId(),
                                tx.getWarehouseId(),
                                tx.getSkuId(),
                                tx.getBatchNo(),
                                tx.getBizType(),
                                tx.getBizNo(),
                                tx.getChangeQuantity(),
                                tx.getBeforeQuantity(),
                                tx.getAfterQuantity(),
                                tx.getOperatorId(),
                                tx.getRemark(),
                                tx.getCreatedAt()))
                        .toList());
    }

    private Inventory findRow(Long warehouseId, Long skuId, String batchNo) {
        String batch = batchNo == null ? "" : batchNo;
        Inventory row = inventoryMapper.selectOne(new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getWarehouseId, warehouseId)
                .eq(Inventory::getSkuId, skuId)
                .eq(Inventory::getBatchNo, batch)
                .last("LIMIT 1"));
        if (row == null) {
            row = new Inventory();
            row.setWarehouseId(warehouseId);
            row.setSkuId(skuId);
            row.setBatchNo(batch);
            row.setQuantity(0);
            row.setReservedQuantity(0);
            row.setFrozenQuantity(0);
            row.setExpireAt(LocalDate.now().plusYears(3));
            inventoryMapper.insert(row);
        }
        return row;
    }

    private List<Inventory> availableRows(Long skuId) {
        return inventoryMapper.selectList(new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getSkuId, skuId)
                .gt(Inventory::getQuantity, 0)
                .orderByAsc(Inventory::getExpireAt)
                .orderByAsc(Inventory::getId)
                .last("FOR UPDATE"));
    }

    private List<Inventory> reservedRows(Long skuId) {
        return inventoryMapper.selectList(new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getSkuId, skuId)
                .gt(Inventory::getReservedQuantity, 0)
                .orderByAsc(Inventory::getExpireAt)
                .orderByAsc(Inventory::getId)
                .last("FOR UPDATE"));
    }

    private List<Inventory> anyRows(Long skuId) {
        return inventoryMapper.selectList(new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getSkuId, skuId)
                .orderByAsc(Inventory::getExpireAt)
                .orderByAsc(Inventory::getId)
                .last("FOR UPDATE"));
    }

    private void recordTransaction(
            Inventory row, int bizType, int changeQuantity, Long operatorId, String bizNo, String remark) {
        InventoryTransaction tx = new InventoryTransaction();
        tx.setWarehouseId(row.getWarehouseId());
        tx.setSkuId(row.getSkuId());
        tx.setBatchNo(row.getBatchNo());
        tx.setBizType(bizType);
        tx.setBizNo(bizNo);
        tx.setChangeQuantity(changeQuantity);
        tx.setBeforeQuantity(row.getQuantity());
        tx.setAfterQuantity(row.getQuantity() + changeQuantity);
        tx.setOperatorId(operatorId);
        tx.setRemark(remark);
        transactionMapper.insert(tx);
    }

    /**
     * 扣减流水：change_quantity 记录出库数量（负值），before/after 记录预占数量变化，
     * 保证流水守恒并支撑库存周转率报表。
     */
    private void recordDeductTransaction(
            Inventory row, int take, int beforeReserved, Long operatorId, String bizNo) {
        InventoryTransaction tx = new InventoryTransaction();
        tx.setWarehouseId(row.getWarehouseId());
        tx.setSkuId(row.getSkuId());
        tx.setBatchNo(row.getBatchNo());
        tx.setBizType(BIZ_DEDUCT);
        tx.setBizNo(bizNo);
        tx.setChangeQuantity(-take);
        tx.setBeforeQuantity(beforeReserved);
        tx.setAfterQuantity(beforeReserved - take);
        tx.setOperatorId(operatorId);
        tx.setRemark("支付成功扣减");
        transactionMapper.insert(tx);
    }

    private InventoryResponse toResponse(Inventory row, Sku sku) {
        return new InventoryResponse(
                row.getId(),
                row.getWarehouseId(),
                row.getSkuId(),
                sku == null ? null : sku.getSkuNo(),
                row.getBatchNo(),
                row.getQuantity(),
                row.getReservedQuantity(),
                row.getFrozenQuantity(),
                row.getExpireAt());
    }
}
