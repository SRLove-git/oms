package com.oms.order.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.oms.common.core.result.Result;
import com.oms.order.client.InventoryClient;
import com.oms.order.client.PaymentClient;
import com.oms.order.client.SkuInfo;
import com.oms.order.constant.OrderStatus;
import com.oms.order.dto.OrderDtos.CreateOrderRequest;
import com.oms.order.dto.OrderDtos.OrderItemRequest;
import com.oms.order.dto.OrderDtos.PaymentSuccessRequest;
import com.oms.order.entity.Order;
import com.oms.order.entity.OrderItem;
import com.oms.order.mapper.OrderItemMapper;
import com.oms.order.mapper.OrderLogMapper;
import com.oms.order.mapper.OrderMapper;
import com.oms.order.mapper.OrderPaymentMapper;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderServiceTest {

    private OrderMapper orderMapper;
    private OrderItemMapper orderItemMapper;
    private OrderPaymentMapper orderPaymentMapper;
    private OrderLogMapper orderLogMapper;
    private InventoryClient inventoryClient;
    private PaymentClient paymentClient;
    private OrderService orderService;

    @BeforeEach
    void setUp() throws Exception {
        orderMapper = mock(OrderMapper.class);
        orderItemMapper = mock(OrderItemMapper.class);
        orderPaymentMapper = mock(OrderPaymentMapper.class);
        orderLogMapper = mock(OrderLogMapper.class);
        inventoryClient = mock(InventoryClient.class);
        paymentClient = mock(PaymentClient.class);
        orderService = new OrderService(
                orderMapper, orderItemMapper, orderPaymentMapper, orderLogMapper, inventoryClient, paymentClient);
        setField(orderService, "timeoutMinutes", 30L);
    }

    @Test
    void createShouldReserveThenPersistOrder() {
        SkuInfo sku = new SkuInfo(1L, "SKU001", "测试商品", new BigDecimal("100.00"), 1);
        when(inventoryClient.getSku(1L)).thenReturn(Result.ok(sku));
        when(inventoryClient.reserve(any())).thenReturn(Result.ok());
        when(orderMapper.insert(any(Order.class))).thenAnswer(invocation -> {
            ((Order) invocation.getArgument(0)).setId(1L);
            return 1;
        });
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order(OrderStatus.PENDING_PAYMENT));
        when(orderItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(orderLogMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        orderService.create(
                new CreateOrderRequest(1L, 1, null, List.of(new OrderItemRequest(1L, 2))), 1L, "tester");

        verify(inventoryClient).reserve(any());
        verify(orderMapper).insert(any(Order.class));
        verify(orderItemMapper).insert(any(OrderItem.class));
    }

    @Test
    void paymentSuccessShouldDeductOnlyOnce() {
        Order order = order(OrderStatus.PENDING_PAYMENT);
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order);
        when(inventoryClient.deduct(any())).thenReturn(Result.ok());

        orderService.handlePaymentSuccess(
                new PaymentSuccessRequest("O001", "P001", "mock", new BigDecimal("100.00"), "TXN1"));
        orderService.handlePaymentSuccess(
                new PaymentSuccessRequest("O001", "P001", "mock", new BigDecimal("100.00"), "TXN1"));

        verify(inventoryClient, times(1)).deduct(any());
    }

    private Order order(int status) {
        Order order = new Order();
        order.setId(1L);
        order.setOrderNo("O001");
        order.setMerchantId(1L);
        order.setStatus(status);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setPayAmount(new BigDecimal("100.00"));
        order.setCurrency("CNY");
        return order;
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
