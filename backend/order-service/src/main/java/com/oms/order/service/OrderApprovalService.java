package com.oms.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.ErrorCode;
import com.oms.order.dto.OrderDtos.ApprovalRequest;
import com.oms.order.dto.OrderDtos.ApprovalResponse;
import com.oms.order.entity.Order;
import com.oms.order.entity.OrderApproval;
import com.oms.order.mapper.OrderApprovalMapper;
import com.oms.order.mapper.OrderMapper;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderApprovalService {

    private final OrderApprovalMapper approvalMapper;
    private final OrderMapper orderMapper;
    private final ObjectMapper objectMapper;

    public OrderApprovalService(
            OrderApprovalMapper approvalMapper, OrderMapper orderMapper, ObjectMapper objectMapper) {
        this.approvalMapper = approvalMapper;
        this.orderMapper = orderMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ApprovalResponse create(String orderNo, ApprovalRequest request, Long operatorId, String operatorName) {
        if (request.approvalType() == null || request.requestPayload() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "审批类型与申请内容不能为空");
        }
        OrderApproval approval = new OrderApproval();
        approval.setOrderNo(orderNo);
        approval.setApprovalType(request.approvalType());
        approval.setRequestPayload(request.requestPayload());
        approval.setStatus(0);
        approval.setOperatorId(operatorId);
        approval.setOperatorName(operatorName);
        approvalMapper.insert(approval);
        return toResponse(approval);
    }

    public List<ApprovalResponse> list(String orderNo) {
        return approvalMapper.selectList(new LambdaQueryWrapper<OrderApproval>()
                        .eq(OrderApproval::getOrderNo, orderNo).orderByDesc(OrderApproval::getId))
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public ApprovalResponse review(Long id, Integer status, String payload, String operatorName) {
        OrderApproval approval = approvalMapper.selectById(id);
        if (approval == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        approval.setStatus(status == null ? 1 : status);
        if (payload != null) {
            approval.setRequestPayload(payload);
        }
        approval.setOperatorName(operatorName);
        approvalMapper.updateById(approval);
        if (approval.getStatus() == 1) {
            apply(approval);
        }
        return toResponse(approval);
    }

    private void apply(OrderApproval approval) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, approval.getOrderNo()));
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        try {
            var node = objectMapper.readTree(approval.getRequestPayload());
            switch (approval.getApprovalType()) {
                case 1 -> {
                    BigDecimal price = node.has("totalAmount")
                            ? node.get("totalAmount").decimalValue() : null;
                    if (price != null) {
                        order.setTotalAmount(price);
                        order.setPayAmount(price);
                    }
                }
                case 2 -> {
                    if (node.has("address")) {
                        order.setAddress(node.get("address").asText());
                    }
                    if (node.has("phone")) {
                        order.setPhone(node.get("phone").asText());
                    }
                }
                case 3 -> {
                    // 物流信息写入订单备注，实际发货仍由发货接口执行
                    order.setRemark((order.getRemark() == null ? "" : order.getRemark() + " ")
                            + "审批物流：" + approval.getRequestPayload());
                }
                case 4 -> {
                    // 部分退款在售后服务中完成退款，审批通过后仅记录状态备注
                    order.setRemark((order.getRemark() == null ? "" : order.getRemark() + " ")
                            + "部分退款审批通过");
                }
                default -> throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "不支持的审批类型");
            }
            orderMapper.updateById(order);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "审批内容解析失败");
        }
    }

    private ApprovalResponse toResponse(OrderApproval approval) {
        return new ApprovalResponse(
                approval.getId(),
                approval.getOrderNo(),
                approval.getApprovalType(),
                approval.getRequestPayload(),
                approval.getStatus(),
                approval.getOperatorName(),
                approval.getCreatedAt());
    }
}
