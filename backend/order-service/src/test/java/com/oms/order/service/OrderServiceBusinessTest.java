package com.oms.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.ErrorCode;
import com.oms.common.core.result.Result;
import com.oms.order.client.InventoryClient;
import com.oms.order.client.PaymentClient;
import com.oms.order.client.SkuInfo;
import com.oms.order.constant.OrderStatus;
import com.oms.order.dto.OrderDtos.CancelRequest;
import com.oms.order.dto.OrderDtos.CreateOrderRequest;
import com.oms.order.dto.OrderDtos.OrderItemRequest;
import com.oms.order.dto.OrderDtos.PaymentSuccessRequest;
import com.oms.order.dto.OpenOrderDtos.OpenOrderResponse;
import com.oms.order.dto.OpenOrderDtos.OpenPaymentNotifyRequest;
import com.oms.order.entity.Order;
import com.oms.order.entity.OrderItem;
import com.oms.order.entity.OrderLog;
import com.oms.order.entity.OrderPayment;
import com.oms.order.mapper.OrderItemMapper;
import com.oms.order.mapper.OrderLogMapper;
import com.oms.order.mapper.OrderMapper;
import com.oms.order.mapper.OrderPaymentMapper;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OrderServiceBusinessTest {

    private OrderMapper orderMapper;
    private OrderItemMapper orderItemMapper;
    private OrderPaymentMapper orderPaymentMapper;
    private OrderLogMapper orderLogMapper;
    private InventoryClient inventoryClient;
    private PaymentClient paymentClient;
    private OrderArchiveService orderArchiveService;
    private OrderService orderService;

    @BeforeEach
    void setUp() throws Exception {
        orderMapper = org.mockito.Mockito.mock(OrderMapper.class);
        orderItemMapper = org.mockito.Mockito.mock(OrderItemMapper.class);
        orderPaymentMapper = org.mockito.Mockito.mock(OrderPaymentMapper.class);
        orderLogMapper = org.mockito.Mockito.mock(OrderLogMapper.class);
        inventoryClient = org.mockito.Mockito.mock(InventoryClient.class);
        paymentClient = org.mockito.Mockito.mock(PaymentClient.class);
        orderArchiveService = org.mockito.Mockito.mock(OrderArchiveService.class);
        orderService = new OrderService(
                orderMapper,
                orderItemMapper,
                orderPaymentMapper,
                orderLogMapper,
                inventoryClient,
                paymentClient,
                orderArchiveService);
        setField(orderService, "timeoutMinutes", 30L);
    }

    // ---------- 创建订单 ----------

    @Test
    void createShouldRejectEmptyItems() {
        assertThatThrownBy(() -> orderService.create(
                        new CreateOrderRequest(1L, 1, null, List.of()), 1L, "tester"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("订单明细不能为空");
        verifyNoInteractions(inventoryClient);
        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    void createShouldRejectUnsalableSku() {
        SkuInfo sku = new SkuInfo(1L, "SKU001", "下架商品", new BigDecimal("10.00"), new BigDecimal("5.00"), 0);
        when(inventoryClient.getSku(1L)).thenReturn(Result.ok(sku));

        assertThatThrownBy(() -> orderService.create(
                        new CreateOrderRequest(1L, 1, null, List.of(new OrderItemRequest(1L, 2))), 1L, "tester"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不可售");
        verify(inventoryClient, never()).reserve(any());
        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    void createShouldRejectMissingSku() {
        when(inventoryClient.getSku(1L)).thenReturn(Result.ok(null));

        assertThatThrownBy(() -> orderService.create(
                        new CreateOrderRequest(1L, 1, null, List.of(new OrderItemRequest(1L, 2))), 1L, "tester"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不可售");
        verify(inventoryClient, never()).reserve(any());
    }

    @Test
    void createShouldComputeAmountsAndSetPendingPaymentWithTimeout() {
        SkuInfo sku1 = new SkuInfo(1L, "SKU001", "商品A", new BigDecimal("100.00"), new BigDecimal("60.00"), 1);
        SkuInfo sku2 = new SkuInfo(2L, "SKU002", "商品B", new BigDecimal("50.00"), new BigDecimal("30.00"), 1);
        when(inventoryClient.getSku(1L)).thenReturn(Result.ok(sku1));
        when(inventoryClient.getSku(2L)).thenReturn(Result.ok(sku2));
        when(inventoryClient.reserve(any())).thenReturn(Result.ok());
        when(orderMapper.insert(any(Order.class))).thenAnswer(invocation -> {
            ((Order) invocation.getArgument(0)).setId(1L);
            return 1;
        });
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order(OrderStatus.PENDING_PAYMENT));
        when(orderItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(orderLogMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        orderService.create(
                new CreateOrderRequest(
                        1L, 1, "备注", List.of(new OrderItemRequest(1L, 2), new OrderItemRequest(2L, 3))),
                1L,
                "tester");

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).insert(orderCaptor.capture());
        Order saved = orderCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("350.00");
        assertThat(saved.getPayAmount()).isEqualByComparingTo("350.00");
        assertThat(saved.getDiscountAmount()).isEqualByComparingTo("0");
        assertThat(saved.getCurrency()).isEqualTo("CNY");
        assertThat(saved.getTimeoutAt())
                .isNotNull()
                .isAfter(LocalDateTime.now().minusMinutes(1))
                .isBefore(LocalDateTime.now().plusMinutes(31));

        ArgumentCaptor<OrderItem> itemCaptor = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemMapper, times(2)).insert(itemCaptor.capture());
        OrderItem first = itemCaptor.getAllValues().get(0);
        assertThat(first.getSkuId()).isEqualTo(1L);
        assertThat(first.getQuantity()).isEqualTo(2);
        assertThat(first.getUnitPrice()).isEqualByComparingTo("100.00");
        assertThat(first.getTotalPrice()).isEqualByComparingTo("200.00");
        assertThat(first.getCostAmount()).isEqualByComparingTo("120.00");
    }

    @Test
    void createShouldNotReleaseWhenRemoteReserveFails() {
        SkuInfo sku = new SkuInfo(1L, "SKU001", "商品A", new BigDecimal("100.00"), new BigDecimal("60.00"), 1);
        when(inventoryClient.getSku(1L)).thenReturn(Result.ok(sku));
        when(inventoryClient.reserve(any())).thenReturn(Result.fail(ErrorCode.CONFLICT.getCode(), "库存不足"));

        assertThatThrownBy(() -> orderService.create(
                        new CreateOrderRequest(1L, 1, null, List.of(new OrderItemRequest(1L, 2))), 1L, "tester"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("库存预占失败");

        verify(inventoryClient, never()).release(any());
        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    void createShouldAttemptReleaseWhenReserveCallThrows() {
        SkuInfo sku = new SkuInfo(1L, "SKU001", "商品A", new BigDecimal("100.00"), new BigDecimal("60.00"), 1);
        when(inventoryClient.getSku(1L)).thenReturn(Result.ok(sku));
        when(inventoryClient.reserve(any())).thenThrow(new RuntimeException("net down"));

        assertThatThrownBy(() -> orderService.create(
                        new CreateOrderRequest(1L, 1, null, List.of(new OrderItemRequest(1L, 2))), 1L, "tester"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("net down");

        // 预占结果未知：必须尝试释放，避免库存泄漏
        ArgumentCaptor<InventoryClient.StockRequest> releaseCaptor =
                ArgumentCaptor.forClass(InventoryClient.StockRequest.class);
        verify(inventoryClient).release(releaseCaptor.capture());
        assertThat(releaseCaptor.getValue().items()).containsExactly(new OrderItemRequest(1L, 2));
        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    void createShouldReleaseInventoryWhenPersistFails() {
        SkuInfo sku = new SkuInfo(1L, "SKU001", "商品A", new BigDecimal("100.00"), new BigDecimal("60.00"), 1);
        when(inventoryClient.getSku(1L)).thenReturn(Result.ok(sku));
        when(inventoryClient.reserve(any())).thenReturn(Result.ok());
        when(orderMapper.insert(any(Order.class))).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> orderService.create(
                        new CreateOrderRequest(1L, 1, null, List.of(new OrderItemRequest(1L, 2))), 1L, "tester"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("db down");

        ArgumentCaptor<InventoryClient.StockRequest> releaseCaptor =
                ArgumentCaptor.forClass(InventoryClient.StockRequest.class);
        verify(inventoryClient).release(releaseCaptor.capture());
        assertThat(releaseCaptor.getValue().orderNo()).startsWith("O");
        assertThat(releaseCaptor.getValue().items()).containsExactly(new OrderItemRequest(1L, 2));
    }

    // ---------- 取消订单 ----------

    @Test
    void cancelPendingPaymentShouldReleaseInventory() {
        Order order = order(OrderStatus.PENDING_PAYMENT);
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order);
        when(orderItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(orderItem(1L, 2)));
        when(inventoryClient.release(any())).thenReturn(Result.ok());
        when(orderMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        orderService.cancel("O001", new CancelRequest("不想要了"), 1L, "tester", 0);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        ArgumentCaptor<InventoryClient.StockRequest> releaseCaptor =
                ArgumentCaptor.forClass(InventoryClient.StockRequest.class);
        verify(inventoryClient).release(releaseCaptor.capture());
        assertThat(releaseCaptor.getValue().orderNo()).isEqualTo("O001");
        assertThat(releaseCaptor.getValue().items()).containsExactly(new OrderItemRequest(1L, 2));
        verify(inventoryClient, never()).restore(any());
        verify(orderLogMapper).insert(any(OrderLog.class));
    }

    @Test
    void cancelPaidByAdminShouldRestoreInventory() {
        Order order = order(OrderStatus.PAID);
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order);
        when(orderItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(orderItem(1L, 2)));
        when(inventoryClient.restore(any())).thenReturn(Result.ok());
        when(orderMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        orderService.cancel("O001", new CancelRequest("管理员取消"), 1L, "admin", 1);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventoryClient).restore(any());
        verify(inventoryClient, never()).release(any());
    }

    @Test
    void cancelPaidByUserShouldThrow() {
        Order order = order(OrderStatus.PAID);
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order);
        when(orderItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(orderItem(1L, 2)));

        assertThatThrownBy(() -> orderService.cancel("O001", new CancelRequest("x"), 1L, "tester", 0))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不允许取消");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(inventoryClient, never()).release(any());
        verify(inventoryClient, never()).restore(any());
    }

    @Test
    void cancelInOtherStatesShouldThrow() {
        Order completed = order(OrderStatus.COMPLETED);
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(completed);
        assertThatThrownBy(() -> orderService.cancel("O001", new CancelRequest("x"), 1L, "admin", 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不允许取消");

        Order audited = order(OrderStatus.AUDITED);
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(audited);
        assertThatThrownBy(() -> orderService.cancel("O001", new CancelRequest("x"), 1L, "admin", 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不允许取消");
    }

    // ---------- 支付成功回调 ----------

    @Test
    void paymentSuccessShouldTransitionStatusAndUpdatePayment() {
        Order order = order(OrderStatus.PENDING_PAYMENT);
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order);
        OrderPayment payment = new OrderPayment();
        payment.setId(1L);
        payment.setPaymentNo("P001");
        payment.setStatus(1);
        when(orderPaymentMapper.selectOne(any(Wrapper.class))).thenReturn(payment);
        when(orderItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(orderItem(1L, 2)));
        when(inventoryClient.deduct(any())).thenReturn(Result.ok());
        when(orderMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        orderService.handlePaymentSuccess(
                new PaymentSuccessRequest("O001", "P001", "mock", new BigDecimal("100.00"), "TXN1"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaidAt()).isNotNull();
        assertThat(payment.getStatus()).isEqualTo(2);
        assertThat(payment.getChannelTxnNo()).isEqualTo("TXN1");
        verify(orderMapper).update(isNull(), any(Wrapper.class));
        verify(orderLogMapper).insert(any(OrderLog.class));
        verify(orderPaymentMapper).updateById(payment);
        verify(inventoryClient).deduct(any());
    }

    @Test
    void paymentSuccessDuplicateShouldNotRepeatTransitions() {
        Order order = order(OrderStatus.PENDING_PAYMENT);
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order);
        OrderPayment payment = new OrderPayment();
        payment.setId(1L);
        payment.setPaymentNo("P001");
        payment.setStatus(1);
        when(orderPaymentMapper.selectOne(any(Wrapper.class))).thenReturn(payment);
        when(orderItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(orderItem(1L, 2)));
        when(inventoryClient.deduct(any())).thenReturn(Result.ok());
        when(orderMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        orderService.handlePaymentSuccess(
                new PaymentSuccessRequest("O001", "P001", "mock", new BigDecimal("100.00"), "TXN1"));
        orderService.handlePaymentSuccess(
                new PaymentSuccessRequest("O001", "P001", "mock", new BigDecimal("100.00"), "TXN1"));

        verify(orderMapper, times(1)).update(isNull(), any(Wrapper.class));
        verify(orderLogMapper, times(1)).insert(any(OrderLog.class));
        verify(orderPaymentMapper, times(1)).updateById(payment);
        verify(inventoryClient, times(1)).deduct(any());
    }

    @Test
    void paymentSuccessShouldIgnoreOrderNotPendingPayment() {
        Order order = order(OrderStatus.PAID);
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order);

        orderService.handlePaymentSuccess(
                new PaymentSuccessRequest("O001", "P001", "mock", new BigDecimal("100.00"), "TXN1"));

        verify(orderMapper, never()).update(isNull(), any(Wrapper.class));
        verify(orderLogMapper, never()).insert(any(OrderLog.class));
        verify(inventoryClient, never()).deduct(any());
    }

    @Test
    void paymentSuccessWithoutPaymentRecordShouldStillDeduct() {
        Order order = order(OrderStatus.PENDING_PAYMENT);
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order);
        when(orderPaymentMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(orderItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(orderItem(1L, 2)));
        when(inventoryClient.deduct(any())).thenReturn(Result.ok());
        when(orderMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        orderService.handlePaymentSuccess(
                new PaymentSuccessRequest("O001", "P001", "mock", new BigDecimal("100.00"), "TXN1"));

        verify(orderPaymentMapper, never()).updateById(any(OrderPayment.class));
        verify(inventoryClient).deduct(any());
    }

    @Test
    void paymentSuccessShouldSwallowDeductFailure() {
        Order order = order(OrderStatus.PENDING_PAYMENT);
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order);
        when(orderPaymentMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(orderItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(orderItem(1L, 2)));
        when(inventoryClient.deduct(any())).thenThrow(new RuntimeException("net down"));
        when(orderMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        assertThatCode(() -> orderService.handlePaymentSuccess(
                        new PaymentSuccessRequest("O001", "P001", "mock", new BigDecimal("100.00"), "TXN1")))
                .doesNotThrowAnyException();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void paymentSuccessShouldIgnoreAmountMismatch() {
        Order order = order(OrderStatus.PENDING_PAYMENT);
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order);
        when(orderItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(orderItem(1L, 2)));

        orderService.handlePaymentSuccess(
                new PaymentSuccessRequest("O001", "P001", "mock", new BigDecimal("99.00"), "TXN1"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        verify(orderMapper, never()).update(isNull(), any(Wrapper.class));
        verify(orderLogMapper, never()).insert(any(OrderLog.class));
        verify(inventoryClient, never()).deduct(any());
    }

    // ---------- 超时取消 ----------

    @Test
    void timeoutCancelShouldCancelExpiredAndRelease() {
        Order expired = order(OrderStatus.PENDING_PAYMENT);
        expired.setTimeoutAt(LocalDateTime.now().minusMinutes(5));
        when(orderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(expired));
        when(orderItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(orderItem(1L, 2)));
        when(inventoryClient.release(any())).thenReturn(Result.ok());
        when(orderMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        orderService.timeoutCancelPendingOrders();

        assertThat(expired.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderMapper).update(isNull(), any(Wrapper.class));
        ArgumentCaptor<InventoryClient.StockRequest> releaseCaptor =
                ArgumentCaptor.forClass(InventoryClient.StockRequest.class);
        verify(inventoryClient).release(releaseCaptor.capture());
        assertThat(releaseCaptor.getValue().orderNo()).isEqualTo("O001");
        assertThat(releaseCaptor.getValue().items()).containsExactly(new OrderItemRequest(1L, 2));
        verify(orderLogMapper).insert(any(OrderLog.class));
    }

    @Test
    void timeoutCancelShouldSkipOrdersThatCannotTransit() {
        Order completed = order(OrderStatus.COMPLETED);
        completed.setTimeoutAt(LocalDateTime.now().minusMinutes(5));
        when(orderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(completed));
        when(orderItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(orderItem(1L, 2)));

        assertThatCode(orderService::timeoutCancelPendingOrders).doesNotThrowAnyException();

        verify(inventoryClient, never()).release(any());
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    @Test
    void timeoutCancelShouldDoNothingWhenNoExpiredOrders() {
        when(orderMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        orderService.timeoutCancelPendingOrders();

        verify(inventoryClient, never()).release(any());
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    // ---------- 商城支付成功通知 ----------

    @Test
    void notifyPaymentSuccessShouldTransitionRecordAndDeduct() {
        Order order = order(OrderStatus.PENDING_PAYMENT);
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order);
        when(orderMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(orderItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(orderItem(1L, 2)));
        when(inventoryClient.deduct(any())).thenReturn(Result.ok());

        OpenOrderResponse response = orderService.notifyPaymentSuccess(
                "M1", new OpenPaymentNotifyRequest("MP001", new BigDecimal("100.00"), "wechat", "TXN1", null), 1L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaidAt()).isNotNull();
        assertThat(response.externalOrderNo()).isNull();
        ArgumentCaptor<OrderPayment> paymentCaptor = ArgumentCaptor.forClass(OrderPayment.class);
        verify(orderPaymentMapper).insert(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getPaymentNo()).isEqualTo("MP001");
        assertThat(paymentCaptor.getValue().getChannel()).isEqualTo("wechat");
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(2);
        verify(orderLogMapper).insert(any(OrderLog.class));
        verify(inventoryClient).deduct(any());
    }

    @Test
    void notifyPaymentSuccessShouldBeIdempotentOnRepeat() {
        Order order = order(OrderStatus.PENDING_PAYMENT);
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order);
        when(orderMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(orderItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(orderItem(1L, 2)));
        when(inventoryClient.deduct(any())).thenReturn(Result.ok());

        orderService.notifyPaymentSuccess(
                "M1", new OpenPaymentNotifyRequest("MP001", new BigDecimal("100.00"), null, null, null), 1L);
        orderService.notifyPaymentSuccess(
                "M1", new OpenPaymentNotifyRequest("MP001", new BigDecimal("100.00"), null, null, null), 1L);

        verify(orderMapper, times(1)).update(isNull(), any(Wrapper.class));
        verify(orderLogMapper, times(1)).insert(any(OrderLog.class));
        verify(inventoryClient, times(1)).deduct(any());
    }

    @Test
    void notifyPaymentSuccessShouldRejectAmountMismatch() {
        Order order = order(OrderStatus.PENDING_PAYMENT);
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order);

        assertThatThrownBy(() -> orderService.notifyPaymentSuccess(
                        "M1", new OpenPaymentNotifyRequest("MP001", new BigDecimal("99.00"), null, null, null), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("金额");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        verify(orderMapper, never()).update(isNull(), any(Wrapper.class));
        verify(inventoryClient, never()).deduct(any());
    }

    @Test
    void notifyPaymentSuccessShouldRejectCancelledOrder() {
        Order order = order(OrderStatus.CANCELLED);
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order);

        assertThatThrownBy(() -> orderService.notifyPaymentSuccess(
                        "M1", new OpenPaymentNotifyRequest("MP001", new BigDecimal("100.00"), null, null, null), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已取消");
        verify(inventoryClient, never()).deduct(any());
    }

    @Test
    void notifyPaymentSuccessShouldRejectUnknownOrder() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> orderService.notifyPaymentSuccess(
                        "M1", new OpenPaymentNotifyRequest("MP001", new BigDecimal("100.00"), null, null, null), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("外部订单不存在");
    }

    @Test
    void notifyPaymentSuccessShouldRequirePaymentNo() {
        assertThatThrownBy(() -> orderService.notifyPaymentSuccess(
                        "M1", new OpenPaymentNotifyRequest("", new BigDecimal("100.00"), null, null, null), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("paymentNo");
    }

    @Test
    void notifyPaymentSuccessShouldRejectOtherMerchant() {
        Order order = order(OrderStatus.PENDING_PAYMENT);
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order);

        assertThatThrownBy(() -> orderService.notifyPaymentSuccess(
                        "M1", new OpenPaymentNotifyRequest("MP001", new BigDecimal("100.00"), null, null, null), 2L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.FORBIDDEN.getCode());
                    assertThat(ex.getMessage()).isEqualTo("无权限访问");
                });
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
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

    private OrderItem orderItem(Long skuId, int quantity) {
        OrderItem item = new OrderItem();
        item.setId(skuId);
        item.setOrderId(1L);
        item.setSkuId(skuId);
        item.setSkuName("商品" + skuId);
        item.setQuantity(quantity);
        item.setUnitPrice(new BigDecimal("100.00"));
        item.setTotalPrice(new BigDecimal("100.00").multiply(BigDecimal.valueOf(quantity)));
        return item;
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
