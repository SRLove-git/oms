package com.oms.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
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
import com.oms.order.dto.OpenOrderDtos.OpenCreateOrderRequest;
import com.oms.order.dto.OpenOrderDtos.OpenOrderResponse;
import com.oms.order.dto.OpenOrderDtos.OpenPaymentNotifyRequest;
import com.oms.order.entity.Order;
import com.oms.order.entity.OrderArchive;
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
import org.springframework.dao.DuplicateKeyException;
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
    private final OrderArchiveService orderArchiveService;

    @Value("${oms.order.timeout-minutes:30}")
    private long timeoutMinutes;

    @Value("${oms.order.currency:SGD}")
    private String defaultCurrency;

    public OrderService(
            OrderMapper orderMapper,
            OrderItemMapper orderItemMapper,
            OrderPaymentMapper orderPaymentMapper,
            OrderLogMapper orderLogMapper,
            InventoryClient inventoryClient,
            PaymentClient paymentClient,
            OrderArchiveService orderArchiveService) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.orderPaymentMapper = orderPaymentMapper;
        this.orderLogMapper = orderLogMapper;
        this.inventoryClient = inventoryClient;
        this.paymentClient = paymentClient;
        this.orderArchiveService = orderArchiveService;
    }

    @SentinelResource(value = "order.create", blockHandler = "createBlocked")
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
        try {
            ensureSuccess(inventoryClient.reserve(stockRequest), "库存预占");
        } catch (BusinessException ex) {
            // 业务失败（如库存不足）：明确未预占，无需释放
            throw ex;
        } catch (RuntimeException ex) {
            // 远程异常：预占结果未知，尝试释放避免库存泄漏
            try {
                inventoryClient.release(stockRequest);
            } catch (Exception releaseEx) {
                log.error("预占异常后释放失败 orderNo={}", orderNo, releaseEx);
            }
            throw ex;
        }

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

    /**
     * 商城开放 API 下单：以 {@code externalOrderNo} 作为幂等键，重复提交返回已存在的订单。
     */
    @Transactional
    public OpenOrderResponse createOpen(OpenCreateOrderRequest request, Long merchantId) {
        if (request.externalOrderNo() == null || request.externalOrderNo().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "externalOrderNo 不能为空");
        }
        Order existing = findByExternalOrderNo(request.externalOrderNo());
        if (existing != null) {
            requireMerchant(existing.getMerchantId(), merchantId);
            return toOpenResponse(existing, loadItems(existing.getId()));
        }

        OrderResponse created = create(
                new CreateOrderRequest(merchantId, request.orderType(), request.remark(), request.items()),
                null, "OPEN_API");
        Order order = findOrder(created.orderNo());
        order.setExternalOrderNo(request.externalOrderNo());
        order.setSource("OPEN_API");
        try {
            orderMapper.updateById(order);
        } catch (DuplicateKeyException ex) {
            // 并发重复提交：回滚本次新建订单（释放库存），返回已存在的幂等订单
            try {
                cancel(created.orderNo(), new CancelRequest("外部订单号重复"), null, "OPEN_API", 1);
            } catch (Exception rollbackEx) {
                log.error("外部订单号冲突回滚失败 orderNo={}", created.orderNo(), rollbackEx);
            }
            Order duplicated = findByExternalOrderNo(request.externalOrderNo());
            if (duplicated != null) {
                requireMerchant(duplicated.getMerchantId(), merchantId);
                return toOpenResponse(duplicated, loadItems(duplicated.getId()));
            }
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "外部订单号冲突");
        }
        return toOpenResponse(order, loadItems(order.getId()));
    }

    /**
     * 商城开放 API 查单：热表优先，热表未命中回落到归档表。
     */
    public OpenOrderResponse getOpen(String externalOrderNo, Long merchantId) {
        Order order = findByExternalOrderNo(externalOrderNo);
        if (order != null) {
            requireMerchant(order.getMerchantId(), merchantId);
            return toOpenResponse(order, loadItems(order.getId()));
        }
        OrderArchive archived = orderArchiveService.findByExternalOrderNo(externalOrderNo);
        if (archived != null) {
            requireMerchant(archived.getMerchantId(), merchantId);
            OrderResponse detail = orderArchiveService.getByOrderNo(archived.getOrderNo());
            return new OpenOrderResponse(
                    detail.orderNo(),
                    archived.getExternalOrderNo(),
                    archived.getSource(),
                    archived.getOrderType(),
                    detail.status(),
                    detail.totalAmount(),
                    detail.currency(),
                    detail.remark(),
                    detail.paidAt(),
                    detail.createdAt(),
                    detail.items());
        }
        throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "外部订单不存在");
    }

    /**
     * 商城开放 API 取消：仅待支付订单可取消，取消后自动释放库存。
     */
    @Transactional
    public void cancelOpen(String externalOrderNo, Long merchantId) {
        Order order = findByExternalOrderNo(externalOrderNo);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "外部订单不存在");
        }
        requireMerchant(order.getMerchantId(), merchantId);
        cancel(order.getOrderNo(), new CancelRequest("商城取消"), null, "OPEN_API", null);
    }

    /**
     * 商城支付成功通知：商城侧收款完成后通知 OMS，订单待支付 → 已支付并扣减库存。
     *
     * <ul>
     *   <li>幂等：重复通知（同一支付单号或已支付状态）返回当前订单，不重复扣减；</li>
     *   <li>金额校验：通知金额必须与订单应付金额一致，不一致拒绝并告警；</li>
     *   <li>并发安全：条件状态流转，与超时取消互斥。</li>
     * </ul>
     */
    @Transactional
    public OpenOrderResponse notifyPaymentSuccess(
            String externalOrderNo, OpenPaymentNotifyRequest request, Long merchantId) {
        if (request.paymentNo() == null || request.paymentNo().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "paymentNo 不能为空");
        }
        Order order = findByExternalOrderNo(externalOrderNo);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "外部订单不存在");
        }
        requireMerchant(order.getMerchantId(), merchantId);

        int status = order.getStatus();
        if (status == OrderStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "订单已取消，不能支付");
        }
        if (status != OrderStatus.PENDING_PAYMENT) {
            // 已支付及之后状态：重复通知幂等返回当前订单
            return toOpenResponse(order, loadItems(order.getId()));
        }
        if (request.amount() == null || request.amount().compareTo(order.getPayAmount()) != 0) {
            log.error("商城支付通知金额不一致，拒绝 orderNo={} expect={} actual={}",
                    order.getOrderNo(), order.getPayAmount(), request.amount());
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "支付金额与订单应付金额不一致");
        }

        LocalDateTime paidAt = request.paidAt() == null ? LocalDateTime.now() : request.paidAt();
        if (!transitIfStatus(order.getId(), OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, paidAt)) {
            Order fresh = findOrder(order.getOrderNo());
            if (fresh.getStatus() != OrderStatus.PENDING_PAYMENT && fresh.getStatus() != OrderStatus.CANCELLED) {
                return toOpenResponse(fresh, loadItems(fresh.getId()));
            }
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "订单状态已变化，请刷新后重试");
        }
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(paidAt);
        appendLog(order.getId(), OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, null, "OPEN_API", "商城支付成功通知");
        recordOpenPayment(order, request);

        try {
            inventoryClient.deduct(itemsAsStockRequest(order.getOrderNo(), order.getId()));
        } catch (Exception ex) {
            log.error("商城支付通知后库存扣减失败 orderNo={}", order.getOrderNo(), ex);
        }
        return toOpenResponse(order, loadItems(order.getId()));
    }

    /**
     * 记录商城支付单（支付单号唯一，重复插入幂等忽略）。
     */
    private void recordOpenPayment(Order order, OpenPaymentNotifyRequest request) {
        Long exists = orderPaymentMapper.selectCount(new LambdaQueryWrapper<OrderPayment>()
                .eq(OrderPayment::getPaymentNo, request.paymentNo()));
        if (exists > 0) {
            return;
        }
        OrderPayment payment = new OrderPayment();
        payment.setOrderId(order.getId());
        payment.setPaymentNo(request.paymentNo());
        payment.setChannel(request.channel() == null || request.channel().isBlank()
                ? "OPEN_API" : request.channel());
        payment.setAmount(order.getPayAmount());
        payment.setCurrency(order.getCurrency());
        payment.setStatus(2);
        payment.setChannelTxnNo(request.channelTxnNo());
        payment.setPaidAt(order.getPaidAt());
        try {
            orderPaymentMapper.insert(payment);
        } catch (DuplicateKeyException ex) {
            log.info("商城支付单重复插入，忽略 paymentNo={}", request.paymentNo());
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
        order.setCurrency(defaultCurrency);
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
            BigDecimal cost = sku.costPrice() == null ? BigDecimal.ZERO : sku.costPrice();
            orderItem.setCostAmount(cost.multiply(BigDecimal.valueOf(item.quantity())));
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

    private <T> T ensureSuccessData(Result<T> result, String action) {
        ensureSuccess(result, action);
        return result.data();
    }

    public OrderResponse get(String orderNo) {
        Order order = findOrderOrNull(orderNo);
        if (order == null) {
            return orderArchiveService.getByOrderNo(orderNo);
        }
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
            cancelWithCondition(order, from, operatorId, operatorName, "取消订单");
            inventoryClient.release(itemsAsStockRequest(orderNo, order.getId()));
        } else if (from == OrderStatus.PAID && admin) {
            cancelWithCondition(order, from, operatorId, operatorName, "管理员取消已支付订单");
            inventoryClient.restore(itemsAsStockRequest(orderNo, order.getId()));
        } else {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "当前状态不允许取消");
        }
    }

    /**
     * 条件取消：仅当订单仍处于 {@code from} 状态时流转为已取消，
     * 防止并发支付回调将订单置为已支付后被取消覆盖。
     */
    private void cancelWithCondition(
            Order order, int from, Long operatorId, String operatorName, String remark) {
        if (!transitIfStatus(order.getId(), from, OrderStatus.CANCELLED)) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "订单状态已变化，请刷新后重试");
        }
        order.setStatus(OrderStatus.CANCELLED);
        appendLog(order.getId(), from, OrderStatus.CANCELLED, operatorId, operatorName, remark);
    }

    public OrderResponse createBlocked(
            CreateOrderRequest request, Long operatorId, String operatorName, BlockException ex) {
        throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE.getCode(), "下单流量过大，请稍后重试");
    }

    @SentinelResource(value = "order.pay", blockHandler = "payBlocked")
    @Transactional
    public PaymentClient.CreatePaymentResponse pay(String orderNo, PayRequest request, Long operatorId) {
        Order order = findOrder(orderNo);
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "订单状态不允许支付");
        }
        String channel = request.channel() == null ? "mock" : request.channel();
        PaymentClient.CreatePaymentRequest paymentRequest = new PaymentClient.CreatePaymentRequest(
                orderNo, order.getPayAmount(), order.getCurrency(), channel, order.getMerchantId());
        PaymentClient.CreatePaymentResponse response =
                ensureSuccessData(paymentClient.create(paymentRequest), "支付发起");
        if (response == null || response.paymentNo() == null) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "支付发起失败: 远程响应为空");
        }

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
    public void markAfterSales(String orderNo, String returnNo, Integer type, Long operatorId, String operatorName) {
        Order order = findOrder(orderNo);
        transit(order, OrderStatus.AFTER_SALES, operatorId, operatorName, "售后介入：" + returnNo);
    }

    @Transactional
    public void completeAfterSales(String orderNo, Long operatorId, String operatorName) {
        Order order = findOrder(orderNo);
        transit(order, OrderStatus.COMPLETED, operatorId, operatorName, "售后处理完成");
    }

    public PaymentClient.CreatePaymentResponse payBlocked(
            String orderNo, PayRequest request, Long operatorId, BlockException ex) {
        throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE.getCode(), "支付请求流量过大，请稍后重试");
    }

    @SentinelResource(value = "order.handlePaymentSuccess", blockHandler = "handlePaymentSuccessBlocked")
    @Transactional
    public void handlePaymentSuccess(PaymentSuccessRequest request) {
        Order order = findOrder(request.orderNo());
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            log.info("支付回调重复或状态异常，忽略 orderNo={} status={}", request.orderNo(), order.getStatus());
            return;
        }
        // 金额校验：回调金额与应付金额不一致时忽略并告警，进入人工核对
        if (request.amount() != null
                && order.getPayAmount() != null
                && request.amount().compareTo(order.getPayAmount()) != 0) {
            log.error("支付金额不一致，忽略回调 orderNo={} expect={} actual={}",
                    request.orderNo(), order.getPayAmount(), request.amount());
            return;
        }
        // 条件流转：仅待支付订单可置为已支付，防止超时取消/重复回调并发覆盖
        LocalDateTime paidAt = LocalDateTime.now();
        if (!transitIfStatus(order.getId(), OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, paidAt)) {
            log.info("支付回调状态已变化，忽略 orderNo={}", request.orderNo());
            return;
        }
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(paidAt);
        appendLog(order.getId(), OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, null, "PAYMENT", "支付成功");

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

    /**
     * 支付回调限流兜底：直接丢弃并记录，渠道侧会按重试策略再次回调。
     */
    public void handlePaymentSuccessBlocked(PaymentSuccessRequest request, BlockException ex) {
        log.warn("支付回调被限流丢弃 orderNo={}", request.orderNo());
    }

    public void timeoutCancelPendingOrders() {
        List<Order> expired = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, OrderStatus.PENDING_PAYMENT)
                .eq(Order::getDeleted, 0)
                .lt(Order::getTimeoutAt, LocalDateTime.now())
                .last("LIMIT 50"));
        for (Order order : expired) {
            try {
                // 条件流转：查询与更新之间若已被支付回调置为已支付，则跳过并保留库存
                if (!transitIfStatus(order.getId(), OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED)) {
                    log.info("超时取消跳过（订单状态已变化） orderNo={}", order.getOrderNo());
                    continue;
                }
                order.setStatus(OrderStatus.CANCELLED);
                appendLog(order.getId(), OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED,
                        null, "SYSTEM", "超时未支付自动取消");
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

    /**
     * 条件状态流转：仅当订单仍处于 {@code fromStatus} 时更新为目标状态（乐观并发控制）。
     *
     * <p>使用普通 UpdateWrapper（字符串列名）而非 LambdaUpdateWrapper：
     * 后者依赖 MyBatis-Plus 的 lambda 缓存初始化，无法在纯单元测试环境使用。
     */
    private boolean transitIfStatus(Long orderId, int fromStatus, int toStatus) {
        return orderMapper.update(null, new UpdateWrapper<Order>()
                .eq("id", orderId)
                .eq("status", fromStatus)
                .set("status", toStatus)) > 0;
    }

    /**
     * 条件状态流转（带支付时间）：支付成功回调专用。
     */
    private boolean transitIfStatus(Long orderId, int fromStatus, int toStatus, LocalDateTime paidAt) {
        return orderMapper.update(null, new UpdateWrapper<Order>()
                .eq("id", orderId)
                .eq("status", fromStatus)
                .set("status", toStatus)
                .set("paid_at", paidAt)) > 0;
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
        Order order = findOrderOrNull(orderNo);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "订单不存在");
        }
        return order;
    }

    private Order findOrderOrNull(String orderNo) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo)
                .eq(Order::getDeleted, 0)
                .last("LIMIT 1"));
        return order;
    }

    private Order findByExternalOrderNo(String externalOrderNo) {
        return orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getExternalOrderNo, externalOrderNo)
                .eq(Order::getDeleted, 0)
                .last("LIMIT 1"));
    }

    private List<OrderItem> loadItems(Long orderId) {
        return orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orderId)
                .eq(OrderItem::getDeleted, 0)
                .orderByAsc(OrderItem::getId));
    }

    private void requireMerchant(Long ownerMerchantId, Long merchantId) {
        if (merchantId == null || !merchantId.equals(ownerMerchantId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private OpenOrderResponse toOpenResponse(Order order, List<OrderItem> items) {
        return new OpenOrderResponse(
                order.getOrderNo(),
                order.getExternalOrderNo(),
                order.getSource(),
                order.getOrderType(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getRemark(),
                order.getPaidAt(),
                order.getCreatedAt(),
                items.stream()
                        .map(item -> new OrderItemResponse(
                                item.getId(),
                                item.getSkuId(),
                                item.getSkuName(),
                                item.getQuantity(),
                                item.getUnitPrice(),
                                item.getTotalPrice()))
                        .toList());
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
