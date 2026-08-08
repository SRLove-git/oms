package com.oms.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.ErrorCode;
import com.oms.common.core.result.PageResult;
import com.oms.order.client.InventoryClient;
import com.oms.order.client.PaymentClient;
import com.oms.order.client.SkuInfo;
import com.oms.order.constant.OrderStatus;
import com.oms.order.dto.OrderDtos.CancelRequest;
import com.oms.order.dto.OrderDtos.CreateOrderRequest;
import com.oms.order.dto.OrderDtos.OrderItemRequest;
import com.oms.order.dto.OrderDtos.OrderItemResponse;
import com.oms.order.dto.OrderDtos.OrderLogResponse;
import com.oms.order.dto.OrderDtos.OrderResponse;
import com.oms.order.dto.OrderDtos.OrderSummaryResponse;
import com.oms.order.dto.OrderDtos.PayRequest;
import com.oms.order.dto.OrderDtos.PaymentSuccessRequest;
import com.oms.order.dto.OrderDtos.ShipRequest;
import com.oms.order.entity.Order;
import com.oms.order.entity.OrderItem;
import com.oms.order.entity.OrderLog;
import com.oms.order.entity.OrderPayment;
import com.oms.order.mapper.OrderItemMapper;
import com.oms.order.mapper.OrderLogMapper;
import com.oms.order.mapper.OrderMapper;
import com.oms.order.mapper.OrderPaymentMapper;
import com.oms.common.core.result.Result;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderPaymentMapper orderPaymentMapper;
    private final OrderLogMapper orderLogMapper;
    private final InventoryClient inventoryClient;
    private final PaymentClient paymentClient;

    @Value("${oms.order.timeout-minutes:30}")
    private long timeoutMinutes;

    public OrderService(
            OrderMapper orderMapper,
            OrderItemMapper orderItemMapper,
            OrderPaymentMapper orderPaymentMapper,
            OrderLogMapper orderLogMapper,
            InventoryClient inventoryClient,
            PaymentClient paymentClient) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.orderPaymentMapper = orderPaymentMapper;
        this.orderLogMapper = orderLogMapper;
        this.inventoryClient = inventoryClient;
        this.paymentClient = paymentClient;
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request, Long operatorId, String operatorName) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "订单明细不能为空");
        }
        List<SkuInfo> skus = new ArrayList<>();
        for (OrderItemRequest item : request.items()) {
            SkuInfo sku = inventoryClient.getSku(item.skuId()).data();
            if (sku == null || sku.status() == null || sku.status() != 1) {
                throw new BusinessException(ErrorCode.CONFLICT.getCode(), "SKU " + item.skuId() + " 不可售");
            }
            skus.add(sku);
        }

        String orderNo = generateOrderNo();
        InventoryClient.StockRequest stockRequest =
                new InventoryClient.StockRequest(orderNo, request.items());
        ensureSuccess(inventoryClient.reserve(stockRequest), "库存预占");

        try {
            return persistOrder(request, skus, orderNo, operatorId, operatorName);
        } catch (RuntimeException ex) {
            try {
                inventoryClient.release(stockRequest);
            } catch (Exception releaseEx) {
                log.error("下单失败回滚库存失败 orderNo={}", orderNo, releaseEx);
            }
            throw ex;
        }
    }

    private OrderResponse persistOrder(
            CreateOrderRequest request,
            List<SkuInfo> skus,
            String orderNo,
            Long operatorId,
            String operatorName) {
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setMerchantId(request.merchantId());
        order.setOrderType(request.orderType() == null ? 1 : request.orderType());
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < request.items().size(); i++) {
            OrderItemRequest item = request.items().get(i);
            BigDecimal unitPrice = skus.get(i).price() == null ? BigDecimal.ZERO : skus.get(i).price();
            total = total.add(unitPrice.multiply(BigDecimal.valueOf(item.quantity())));
        }
        order.setTotalAmount(total);
        order.setPayAmount(total);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setCurrency("CNY");
        order.setRemark(request.remark());
        order.setTimeoutAt(LocalDateTime.now().plusMinutes(timeoutMinutes));
        orderMapper.insert(order);

        for (int i = 0; i < request.items().size(); i++) {
            OrderItemRequest item = request.items().get(i);
            SkuInfo sku = skus.get(i);
            OrderItem orderItem = new OrderItem();
            orderItem.setOrderId(order.getId());
            orderItem.setSkuId(item.skuId());
            orderItem.setSkuName(sku.name());
            orderItem.setQuantity(item.quantity());
            orderItem.setUnitPrice(sku.price());
            orderItem.setTotalPrice(sku.price().multiply(BigDecimal.valueOf(item.quantity())));
            orderItemMapper.insert(orderItem);
        }

        appendLog(order.getId(), null, OrderStatus.PENDING_PAYMENT, operatorId, operatorName, "下单");
        return get(orderNo);
    }

    private void ensureSuccess(Result<?> result, String action) {
        if (result == null) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), action + "失败: 远程服务无响应");
        }
        if (!result.isSuccess()) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), action + "失败: " + result.message());
        }
    }

    public OrderResponse get(String orderNo) {
        Order order = findOrder(orderNo);
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, order.getId())
                .eq(OrderItem::getDeleted, 0)
                .orderByAsc(OrderItem::getId));
        List<OrderLog> logs = orderLogMapper.selectList(new LambdaQueryWrapper<OrderLog>()
                .eq(OrderLog::getOrderId, order.getId())
                .orderByAsc(OrderLog::getId));
        return toResponse(order, items, logs);
    }

    public PageResult<OrderSummaryResponse> page(Long merchantId, Integer status, int page, int size) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getDeleted, 0)
                .orderByDesc(Order::getId);
        if (merchantId != null) {
            wrapper.eq(Order::getMerchantId, merchantId);
        }
        if (status != null) {
            wrapper.eq(Order::getStatus, status);
        }
        Page<Order> result = orderMapper.selectPage(new Page<>(page, size), wrapper);
        List<Order> records = result.getRecords();
        List<Long> orderIds = records.stream().map(Order::getId).toList();
        java.util.Map<Long, Long> itemCounts = orderIds.isEmpty()
                ? java.util.Map.of()
                : orderItemMapper
                        .selectList(new LambdaQueryWrapper<OrderItem>()
                                .in(OrderItem::getOrderId, orderIds)
                                .eq(OrderItem::getDeleted, 0))
                        .stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                OrderItem::getOrderId, java.util.stream.Collectors.summingLong(i -> 1L)));
        return PageResult.of(
                result.getTotal(),
                records.stream()
                        .map(order -> toSummary(order, itemCounts.getOrDefault(order.getId(), 0L).intValue()))
                        .toList());
    }

    @Transactional
    public void cancel(String orderNo, CancelRequest request, Long operatorId, String operatorName, Integer userType) {
        Order order = findOrder(orderNo);
        int from = order.getStatus();
        boolean admin = userType != null && userType == 1;
        if (from == OrderStatus.PENDING_PAYMENT) {
            transit(order, OrderStatus.CANCELLED, operatorId, operatorName, "取消订单");
            inventoryClient.release(itemsAsStockRequest(orderNo, order.getId()));
        } else if (from == OrderStatus.PAID && admin) {
            transit(order, OrderStatus.CANCELLED, operatorId, operatorName, "管理员取消已支付订单");
            inventoryClient.restore(itemsAsStockRequest(orderNo, order.getId()));
        } else {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "当前状态不允许取消");
        }
    }

    @Transactional
    public PaymentClient.CreatePaymentResponse pay(String orderNo, PayRequest request, Long operatorId) {
        Order order = findOrder(orderNo);
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "订单状态不允许支付");
        }
        String channel = request.channel() == null ? "mock" : request.channel();
        PaymentClient.CreatePaymentRequest paymentRequest = new PaymentClient.CreatePaymentRequest(
                orderNo, order.getPayAmount(), order.getCurrency(), channel, order.getMerchantId());
        PaymentClient.CreatePaymentResponse response = paymentClient.create(paymentRequest).data();

        Long exists = orderPaymentMapper.selectCount(new LambdaQueryWrapper<OrderPayment>()
                .eq(OrderPayment::getPaymentNo, response.paymentNo()));
        if (exists == 0) {
            OrderPayment payment = new OrderPayment();
            payment.setOrderId(order.getId());
            payment.setPaymentNo(response.paymentNo());
            payment.setChannel(channel);
            payment.setAmount(order.getPayAmount());
            payment.setCurrency(order.getCurrency());
            payment.setStatus(1);
            orderPaymentMapper.insert(payment);
        }
        return response;
    }

    @Transactional
    public void audit(String orderNo, Long operatorId, String operatorName) {
        Order order = findOrder(orderNo);
        transit(order, OrderStatus.AUDITED, operatorId, operatorName, "审核通过");
    }

    @Transactional
    public void ship(String orderNo, ShipRequest request, Long operatorId, String operatorName) {
        Order order = findOrder(orderNo);
        transit(order, OrderStatus.SHIPPED, operatorId, operatorName, "发货");
    }

    @Transactional
    public void sign(String orderNo, Long operatorId, String operatorName) {
        Order order = findOrder(orderNo);
        transit(order, OrderStatus.SIGNED, operatorId, operatorName, "确认签收");
    }

    @Transactional
    public void complete(String orderNo, Long operatorId, String operatorName) {
        Order order = findOrder(orderNo);
        transit(order, OrderStatus.COMPLETED, operatorId, operatorName, "完成订单");
    }

    @Transactional
    public void handlePaymentSuccess(PaymentSuccessRequest request) {
        Order order = findOrder(request.orderNo());
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            log.info("支付回调重复或状态异常，忽略 orderNo={} status={}", request.orderNo(), order.getStatus());
            return;
        }
        transit(order, OrderStatus.PAID, null, "PAYMENT", "支付成功");
        order.setPaidAt(LocalDateTime.now());
        orderMapper.updateById(order);

        OrderPayment payment = orderPaymentMapper.selectOne(new LambdaQueryWrapper<OrderPayment>()
                .eq(OrderPayment::getPaymentNo, request.paymentNo())
                .last("LIMIT 1"));
        if (payment != null) {
            payment.setStatus(2);
            payment.setChannelTxnNo(request.channelTxnNo());
            payment.setPaidAt(LocalDateTime.now());
            orderPaymentMapper.updateById(payment);
        }

        try {
            inventoryClient.deduct(itemsAsStockRequest(request.orderNo(), order.getId()));
        } catch (Exception ex) {
            log.error("支付成功后库存扣减失败 orderNo={}", request.orderNo(), ex);
        }
    }

    public void timeoutCancelPendingOrders() {
        List<Order> expired = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, OrderStatus.PENDING_PAYMENT)
                .eq(Order::getDeleted, 0)
                .lt(Order::getTimeoutAt, LocalDateTime.now())
                .last("LIMIT 50"));
        for (Order order : expired) {
            try {
                transit(order, OrderStatus.CANCELLED, null, "SYSTEM", "超时未支付自动取消");
                inventoryClient.release(itemsAsStockRequest(order.getOrderNo(), order.getId()));
                log.info("超时订单已取消并释放库存 orderNo={}", order.getOrderNo());
            } catch (Exception ex) {
                log.error("超时取消失败 orderNo={}", order.getOrderNo(), ex);
            }
        }
    }

    private InventoryClient.StockRequest itemsAsStockRequest(String orderNo, Long orderId) {
        List<OrderItemRequest> items = orderItemMapper
                .selectList(new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, orderId)
                        .eq(OrderItem::getDeleted, 0))
                .stream()
                .map(item -> new OrderItemRequest(item.getSkuId(), item.getQuantity()))
                .toList();
        return new InventoryClient.StockRequest(orderNo, items);
    }

    private void transit(
            Order order, int toStatus, Long operatorId, String operatorName, String remark) {
        int from = order.getStatus();
        if (!OrderStatus.canTransit(from, toStatus)) {
            throw new BusinessException(
                    ErrorCode.CONFLICT.getCode(),
                    "订单状态不允许从 " + OrderStatus.name(from) + " 流转到 " + OrderStatus.name(toStatus));
        }
        order.setStatus(toStatus);
        orderMapper.updateById(order);
        appendLog(order.getId(), from, toStatus, operatorId, operatorName, remark);
    }

    private void appendLog(
            Long orderId,
            Integer fromStatus,
            Integer toStatus,
            Long operatorId,
            String operatorName,
            String remark) {
        OrderLog orderLog = new OrderLog();
        orderLog.setOrderId(orderId);
        orderLog.setFromStatus(fromStatus);
        orderLog.setToStatus(toStatus);
        orderLog.setOperatorId(operatorId);
        orderLog.setOperatorName(operatorName);
        orderLog.setRemark(remark);
        orderLogMapper.insert(orderLog);
    }

    private Order findOrder(String orderNo) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo)
                .eq(Order::getDeleted, 0)
                .last("LIMIT 1"));
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "订单不存在");
        }
        return order;
    }

    private String generateOrderNo() {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "O" + ts + ThreadLocalRandom.current().nextInt(10000, 100000);
    }

    private OrderResponse toResponse(Order order, List<OrderItem> items, List<OrderLog> logs) {
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

    private OrderSummaryResponse toSummary(Order order, int itemCount) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getOrderNo(),
                order.getMerchantId(),
                order.getOrderType(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getPayAmount(),
                order.getCreatedAt(),
                itemCount);
    }
}
