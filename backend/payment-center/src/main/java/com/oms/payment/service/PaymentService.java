package com.oms.payment.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.ErrorCode;
import com.oms.common.core.result.PageResult;
import com.oms.payment.adapter.PaymentAdapter;
import com.oms.payment.client.OrderClient;
import com.oms.payment.dto.PaymentDtos.CallbackRequest;
import com.oms.payment.dto.PaymentDtos.CreatePaymentRequest;
import com.oms.payment.dto.PaymentDtos.CreatePaymentResponse;
import com.oms.payment.dto.PaymentDtos.PaymentResponse;
import com.oms.payment.dto.PaymentDtos.RefundRequest;
import com.oms.payment.entity.PaymentNotifyLog;
import com.oms.payment.entity.PaymentTransaction;
import com.oms.payment.mapper.PaymentNotifyLogMapper;
import com.oms.payment.mapper.PaymentTransactionMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PaymentTransactionMapper transactionMapper;
    private final PaymentNotifyLogMapper notifyLogMapper;
    private final OrderClient orderClient;
    private final Map<String, PaymentAdapter> adapters;

    @Value("${oms.payment.mock-only:true}")
    private boolean mockOnly;

    @Value("${oms.payment.currency:SGD}")
    private String defaultCurrency;

    public PaymentService(
            PaymentTransactionMapper transactionMapper,
            PaymentNotifyLogMapper notifyLogMapper,
            OrderClient orderClient,
            List<PaymentAdapter> adapterList) {
        this.transactionMapper = transactionMapper;
        this.notifyLogMapper = notifyLogMapper;
        this.orderClient = orderClient;
        this.adapters = adapterList.stream()
                .collect(Collectors.toMap(PaymentAdapter::channel, Function.identity()));
    }

    @SentinelResource(value = "payment.create", blockHandler = "createBlocked")
    @Transactional
    public CreatePaymentResponse create(CreatePaymentRequest request) {
        String channel = request.channel() == null ? "mock" : request.channel();
        if (mockOnly && !"mock".equalsIgnoreCase(channel)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "当前为模拟支付模式，仅支持 mock 渠道");
        }
        PaymentAdapter adapter = adapters.get(channel);
        if (adapter == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "暂不支持该支付渠道");
        }

        // 幂等查询使用普通 QueryWrapper（字符串列名）：LambdaQueryWrapper 依赖
        // MyBatis-Plus 的 lambda 缓存初始化，无法在纯单元测试环境使用
        PaymentTransaction existing = transactionMapper.selectOne(new QueryWrapper<PaymentTransaction>()
                .eq("order_no", request.orderNo())
                .in("status", 1, 2)
                .eq("deleted", 0)
                .last("LIMIT 1"));
        if (existing != null) {
            if (existing.getStatus() != null && existing.getStatus() == 2) {
                throw new BusinessException(ErrorCode.CONFLICT.getCode(), "订单已支付，不能重复创建支付单");
            }
            return new CreatePaymentResponse(
                    existing.getPaymentNo(), existing.getChannel(), buildPayUrl(existing), existing.getAmount());
        }

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setPaymentNo(generatePaymentNo());
        transaction.setOrderNo(request.orderNo());
        transaction.setChannel(adapter.channel());
        transaction.setAmount(request.amount() == null ? BigDecimal.ZERO : request.amount());
        transaction.setCurrency(request.currency() == null ? defaultCurrency : request.currency());
        transaction.setStatus(1);
        transaction.setNotifyCount(0);
        transactionMapper.insert(transaction);

        return adapter.createPayment(request, transaction);
    }

    @SentinelResource(value = "payment.handleCallback", blockHandler = "handleCallbackBlocked")
    @Transactional
    public void handleCallback(String channel, CallbackRequest request) {
        PaymentTransaction transaction = transactionMapper.selectOne(new LambdaQueryWrapper<PaymentTransaction>()
                .eq(PaymentTransaction::getPaymentNo, request.paymentNo())
                .eq(PaymentTransaction::getDeleted, 0)
                .last("LIMIT 1"));
        if (transaction == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "支付单不存在");
        }
        if (transaction.getStatus() != null && transaction.getStatus() == 2) {
            log.info("支付回调重复，忽略 paymentNo={}", request.paymentNo());
            return;
        }

        PaymentAdapter adapter = adapters.get(channel);
        if (adapter == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "渠道回调校验器不存在");
        }
        try {
            adapter.verifyCallback(request, transaction);
        } catch (BusinessException ex) {
            recordNotifyLog(request, transaction, 0, ex.getMessage());
            throw ex;
        }

        transaction.setStatus(2);
        transaction.setChannelTxnNo(request.channelTxnNo());
        transaction.setPaidAt(LocalDateTime.now());
        transaction.setNotifyCount(transaction.getNotifyCount() + 1);
        transaction.setNotifyAt(LocalDateTime.now());
        transactionMapper.updateById(transaction);
        recordNotifyLog(request, transaction, 1, "SUCCESS");

        try {
            orderClient.notifyPaymentSuccess(new OrderClient.PaymentSuccessRequest(
                    transaction.getOrderNo(),
                    transaction.getPaymentNo(),
                    transaction.getChannel(),
                    transaction.getAmount(),
                    request.channelTxnNo()));
        } catch (Exception ex) {
            log.error("通知订单服务支付成功失败 paymentNo={}", transaction.getPaymentNo(), ex);
        }
    }

    public CreatePaymentResponse createBlocked(CreatePaymentRequest request, BlockException ex) {
        throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE.getCode(), "支付请求流量过大，请稍后重试");
    }

    /**
     * 渠道回调限流兜底：丢弃并记录，渠道侧按重试策略再次回调。
     */
    public void handleCallbackBlocked(String channel, CallbackRequest request, BlockException ex) {
        log.warn("支付回调被限流丢弃 channel={} paymentNo={}", channel, request.paymentNo());
    }

    @Transactional
    public void refund(String paymentNo, RefundRequest request) {
        PaymentTransaction transaction = transactionMapper.selectOne(new LambdaQueryWrapper<PaymentTransaction>()
                .eq(PaymentTransaction::getPaymentNo, paymentNo)
                .eq(PaymentTransaction::getDeleted, 0)
                .last("LIMIT 1"));
        if (transaction == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (transaction.getStatus() != 2) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "仅已支付订单可退款");
        }
        if (request.amount() != null
                && transaction.getAmount() != null
                && request.amount().compareTo(transaction.getAmount()) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "退款金额超过支付金额");
        }
        transaction.setStatus(5);
        transactionMapper.updateById(transaction);
    }

    public PageResult<PaymentResponse> page(String orderNo, Integer status, int page, int size) {
        LambdaQueryWrapper<PaymentTransaction> wrapper = new LambdaQueryWrapper<PaymentTransaction>()
                .eq(PaymentTransaction::getDeleted, 0)
                .orderByDesc(PaymentTransaction::getId);
        if (orderNo != null) {
            wrapper.eq(PaymentTransaction::getOrderNo, orderNo);
        }
        if (status != null) {
            wrapper.eq(PaymentTransaction::getStatus, status);
        }
        Page<PaymentTransaction> result = transactionMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(
                result.getTotal(),
                result.getRecords().stream().map(this::toResponse).toList());
    }

    private void recordNotifyLog(CallbackRequest request, PaymentTransaction transaction, int verify, String result) {
        PaymentNotifyLog notifyLog = new PaymentNotifyLog();
        notifyLog.setPaymentNo(transaction.getPaymentNo());
        notifyLog.setChannel(transaction.getChannel());
        notifyLog.setVerifyResult(verify);
        notifyLog.setHandleResult(result);
        try {
            notifyLog.setRequestBody(MAPPER.writeValueAsString(request));
        } catch (Exception ex) {
            notifyLog.setRequestBody(request.toString());
        }
        notifyLogMapper.insert(notifyLog);
    }

    private String buildPayUrl(PaymentTransaction transaction) {
        return "http://localhost:8085/api/v1/payment-callbacks/mock?paymentNo="
                + transaction.getPaymentNo()
                + "&amount="
                + transaction.getAmount();
    }

    private String generatePaymentNo() {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "P" + ts + ThreadLocalRandom.current().nextInt(10000, 100000);
    }

    private PaymentResponse toResponse(PaymentTransaction tx) {
        return new PaymentResponse(
                tx.getId(),
                tx.getPaymentNo(),
                tx.getOrderNo(),
                tx.getChannel(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getStatus(),
                tx.getChannelTxnNo(),
                tx.getCreatedAt(),
                tx.getPaidAt());
    }
}
