package com.oms.aftersales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oms.aftersales.client.IntegrationClient;
import com.oms.aftersales.client.InventoryClient;
import com.oms.aftersales.client.MallCallbackNotifier;
import com.oms.aftersales.client.OrderClient;
import com.oms.aftersales.client.PaymentClient;
import com.oms.aftersales.dto.AfterSalesDtos.ApplyItemRequest;
import com.oms.aftersales.dto.AfterSalesDtos.ApplyRequest;
import com.oms.aftersales.dto.AfterSalesDtos.InspectRequest;
import com.oms.aftersales.dto.AfterSalesDtos.RefundRequest;
import com.oms.aftersales.dto.AfterSalesDtos.RepairCreateRequest;
import com.oms.aftersales.dto.AfterSalesDtos.RepairFeeRequest;
import com.oms.aftersales.dto.AfterSalesDtos.RepairProgressRequest;
import com.oms.aftersales.dto.AfterSalesDtos.RepairResponse;
import com.oms.aftersales.dto.AfterSalesDtos.RepairLogResponse;
import com.oms.aftersales.dto.AfterSalesDtos.ReviewRequest;
import com.oms.aftersales.dto.AfterSalesDtos.ReturnOrderResponse;
import com.oms.aftersales.dto.AfterSalesDtos.ReturnOrderSummaryResponse;
import com.oms.aftersales.dto.AfterSalesDtos.ReturnItemResponse;
import com.oms.aftersales.dto.AfterSalesDtos.RefundRecordResponse;
import com.oms.aftersales.dto.OpenAfterSalesDtos.OpenReturnOrderRequest;
import com.oms.aftersales.dto.OpenAfterSalesDtos.OpenReturnOrderResponse;
import com.oms.aftersales.entity.RefundRecord;
import com.oms.aftersales.entity.RepairLog;
import com.oms.aftersales.entity.RepairOrder;
import com.oms.aftersales.entity.ReturnItem;
import com.oms.aftersales.entity.ReturnOrder;
import com.oms.aftersales.mapper.RefundRecordMapper;
import com.oms.aftersales.mapper.RepairLogMapper;
import com.oms.aftersales.mapper.RepairOrderMapper;
import com.oms.aftersales.mapper.ReturnItemMapper;
import com.oms.aftersales.mapper.ReturnOrderMapper;
import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.ErrorCode;
import com.oms.common.core.result.PageResult;
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
public class AfterSalesService {

    @Value("${oms.aftersales.currency:SGD}")
    private String defaultCurrency;

    private static final Logger log = LoggerFactory.getLogger(AfterSalesService.class);

    private static final int STATUS_PENDING_REVIEW = 1;
    private static final int STATUS_APPROVED = 2;
    private static final int STATUS_REJECTED = 3;
    private static final int STATUS_INSPECTING = 4;
    private static final int STATUS_REFUNDING = 5;
    private static final int STATUS_COMPLETED = 6;
    private static final int STATUS_CANCELLED = 7;

    private final ReturnOrderMapper returnOrderMapper;
    private final ReturnItemMapper returnItemMapper;
    private final RefundRecordMapper refundRecordMapper;
    private final RepairOrderMapper repairOrderMapper;
    private final RepairLogMapper repairLogMapper;
    private final OrderClient orderClient;
    private final PaymentClient paymentClient;
    private final InventoryClient inventoryClient;
    private final IntegrationClient integrationClient;
    private final MallCallbackNotifier mallCallbackNotifier;

    public AfterSalesService(
            ReturnOrderMapper returnOrderMapper,
            ReturnItemMapper returnItemMapper,
            RefundRecordMapper refundRecordMapper,
            RepairOrderMapper repairOrderMapper,
            RepairLogMapper repairLogMapper,
            OrderClient orderClient,
            PaymentClient paymentClient,
            InventoryClient inventoryClient,
            IntegrationClient integrationClient,
            MallCallbackNotifier mallCallbackNotifier) {
        this.returnOrderMapper = returnOrderMapper;
        this.returnItemMapper = returnItemMapper;
        this.refundRecordMapper = refundRecordMapper;
        this.repairOrderMapper = repairOrderMapper;
        this.repairLogMapper = repairLogMapper;
        this.orderClient = orderClient;
        this.paymentClient = paymentClient;
        this.inventoryClient = inventoryClient;
        this.integrationClient = integrationClient;
        this.mallCallbackNotifier = mallCallbackNotifier;
    }

