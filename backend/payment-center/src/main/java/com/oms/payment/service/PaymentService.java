package com.oms.payment.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.ErrorCode;
import com.oms.common.core.result.PageResult;
import com.oms.common.core.result.Result;
import com.oms.payment.adapter.PaymentAdapter;
import com.oms.payment.client.OrderClient;
import com.oms.payment.client.OrderClient.OrderPaymentState;
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
import java.util.Set;
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
    private static final Set<String> SIMULATED_CHANNELS = Set.of("mock", "visa", "mastercard");

    private final PaymentTransactionMapper transactionMapper;
    private final PaymentNotifyLogMapper notifyLogMapper;
    private final OrderClient orderClient;
    private final BalanceService balanceService;
    private final Map<String, PaymentAdapter> adapters;

    @Value("${oms.payment.mock-only:true}")
    private boolean mockOnly;

    @Value("${oms.payment.currency:SGD}")
    private String defaultCurrency;

    public PaymentService(
            PaymentTransactionMapper transactionMapper,
            PaymentNotifyLogMapper notifyLogMapper,
            OrderClient orderClient,
            BalanceService balanceService,
            List<PaymentAdapter> adapterList) {
        this.transactionMapper = transactionMapper;
        this.notifyLogMapper = notifyLogMapper;
        this.orderClient = orderClient;
        this.balanceService = balanceService;
        this.adapters = adapterList.stream()
                .collect(Collectors.toMap(PaymentAdapter::channel, Function.identity()));
    }

    @SentinelResource(value = "payment.create", blockHandler = "createBlocked")
    @Transactional
    public CreatePaymentResponse create(CreatePaymentRequest request) {
        String channel = request.channel() == null ? "mock" : request.channel().toLowerCase();
        boolean balance = "balance".equals(channel);
        if (!balance && mockOnly && !SIMULATED_CHANNELS.contains(channel)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST.getCode(), "当前为模拟支付模式，仅支持 mock/visa/mastercard/balance 渠道");
        }

        OrderPaymentState state = loadPaymentState(request.orderNo());
        if (state.status() == null || state.status() != 1) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "订单状态不允许支付");
        }
        BigDecimal outstanding = state.payAmount().subtract(state.paidAmount());
        BigDecimal amount = request.amount() == null ? outstanding : request.amount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "支付金额必须大于 0");
        }
        if (amount.compareTo(outstanding) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "支付金额超过订单待支付金额");
        }

        if (balance) {
            return payByBalance(request, state, amount);
        }

        PaymentAdapter adapter = adapters.get(channel);
        if (adapter == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "暂不支持该支付渠道");
        }

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setPaymentNo(generatePaymentNo());
        transaction.setOrderNo(request.orderNo());
        transaction.setChannel(adapter.channel());
        transaction.setAmount(amount);
        transaction.setCurrency(request.currency() == null ? state.currency() : request.currency());
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

    private OrderPaymentState loadPaymentState(String orderNo) {
        Result<OrderPaymentState> result = orderClient.getPaymentState(orderNo);
        if (result == null || !result.isSuccess() || result.data() == null) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "订单支付状态不可用");
        }
        return result.data();
    }

    /**
     * 余额支付：直接扣减商户储值余额并生成已支付流水，不走外部渠道回调。
     */
    private CreatePaymentResponse payByBalance(
            CreatePaymentRequest request, OrderPaymentState state, BigDecimal amount) {
        String balanceBizNo = balanceService.debit(
                state.merchantId(), amount, "订单余额支付 " + request.orderNo());

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setPaymentNo(generatePaymentNo());
        transaction.setOrderNo(request.orderNo());
        transaction.setChannel("balance");
        transaction.setAmount(amount);
        transaction.setCurrency(request.currency() == null ? state.currency() : request.currency());
        transaction.setStatus(2);
        transaction.setChannelTxnNo(balanceBizNo);
        transaction.setNotifyCount(0);
        transaction.setPaidAt(LocalDateTime.now());
        transaction.setNotifyAt(LocalDateTime.now());
        transactionMapper.insert(transaction);

        try {
            orderClient.notifyPaymentSuccess(new OrderClient.PaymentSuccessRequest(
                    transaction.getOrderNo(),
                    transaction.getPaymentNo(),
                    transaction.getChannel(),
                    transaction.getAmount(),
                    balanceBizNo));
        } catch (Exception ex) {
            log.error("余额支付通知订单服务失败 orderNo={}", request.orderNo(), ex);
        }
        return new CreatePaymentResponse(
                transaction.getPaymentNo(), "balance", null, transaction.getAmount());
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
        BigDecimal refundAmount = request.amount() == null ? transaction.getAmount() : request.amount();
        if (transaction.getAmount() != null && refundAmount.compareTo(transaction.getAmount()) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "退款金额超过支付金额");
        }
        transaction.setStatus(5);
        transactionMapper.updateById(transaction);

        if ("balance".equals(transaction.getChannel())) {
            OrderPaymentState state = loadPaymentState(transaction.getOrderNo());
            balanceService.credit(state.merchantId(), refundAmount, "余额支付退款 " + paymentNo);
        }
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
