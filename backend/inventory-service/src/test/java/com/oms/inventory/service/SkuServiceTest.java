package com.oms.inventory.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.Result;
import com.oms.inventory.client.OrderClient;
import com.oms.inventory.entity.Sku;
import com.oms.inventory.mapper.InventoryMapper;
import com.oms.inventory.mapper.SkuMapper;
import com.oms.inventory.mapper.SpuMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SkuServiceTest {

    private SkuMapper skuMapper;
    private SpuMapper spuMapper;
    private InventoryMapper inventoryMapper;
    private OrderClient orderClient;
    private SkuService skuService;

    @BeforeEach
    void setUp() {
        skuMapper = mock(SkuMapper.class);
        spuMapper = mock(SpuMapper.class);
        inventoryMapper = mock(InventoryMapper.class);
        orderClient = mock(OrderClient.class);
        skuService = new SkuService(skuMapper, spuMapper, inventoryMapper, orderClient);
    }

    @Test
    void deleteShouldPhysicallyRemoveSkuWhenNoStockAndNoOrders() {
        when(skuMapper.selectById(1L)).thenReturn(sku());
        when(inventoryMapper.countBySkuId(1L)).thenReturn(0L);
        when(orderClient.skuReferences(1L))
                .thenReturn(Result.ok(new OrderClient.SkuReferenceCheck(false, 0, 0)));
        when(skuMapper.deletePhysically(1L)).thenReturn(1);

        skuService.delete(1L);

        verify(skuMapper).deletePhysically(1L);
    }

    @Test
    void deleteShouldRejectWhenStockExists() {
        when(skuMapper.selectById(1L)).thenReturn(sku());
        when(inventoryMapper.countBySkuId(1L)).thenReturn(1L);

        assertThatThrownBy(() -> skuService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仍有库存记录");

        verify(skuMapper, never()).deletePhysically(1L);
    }

    @Test
    void deleteShouldRejectWhenOrderReferencesExist() {
        when(skuMapper.selectById(1L)).thenReturn(sku());
        when(inventoryMapper.countBySkuId(1L)).thenReturn(0L);
        when(orderClient.skuReferences(1L))
                .thenReturn(Result.ok(new OrderClient.SkuReferenceCheck(true, 1, 0)));

        assertThatThrownBy(() -> skuService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("关联订单");

        verify(skuMapper, never()).deletePhysically(1L);
    }

    private Sku sku() {
        Sku sku = new Sku();
        sku.setId(1L);
        sku.setSpuId(1L);
        sku.setSkuNo("SKU001");
        sku.setName("测试商品");
        return sku;
    }
}
