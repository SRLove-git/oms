package com.oms.inventory.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oms.common.core.exception.BusinessException;
import com.oms.inventory.dto.InventoryDtos.ReserveItem;
import com.oms.inventory.dto.InventoryDtos.ReserveRequest;
import com.oms.inventory.entity.Inventory;
import com.oms.inventory.entity.InventoryTransaction;
import com.oms.inventory.mapper.InventoryMapper;
import com.oms.inventory.mapper.InventoryTransactionMapper;
import com.oms.inventory.mapper.SkuMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    @Test
    void reserveShouldConsumeAvailableAndRecordTransaction() {
        when(inventoryMapper.selectList(any())).thenReturn(List.of(row(1L, 10, 0)));
        when(inventoryMapper.update(any(), any())).thenReturn(1);

        inventoryService.reserve(new ReserveRequest("ORD001", List.of(new ReserveItem(1L, 3))), 1L);

        verify(transactionMapper, atLeastOnce()).insert(any(InventoryTransaction.class));
    }

    @Test
    void reserveShouldThrowWhenInsufficient() {
        when(inventoryMapper.selectList(any())).thenReturn(List.of(row(1L, 3, 0)));
        when(inventoryMapper.update(any(), any())).thenReturn(1);

        assertThatThrownBy(() ->
                        inventoryService.reserve(new ReserveRequest("ORD001", List.of(new ReserveItem(1L, 5))), 1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void releaseShouldReturnReservedStock() {
        Inventory row = row(1L, 0, 5);
        when(inventoryMapper.selectList(any())).thenReturn(List.of(row));
        when(inventoryMapper.update(any(), any())).thenReturn(1);

        inventoryService.release(new ReserveRequest("ORD001", List.of(new ReserveItem(1L, 2))), 1L);

        verify(transactionMapper, atLeastOnce()).insert(any(InventoryTransaction.class));
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