    @Transactional
    public ReturnOrderResponse apply(ApplyRequest request, Long operatorId, String operatorName) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "售后明细不能为空");
        }
        OrderClient.OrderDetail order = getOrder(request.orderNo());
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "订单不存在");
        }
        return applyInternal(order, request.type(), request.reason(), request.items(), false);
    }

    /**
     * 商城开放 API 申请退款：无需传 orderItemId，按整单生成售后明细。
     * 商城已支付/已发货/已完成订单均允许发起，由 OMS 管理端后续审核。
     */
    @Transactional
    public ReturnOrderResponse applyOpen(OpenReturnOrderRequest request, Long merchantId) {
        if (request == null || request.externalOrderNo() == null || request.externalOrderNo().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "externalOrderNo 不能为空");
        }
        OrderClient.OrderDetail order = getOrderByExternalOrderNo(request.externalOrderNo());
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "订单不存在");
        }
        if (merchantId != null && !merchantId.equals(order.merchantId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权操作该订单");
        }
        List<ApplyItemRequest> items = order.items() == null ? List.of() : order.items().stream()
                .map(item -> new ApplyItemRequest(item.id(), item.skuId(), item.quantity()))
                .toList();
        return applyInternal(order, request.type(), request.reason(), items, true);
    }

    public OpenReturnOrderResponse getOpenByExternal(String externalOrderNo, Long merchantId) {
        if (externalOrderNo == null || externalOrderNo.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "externalOrderNo 不能为空");
        }
        OrderClient.OrderDetail order = getOrderByExternalOrderNo(externalOrderNo);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "订单不存在");
        }
        if (merchantId != null && !merchantId.equals(order.merchantId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权操作该订单");
        }
        ReturnOrder returnOrder = returnOrderMapper.selectOne(new LambdaQueryWrapper<ReturnOrder>()
                .eq(ReturnOrder::getOrderNo, order.orderNo())
                .eq(ReturnOrder::getDeleted, 0)
                .orderByDesc(ReturnOrder::getId)
                .last("LIMIT 1"));
        if (returnOrder == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "售后单不存在");
        }
        return new OpenReturnOrderResponse(
                returnOrder.getReturnNo(),
                returnOrder.getOrderNo(),
                externalOrderNo,
                returnOrder.getType(),
                returnOrder.getStatus(),
                returnOrder.getReason(),
                returnOrder.getTotalAmount(),
                returnOrder.getCreatedAt());
    }

    private ReturnOrderResponse applyInternal(
            OrderClient.OrderDetail order,
            Integer type,
            String reason,
            List<ApplyItemRequest> items,
            boolean allowPaidOrder) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "售后明细不能为空");
        }
        if (allowPaidOrder) {
            if (!List.of(2, 3, 4, 5, 6).contains(order.status())) {
                throw new BusinessException(ErrorCode.CONFLICT.getCode(), "当前订单状态不允许申请售后");
            }
        } else if (order.status() != 4 && order.status() != 5 && order.status() != 6) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "当前订单状态不允许申请售后");
        }
        boolean exists = returnOrderMapper.selectCount(new LambdaQueryWrapper<ReturnOrder>()
                        .eq(ReturnOrder::getOrderNo, order.orderNo())
                        .in(ReturnOrder::getStatus, List.of(STATUS_PENDING_REVIEW, STATUS_APPROVED, STATUS_INSPECTING, STATUS_REFUNDING)))
                > 0;
        if (exists) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "该订单存在进行中的售后单");
        }

        ReturnOrder returnOrder = new ReturnOrder();
        returnOrder.setReturnNo(generateNo("R"));
        returnOrder.setOrderId(order.id());
        returnOrder.setOrderNo(order.orderNo());
        returnOrder.setMerchantId(order.merchantId());
        returnOrder.setType(type == null ? 1 : type);
        returnOrder.setStatus(STATUS_PENDING_REVIEW);
        returnOrder.setPreviousStatus(order.status());
        returnOrder.setReason(reason);
        BigDecimal total = BigDecimal.ZERO;
        for (ApplyItemRequest item : items) {
            OrderClient.OrderItem orderItem = order.items().stream()
                    .filter(i -> i.id().equals(item.orderItemId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.BAD_REQUEST.getCode(), "售后商品不在订单明细中 orderItemId=" + item.orderItemId()));
            if (item.quantity() <= 0 || item.quantity() > orderItem.quantity()) {
                throw new BusinessException(
                        ErrorCode.BAD_REQUEST.getCode(), "售后数量超出订单明细数量 skuId=" + item.skuId());
            }
            total = total.add(orderItem.unitPrice().multiply(BigDecimal.valueOf(item.quantity())));
        }
        returnOrder.setTotalAmount(total);
        returnOrderMapper.insert(returnOrder);

        for (ApplyItemRequest item : items) {
            OrderClient.OrderItem orderItem = order.items().stream()
                    .filter(i -> i.id().equals(item.orderItemId()))
                    .findFirst()
                    .orElseThrow();
            ReturnItem returnItem = new ReturnItem();
            returnItem.setReturnId(returnOrder.getId());
            returnItem.setOrderItemId(orderItem.id());
            returnItem.setSkuId(item.skuId());
            returnItem.setQuantity(item.quantity());
            returnItem.setUnitAmount(orderItem.unitPrice());
            returnItemMapper.insert(returnItem);
        }

        bestEffort(() -> orderClient.notifyAfterSales(
                order.orderNo(),
                new OrderClient.AfterSalesNotifyRequest(returnOrder.getReturnNo(), returnOrder.getType(), order.status())));
        bestEffort(() -> integrationClient.send(new IntegrationClient.SendRequest(
                "in_app", "AFTER_SALES_APPLIED", "merchant:" + order.merchantId(),
                "售后申请已提交", "订单 " + order.orderNo() + " 的售后申请 " + returnOrder.getReturnNo() + " 已提交，等待审核")));
        return get(returnOrder.getReturnNo());
    }

    public PageResult<ReturnOrderSummaryResponse> page(Long merchantId, Integer status, int page, int size) {
        LambdaQueryWrapper<ReturnOrder> wrapper = new LambdaQueryWrapper<ReturnOrder>()
                .eq(ReturnOrder::getDeleted, 0)
                .orderByDesc(ReturnOrder::getId);
        if (merchantId != null) {
            wrapper.eq(ReturnOrder::getMerchantId, merchantId);
        }
        if (status != null) {
            wrapper.eq(ReturnOrder::getStatus, status);
        }
        Page<ReturnOrder> result = returnOrderMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(
                result.getTotal(),
                result.getRecords().stream().map(this::toSummary).toList());
    }

    public ReturnOrderResponse get(String returnNo) {
        ReturnOrder returnOrder = find(returnNo);
        List<ReturnItem> items = returnItemMapper.selectList(new LambdaQueryWrapper<ReturnItem>()
                .eq(ReturnItem::getReturnId, returnOrder.getId())
                .eq(ReturnItem::getDeleted, 0)
                .orderByAsc(ReturnItem::getId));
        List<RefundRecord> refunds = refundRecordMapper.selectList(new LambdaQueryWrapper<RefundRecord>()
                .eq(RefundRecord::getReturnId, returnOrder.getId())
                .eq(RefundRecord::getDeleted, 0)
                .orderByAsc(RefundRecord::getId));
        List<RepairOrder> repairs = repairOrderMapper.selectList(new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getReturnId, returnOrder.getId())
                .eq(RepairOrder::getDeleted, 0)
                .orderByAsc(RepairOrder::getId));
        return toResponse(returnOrder, items, refunds, repairs);
    }

    @Transactional
    public void review(String returnNo, ReviewRequest request, Long operatorId, String operatorName) {
        ReturnOrder returnOrder = find(returnNo);
        requireStatus(returnOrder, STATUS_PENDING_REVIEW);
        returnOrder.setStatus(request.approved() ? STATUS_APPROVED : STATUS_REJECTED);
        returnOrderMapper.updateById(returnOrder);
        if (request.approved()) {
            bestEffort(() -> orderClient.notifyAfterSales(
                    returnOrder.getOrderNo(),
                    new OrderClient.AfterSalesNotifyRequest(returnOrder.getReturnNo(), returnOrder.getType(), null)));
        } else {
            restorePreviousOrderStatus(returnOrder);
        }
        notifyMall(returnOrder);
        bestEffort(() -> integrationClient.send(new IntegrationClient.SendRequest(
                "in_app",
                "AFTER_SALES_REVIEWED",
                "merchant:" + returnOrder.getMerchantId(),
                "售后单审核结果",
                "售后单 " + returnNo + (request.approved() ? " 已审核通过" : " 已驳回："
                        + (request.reason() == null ? "" : request.reason())))));
    }

    @Transactional
    public void receiveAndInspect(String returnNo, InspectRequest request, Long operatorId, String operatorName) {
        ReturnOrder returnOrder = find(returnNo);
        requireStatus(returnOrder, STATUS_APPROVED);
        returnOrder.setStatus(STATUS_INSPECTING);
        returnOrderMapper.updateById(returnOrder);
        if (request.qualified()) {
            if (returnOrder.getType() == 1) {
                restoreStock(returnOrder);
                returnOrder.setStatus(STATUS_REFUNDING);
                returnOrderMapper.updateById(returnOrder);
                bestEffort(() -> integrationClient.send(new IntegrationClient.SendRequest(
                        "in_app",
                        "AFTER_SALES_INSPECTED",
                        "merchant:" + returnOrder.getMerchantId(),
                        "售后商品已收货质检",
                        "售后单 " + returnNo + " 商品已质检合格，等待退款")));
            } else if (returnOrder.getType() == 2) {
                restoreStock(returnOrder);
                bestEffort(() -> integrationClient.send(new IntegrationClient.SendRequest(
                        "in_app",
                        "AFTER_SALES_INSPECTED",
                        "merchant:" + returnOrder.getMerchantId(),
                        "售后商品已收货质检",
                        "售后单 " + returnNo + " 商品已质检合格，等待换货发运")));
            } else {
                bestEffort(() -> integrationClient.send(new IntegrationClient.SendRequest(
                        "in_app",
                        "AFTER_SALES_INSPECTED",
                        "merchant:" + returnOrder.getMerchantId(),
                        "售后商品已收货质检",
                        "售后单 " + returnNo + " 商品已质检合格，等待创建维修工单")));
            }
        } else {
            returnOrder.setStatus(STATUS_REJECTED);
            returnOrderMapper.updateById(returnOrder);
            restorePreviousOrderStatus(returnOrder);
            notifyMall(returnOrder);
            bestEffort(() -> integrationClient.send(new IntegrationClient.SendRequest(
                    "in_app",
                    "AFTER_SALES_REJECTED",
                    "merchant:" + returnOrder.getMerchantId(),
                    "售后商品质检不通过",
                    "售后单 " + returnNo + " 质检不通过：" + (request.remark() == null ? "" : request.remark()))));
        }
        notifyMall(returnOrder);
    }

    @Transactional
    public void refund(String returnNo, RefundRequest request, Long operatorId, String operatorName) {
        ReturnOrder returnOrder = find(returnNo);
        requireStatus(returnOrder, STATUS_REFUNDING);
        RefundRecord record = new RefundRecord();
        record.setRefundNo(generateNo("RF"));
        record.setReturnId(returnOrder.getId());
        record.setOrderId(returnOrder.getOrderId());
        record.setPaymentNo(request.paymentNo());
        record.setAmount(request.amount() == null ? returnOrder.getTotalAmount() : request.amount());
        record.setCurrency(defaultCurrency);
        record.setMethod(request.method() == null ? 1 : request.method());
        record.setStatus(3);
        record.setChannelTxnNo("REF-" + System.currentTimeMillis());
        record.setRefundedAt(LocalDateTime.now());
        refundRecordMapper.insert(record);

        bestEffort(() -> paymentClient.refund(
                request.paymentNo(),
                new PaymentClient.RefundRequest(record.getAmount(), "售后退款 " + returnNo)));
        returnOrder.setStatus(STATUS_COMPLETED);
        returnOrderMapper.updateById(returnOrder);
        completeOrderLinkage(returnOrder);
        notifyMall(returnOrder);
        bestEffort(() -> integrationClient.send(new IntegrationClient.SendRequest(
                "in_app",
                "AFTER_SALES_REFUNDED",
                "merchant:" + returnOrder.getMerchantId(),
                "售后退款成功",
                "售后单 " + returnNo + " 退款 " + record.getAmount() + " 元已原路退回")));
    }

    @Transactional
    public void exchangeShip(String returnNo, Long operatorId, String operatorName) {
        ReturnOrder returnOrder = find(returnNo);
        if (returnOrder.getType() != 2) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "仅换货单支持换货发运");
        }
        requireStatus(returnOrder, STATUS_INSPECTING);
        returnOrder.setStatus(STATUS_COMPLETED);
        returnOrderMapper.updateById(returnOrder);
        completeOrderLinkage(returnOrder);
        notifyMall(returnOrder);
        bestEffort(() -> integrationClient.send(new IntegrationClient.SendRequest(
                "in_app",
                "AFTER_SALES_EXCHANGED",
                "merchant:" + returnOrder.getMerchantId(),
                "换货已发运",
                "售后单 " + returnNo + " 换货商品已发运")));
    }

    @Transactional
    public void cancel(String returnNo, Long operatorId, String operatorName) {
        ReturnOrder returnOrder = find(returnNo);
        requireStatusIn(returnOrder, List.of(STATUS_PENDING_REVIEW, STATUS_APPROVED, STATUS_INSPECTING));
        returnOrder.setStatus(STATUS_CANCELLED);
        returnOrderMapper.updateById(returnOrder);
        restorePreviousOrderStatus(returnOrder);
        notifyMall(returnOrder);
    }

    @Transactional
    public RepairResponse createRepair(String returnNo, RepairCreateRequest request, Long operatorId, String operatorName) {
        ReturnOrder returnOrder = find(returnNo);
        if (returnOrder.getType() != 3) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "仅维修类售后支持创建维修工单");
        }
        requireStatusIn(returnOrder, List.of(STATUS_APPROVED, STATUS_INSPECTING));
        RepairOrder repair = new RepairOrder();
        repair.setRepairNo(generateNo("RP"));
        repair.setReturnId(returnOrder.getId());
        repair.setReturnNo(returnOrder.getReturnNo());
        repair.setOrderNo(returnOrder.getOrderNo());
        repair.setSkuId(request.skuId());
        repair.setStatus(1);
        repair.setFaultDesc(request.faultDesc());
        repair.setAssignedTo(request.assignedTo());
        repair.setRepairFee(BigDecimal.ZERO);
        repairOrderMapper.insert(repair);
        appendRepairLog(repair.getId(), "assign", "创建维修工单，指派给 " + request.assignedTo(), operatorName);
        if (returnOrder.getStatus() == STATUS_APPROVED) {
            returnOrder.setStatus(STATUS_INSPECTING);
            returnOrderMapper.updateById(returnOrder);
        }
        return repairResponse(repair);
    }

    @Transactional
    public void repairProgress(Long repairId, RepairProgressRequest request, Long operatorId, String operatorName) {
        RepairOrder repair = findRepair(repairId);
        String action = request.action();
        if ("start".equals(action)) {
            requireRepairStatus(repair, 1);
            repair.setStatus(2);
        } else if ("complete".equals(action)) {
            requireRepairStatus(repair, 2);
            repair.setStatus(4);
            repair.setFinishedAt(LocalDateTime.now());
            ReturnOrder returnOrder = returnOrderMapper.selectById(repair.getReturnId());
            if (returnOrder != null && returnOrder.getStatus() != STATUS_COMPLETED) {
                returnOrder.setStatus(STATUS_COMPLETED);
                returnOrderMapper.updateById(returnOrder);
                completeOrderLinkage(returnOrder);
                notifyMall(returnOrder);
            }
        } else if ("cancel".equals(action)) {
            requireRepairStatusIn(repair, List.of(1, 2));
            repair.setStatus(5);
        } else {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "不支持的维修动作: " + action);
        }
        repairOrderMapper.updateById(repair);
        appendRepairLog(repair.getId(), action, request.content(), operatorName);
        if ("complete".equals(action)) {
            ReturnOrder returnOrder = returnOrderMapper.selectById(repair.getReturnId());
            if (returnOrder != null) {
                bestEffort(() -> integrationClient.send(new IntegrationClient.SendRequest(
                        "in_app",
                        "AFTER_SALES_REPAIRED",
                        "merchant:" + returnOrder.getMerchantId(),
                        "维修完成",
                        "维修单 " + repair.getRepairNo() + " 已完成")));
            }
        }
    }

    @Transactional
    public void repairFee(Long repairId, RepairFeeRequest request, Long operatorId, String operatorName) {
        RepairOrder repair = findRepair(repairId);
        repair.setRepairFee(request.repairFee());
        repairOrderMapper.updateById(repair);
        appendRepairLog(repair.getId(), "fee", "维修费用 " + request.repairFee() + " 元", operatorName);
    }

    private ReturnOrder find(String returnNo) {
        ReturnOrder returnOrder = returnOrderMapper.selectOne(new LambdaQueryWrapper<ReturnOrder>()
                .eq(ReturnOrder::getReturnNo, returnNo)
                .eq(ReturnOrder::getDeleted, 0)
                .last("LIMIT 1"));
        if (returnOrder == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "售后单不存在");
        }
        return returnOrder;
    }

    private RepairOrder findRepair(Long repairId) {
        RepairOrder repair = repairOrderMapper.selectOne(new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getId, repairId)
                .eq(RepairOrder::getDeleted, 0)
                .last("LIMIT 1"));
        if (repair == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "维修工单不存在");
        }
        return repair;
    }

    private void requireStatus(ReturnOrder returnOrder, int expected) {
        if (returnOrder.getStatus() == null || returnOrder.getStatus() != expected) {
            throw new BusinessException(
                    ErrorCode.CONFLICT.getCode(), "售后单状态不允许该操作，当前状态=" + returnOrder.getStatus());
        }
    }

    private void requireStatusIn(ReturnOrder returnOrder, List<Integer> expected) {
        if (returnOrder.getStatus() == null || !expected.contains(returnOrder.getStatus())) {
            throw new BusinessException(
                    ErrorCode.CONFLICT.getCode(), "售后单状态不允许该操作，当前状态=" + returnOrder.getStatus());
        }
    }

    private void requireRepairStatus(RepairOrder repair, int expected) {
        if (repair.getStatus() == null || repair.getStatus() != expected) {
            throw new BusinessException(
                    ErrorCode.CONFLICT.getCode(), "维修单状态不允许该操作，当前状态=" + repair.getStatus());
        }
    }

    private void requireRepairStatusIn(RepairOrder repair, List<Integer> expected) {
        if (repair.getStatus() == null || !expected.contains(repair.getStatus())) {
            throw new BusinessException(
                    ErrorCode.CONFLICT.getCode(), "维修单状态不允许该操作，当前状态=" + repair.getStatus());
        }
    }

    private void restoreStock(ReturnOrder returnOrder) {
        List<ReturnItem> items = returnItemMapper.selectList(new LambdaQueryWrapper<ReturnItem>()
                .eq(ReturnItem::getReturnId, returnOrder.getId())
                .eq(ReturnItem::getDeleted, 0));
        List<InventoryClient.Item> stockItems = items.stream()
                .map(i -> new InventoryClient.Item(i.getSkuId(), i.getQuantity()))
                .toList();
        if (!stockItems.isEmpty()) {
            bestEffort(() -> inventoryClient.restore(
                    new InventoryClient.StockRequest(returnOrder.getOrderNo(), stockItems)));
        }
    }

    private void completeOrderLinkage(ReturnOrder returnOrder) {
        bestEffort(() -> orderClient.notifyAfterSalesComplete(returnOrder.getOrderNo()));
    }

    private void restorePreviousOrderStatus(ReturnOrder returnOrder) {
        bestEffort(() -> orderClient.restoreAfterSalesStatus(
                returnOrder.getOrderNo(),
                new OrderClient.RestoreStatusRequest(returnOrder.getPreviousStatus())));
    }

    private void notifyMall(ReturnOrder returnOrder) {
        bestEffort(() -> mallCallbackNotifier.notifyAfterSaleStatus(
                returnOrder.getOrderNo(),
                returnOrder.getReturnNo(),
                returnOrder.getStatus(),
                "aftersale.updated"));
    }

    private OrderClient.OrderDetail getOrder(String orderNo) {
        try {
            Result<OrderClient.OrderDetail> result = orderClient.get(orderNo);
            if (result == null || !result.isSuccess()) {
                return null;
            }
            return result.data();
        } catch (Exception ex) {
            log.error("查询订单失败 orderNo={}", orderNo, ex);
            return null;
        }
    }

    private OrderClient.OrderDetail getOrderByExternalOrderNo(String externalOrderNo) {
        try {
            Result<OrderClient.OrderDetail> result = orderClient.getByExternalOrderNo(externalOrderNo);
            if (result == null || !result.isSuccess()) {
                return null;
            }
            return result.data();
        } catch (Exception ex) {
            log.error("按外部订单号查询订单失败 externalOrderNo={}", externalOrderNo, ex);
            return null;
        }
    }

    private void bestEffort(Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            log.warn("联动调用失败（已忽略）: {}", ex.getMessage());
        }
    }

    private String generateNo(String prefix) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return prefix + ts + ThreadLocalRandom.current().nextInt(10000, 100000);
    }

    private void appendRepairLog(Long repairId, String action, String content, String operatorName) {
        RepairLog repairLog = new RepairLog();
        repairLog.setRepairId(repairId);
        repairLog.setAction(action);
        repairLog.setContent(content);
        repairLog.setOperatorName(operatorName);
        repairLogMapper.insert(repairLog);
    }

    private ReturnOrderSummaryResponse toSummary(ReturnOrder order) {
        return new ReturnOrderSummaryResponse(
                order.getId(),
                order.getReturnNo(),
                order.getOrderNo(),
                order.getType(),
                order.getStatus(),
                order.getReason(),
                order.getTotalAmount(),
                order.getCreatedAt());
    }

    private ReturnOrderResponse toResponse(
            ReturnOrder order, List<ReturnItem> items, List<RefundRecord> refunds, List<RepairOrder> repairs) {
        return new ReturnOrderResponse(
                order.getId(),
                order.getReturnNo(),
                order.getOrderNo(),
                order.getType(),
                order.getStatus(),
                order.getReason(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                items.stream()
                        .map(i -> new ReturnItemResponse(i.getId(), i.getOrderItemId(), i.getSkuId(), i.getQuantity(), i.getUnitAmount()))
                        .toList(),
                refunds.stream()
                        .map(r -> new RefundRecordResponse(
                                r.getRefundNo(),
                                r.getPaymentNo(),
                                r.getAmount(),
                                r.getMethod(),
                                r.getStatus(),
                                r.getChannelTxnNo(),
                                r.getRefundedAt()))
                        .toList(),
                repairs.stream().map(this::repairResponse).toList());
    }

    private RepairResponse repairResponse(RepairOrder repair) {
        List<RepairLog> logs = repairLogMapper.selectList(new LambdaQueryWrapper<RepairLog>()
                .eq(RepairLog::getRepairId, repair.getId())
                .orderByAsc(RepairLog::getId));
        return new RepairResponse(
                repair.getId(),
                repair.getRepairNo(),
                repair.getReturnNo(),
                repair.getSkuId(),
                repair.getStatus(),
                repair.getFaultDesc(),
                repair.getRepairFee(),
                repair.getAssignedTo(),
                repair.getFinishedAt(),
                logs.stream()
                        .map(l -> new RepairLogResponse(l.getAction(), l.getContent(), l.getOperatorName(), l.getCreatedAt()))
                        .toList());
    }
}
