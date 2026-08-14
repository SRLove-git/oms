package com.oms.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oms.common.core.exception.BusinessException;
import com.oms.inventory.dto.InventoryDtos.InboundRequest;
import com.oms.inventory.dto.InventoryDtos.ReserveItem;
import com.oms.inventory.dto.InventoryDtos.ReserveRequest;
import com.oms.inventory.entity.Inventory;
import com.oms.inventory.entity.InventoryTransaction;
import com.oms.inventory.mapper.InventoryMapper;
import com.oms.inventory.mapper.InventoryTransactionMapper;
import com.oms.inventory.mapper.SkuMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class InventoryServiceTest {

    private InventoryMapper inventoryMapper;
    private InventoryTransactionMapper transactionMapper;
    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryMapper = mock(InventoryMapper.class);
        transactionMapper = mock(InventoryTransactionMapper.class);
        inventoryService = new InventoryService(inventoryMapper, transactionMapper, mock(SkuMapper.class));
    }

    // ---------- 入库 ----------

    @Test
    void inboundShouldCreateRowWhenMissing() {
        when(inventoryMapper.selectOne(any())).thenReturn(null);
        when(inventoryMapper.insert(any(Inventory.class))).thenAnswer(invocation -> {
            ((Inventory) invocation.getArgument(0)).setId(10L);
            return 1;
        });
        when(inventoryMapper.update(any(), any())).thenReturn(1);

        inventoryService.inbound(new InboundRequest(1L, 1L, 5, "B1", LocalDate.of(2026, 1, 1), "首次入库"), 9L);

        ArgumentCaptor<Inventory> createdCaptor = ArgumentCaptor.forClass(Inventory.class);
        verify(inventoryMapper).insert(createdCaptor.capture());
        Inventory created = createdCaptor.getValue();
        assertThat(created.getWarehouseId()).isEqualTo(1L);
        assertThat(created.getSkuId()).isEqualTo(1L);
        assertThat(created.getBatchNo()).isEqualTo("B1");
        assertThat(created.getQuantity()).isZero();
        assertThat(created.getReservedQuantity()).isZero();

        ArgumentCaptor<InventoryTransaction> txCaptor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(transactionMapper).insert(txCaptor.capture());
        InventoryTransaction tx = txCaptor.getValue();
        assertThat(tx.getChangeQuantity()).isEqualTo(5);
        assertThat(tx.getBeforeQuantity()).isZero();
        assertThat(tx.getAfterQuantity()).isEqualTo(5);
        assertThat(tx.getBizType()).isEqualTo(5);
        assertThat(tx.getBizNo()).isEqualTo("INBOUND");
        assertThat(tx.getOperatorId()).isEqualTo(9L);
        assertThat(tx.getBatchNo()).isEqualTo("B1");
    }

    @Test
    void inboundShouldAccumulateQuantityAndRecordTransaction() {
        Inventory existing = row(1L, 10, 0);
        existing.setBatchNo("B1");
        when(inventoryMapper.selectOne(any())).thenReturn(existing);
        when(inventoryMapper.update(any(), any())).thenReturn(1);

        inventoryService.inbound(new InboundRequest(1L, 1L, 5, "B1", null, "补货"), 9L);

        verify(inventoryMapper).update(isNull(), any());
        ArgumentCaptor<InventoryTransaction> txCaptor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(transactionMapper).insert(txCaptor.capture());
        assertThat(txCaptor.getValue().getChangeQuantity()).isEqualTo(5);
        assertThat(txCaptor.getValue().getBeforeQuantity()).isEqualTo(10);
        assertThat(txCaptor.getValue().getAfterQuantity()).isEqualTo(15);
        assertThat(txCaptor.getValue().getBizNo()).isEqualTo("INBOUND");
    }

    @Test
    void inboundShouldThrowWhenUpdateFails() {
        when(inventoryMapper.selectOne(any())).thenReturn(row(1L, 10, 0));
        when(inventoryMapper.update(any(), any())).thenReturn(0);

        assertThatThrownBy(() ->
                        inventoryService.inbound(new InboundRequest(1L, 1L, 5, "B1", null, "补货"), 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("库存更新失败");
        verify(transactionMapper, never()).insert(any(InventoryTransaction.class));
    }

    // ---------- 预占 ----------

    @Test
    void reserveShouldConsumeEarliestExpiryFirstAcrossBatches() {
        Inventory b1 = row(1L, 3, 0);
        b1.setBatchNo("B1");
        b1.setExpireAt(LocalDate.of(2025, 1, 1));
        Inventory b2 = row(2L, 5, 0);
        b2.setBatchNo("B2");
        b2.setExpireAt(LocalDate.of(2026, 1, 1));
        // selectList 的排序（效期升序）由 SQL 保证，mock 按效期先后返回
        when(inventoryMapper.selectList(any())).thenReturn(List.of(b1, b2));
        when(inventoryMapper.update(any(), any())).thenReturn(1);

        inventoryService.reserve(new ReserveRequest("ORD001", List.of(new ReserveItem(1L, 6))), 7L);

        verify(inventoryMapper, times(2)).update(isNull(), any());

        ArgumentCaptor<InventoryTransaction> txCaptor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(transactionMapper, times(2)).insert(txCaptor.capture());
        List<InventoryTransaction> txs = txCaptor.getAllValues();
        // 先扣最早的批次 B1（3 件），再跨批次凑数扣 B2（3 件）
        assertThat(txs.get(0).getBatchNo()).isEqualTo("B1");
        assertThat(txs.get(0).getChangeQuantity()).isEqualTo(-3);
        assertThat(txs.get(1).getBatchNo()).isEqualTo("B2");
        assertThat(txs.get(1).getChangeQuantity()).isEqualTo(-3);
        assertThat(txs).allSatisfy(tx -> {
            assertThat(tx.getBizType()).isEqualTo(1);
            assertThat(tx.getBizNo()).isEqualTo("ORD001");
            assertThat(tx.getOperatorId()).isEqualTo(7L);
        });
    }

    @Test
    void reserveShouldThrowWhenTotalInsufficientAcrossBatches() {
        Inventory b1 = row(1L, 3, 0);
        b1.setBatchNo("B1");
        Inventory b2 = row(2L, 2, 0);
        b2.setBatchNo("B2");
        when(inventoryMapper.selectList(any())).thenReturn(List.of(b1, b2));
        when(inventoryMapper.update(any(), any())).thenReturn(1);

        assertThatThrownBy(() ->
                        inventoryService.reserve(new ReserveRequest("ORD001", List.of(new ReserveItem(1L, 10))), 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("库存不足");
        verify(inventoryMapper, times(2)).update(isNull(), any());
    }

    @Test
    void reserveShouldSkipRowWhenUpdateFails() {
        Inventory b1 = row(1L, 5, 0);
        b1.setBatchNo("B1");
        Inventory b2 = row(2L, 5, 0);
        b2.setBatchNo("B2");
        when(inventoryMapper.selectList(any())).thenReturn(List.of(b1, b2));
        when(inventoryMapper.update(any(), any())).thenReturn(0, 1);

        inventoryService.reserve(new ReserveRequest("ORD001", List.of(new ReserveItem(1L, 4))), 7L);

        ArgumentCaptor<InventoryTransaction> txCaptor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(transactionMapper).insert(txCaptor.capture());
        assertThat(txCaptor.getValue().getBatchNo()).isEqualTo("B2");
        assertThat(txCaptor.getValue().getChangeQuantity()).isEqualTo(-4);
    }

    // ---------- 释放 ----------

    @Test
    void releaseShouldReturnReservedStock() {
        Inventory row = row(1L, 0, 5);
        when(inventoryMapper.selectList(any())).thenReturn(List.of(row));
        when(inventoryMapper.update(any(), any())).thenReturn(1);

        inventoryService.release(new ReserveRequest("ORD001", List.of(new ReserveItem(1L, 2))), 1L);

        verify(transactionMapper, atLeastOnce()).insert(any(InventoryTransaction.class));
    }

    @Test
    void releaseShouldRecordPositiveChangeOnAvailableQuantity() {
        Inventory row = row(1L, 0, 5);
        row.setBatchNo("B1");
        when(inventoryMapper.selectList(any())).thenReturn(List.of(row));
        when(inventoryMapper.update(any(), any())).thenReturn(1);

        inventoryService.release(new ReserveRequest("ORD001", List.of(new ReserveItem(1L, 2))), 7L);

        verify(inventoryMapper).update(isNull(), any());
        ArgumentCaptor<InventoryTransaction> txCaptor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(transactionMapper).insert(txCaptor.capture());
        InventoryTransaction tx = txCaptor.getValue();
        assertThat(tx.getChangeQuantity()).isEqualTo(2);
        assertThat(tx.getBeforeQuantity()).isZero();
        assertThat(tx.getAfterQuantity()).isEqualTo(2);
        assertThat(tx.getBizType()).isEqualTo(2);
        assertThat(tx.getBizNo()).isEqualTo("ORD001");
        assertThat(tx.getRemark()).isEqualTo("取消/超时释放");
    }

    @Test
    void releaseShouldThrowWhenReservedInsufficient() {
        when(inventoryMapper.selectList(any())).thenReturn(List.of(row(1L, 0, 3)));
        when(inventoryMapper.update(any(), any())).thenReturn(1);

        assertThatThrownBy(() ->
                        inventoryService.release(new ReserveRequest("ORD001", List.of(new ReserveItem(1L, 5))), 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("预占数量不足");
    }

    // ---------- 扣减 ----------

    @Test
    void deductShouldRecordNegativeChangeOnReservedBaseline() {
        Inventory row = row(1L, 0, 5);
        row.setBatchNo("B1");
        when(inventoryMapper.selectList(any())).thenReturn(List.of(row));
        when(inventoryMapper.update(any(), any())).thenReturn(1);

        inventoryService.deduct(new ReserveRequest("ORD001", List.of(new ReserveItem(1L, 2))), 7L);

        verify(inventoryMapper).update(isNull(), any());
        ArgumentCaptor<InventoryTransaction> txCaptor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(transactionMapper).insert(txCaptor.capture());
        InventoryTransaction tx = txCaptor.getValue();
        assertThat(tx.getChangeQuantity()).isEqualTo(-2);
        assertThat(tx.getBeforeQuantity()).isEqualTo(5);
        assertThat(tx.getAfterQuantity()).isEqualTo(3);
        assertThat(tx.getBizType()).isEqualTo(3);
        assertThat(tx.getBizNo()).isEqualTo("ORD001");
        assertThat(tx.getRemark()).isEqualTo("支付成功扣减");
    }

    @Test
    void deductShouldThrowWhenReservedInsufficient() {
        when(inventoryMapper.selectList(any())).thenReturn(List.of(row(1L, 0, 3)));
        when(inventoryMapper.update(any(), any())).thenReturn(1);

        assertThatThrownBy(() ->
                        inventoryService.deduct(new ReserveRequest("ORD001", List.of(new ReserveItem(1L, 5))), 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("预占数量不足");

        // 部分预占先被扣减并记账，随后整体抛错（事务内回滚由 @Transactional 负责）
        ArgumentCaptor<InventoryTransaction> txCaptor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(transactionMapper).insert(txCaptor.capture());
        assertThat(txCaptor.getValue().getChangeQuantity()).isEqualTo(-3);
        assertThat(txCaptor.getValue().getBeforeQuantity()).isEqualTo(3);
        assertThat(txCaptor.getValue().getAfterQuantity()).isZero();
    }

    // ---------- 回补 ----------

    @Test
    void restoreShouldAddBackToFirstRowOnly() {
        Inventory r1 = row(1L, 0, 0);
        r1.setBatchNo("B1");
        Inventory r2 = row(2L, 0, 0);
        r2.setBatchNo("B2");
        when(inventoryMapper.selectList(any())).thenReturn(List.of(r1, r2));
        when(inventoryMapper.update(any(), any())).thenReturn(1);

        inventoryService.restore(new ReserveRequest("ORD001", List.of(new ReserveItem(1L, 4))), 7L);

        verify(inventoryMapper, times(1)).update(isNull(), any());
        ArgumentCaptor<InventoryTransaction> txCaptor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(transactionMapper).insert(txCaptor.capture());
        InventoryTransaction tx = txCaptor.getValue();
        assertThat(tx.getBatchNo()).isEqualTo("B1");
        assertThat(tx.getChangeQuantity()).isEqualTo(4);
        assertThat(tx.getBizType()).isEqualTo(4);
        assertThat(tx.getBizNo()).isEqualTo("ORD001");
        assertThat(tx.getRemark()).isEqualTo("退货/取消回补");
    }

    @Test
    void restoreShouldSkipRowWhenUpdateFails() {
        Inventory r1 = row(1L, 0, 0);
        r1.setBatchNo("B1");
        Inventory r2 = row(2L, 0, 0);
        r2.setBatchNo("B2");
        when(inventoryMapper.selectList(any())).thenReturn(List.of(r1, r2));
        when(inventoryMapper.update(any(), any())).thenReturn(0, 1);

        inventoryService.restore(new ReserveRequest("ORD001", List.of(new ReserveItem(1L, 4))), 7L);

        verify(inventoryMapper, times(2)).update(isNull(), any());
        ArgumentCaptor<InventoryTransaction> txCaptor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(transactionMapper).insert(txCaptor.capture());
        assertThat(txCaptor.getValue().getBatchNo()).isEqualTo("B2");
    }

    private Inventory row(Long id, int quantity, int reserved) {
        Inventory row = new Inventory();
        row.setId(id);
        row.setWarehouseId(1L);
        row.setSkuId(1L);
        row.setBatchNo("");
        row.setQuantity(quantity);
        row.setReservedQuantity(reserved);
        return row;
    }
}
