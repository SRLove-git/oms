package com.oms.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.ErrorCode;
import com.oms.payment.dto.PaymentDtos.ExternalRefundRequest;
import com.oms.payment.dto.PaymentDtos.RefundOutcome;
import com.oms.payment.entity.PaymentTransaction;
import com.oms.payment.integration.yeahpay.YeahPayRefundClient;
import com.oms.payment.mapper.PaymentTransactionMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 将商城侧已支付流水登记到支付中心，并按 YeahPay 原交易发起原路退款。 */
@Service
public class ExternalPaymentRefundService {

    private final PaymentTransactionMapper transactionMapper;
    private final YeahPayRefundClient yeahPayRefundClient;

    public ExternalPaymentRefundService(
            PaymentTransactionMapper transactionMapper,
            YeahPayRefundClient yeahPayRefundClient) {
        this.transactionMapper = transactionMapper;
        this.yeahPayRefundClient = yeahPayRefundClient;
    }

    @Transactional
    public RefundOutcome refund(ExternalRefundRequest request) {
        validate(request);
        PaymentTransaction transaction = upsertPaidTransaction(request);
        if (transaction.getStatus() == 5) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "支付单已完成退款，不能重复退款");
        }
        if (request.amount().compareTo(transaction.getAmount()) != 0) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST.getCode(), "YeahPay 当前仅开放整单全额退款");
        }

        YeahPayRefundClient.RefundResult result = yeahPayRefundClient.refund(
                request.providerOrderNo(),
                transaction.getChannelTxnNo(),
                request.merchantRefundId());
        if (result.status() == 2) {
            transaction.setStatus(5);
            transactionMapper.updateById(transaction);
        }
        return new RefundOutcome(result.status(), result.providerRefundNo(), result.message());
    }

    public Map<String, Object> verifyConfiguration() {
        return yeahPayRefundClient.verifyConfiguration();
    }

    private PaymentTransaction upsertPaidTransaction(ExternalRefundRequest request) {
        PaymentTransaction existing = transactionMapper.selectOne(
                new LambdaQueryWrapper<PaymentTransaction>()
                        .eq(PaymentTransaction::getPaymentNo, request.paymentNo())
                        .eq(PaymentTransaction::getDeleted, 0)
                        .last("LIMIT 1"));
        if (existing != null) {
            verifySamePayment(existing, request);
            return existing;
        }

        PaymentTransaction created = new PaymentTransaction();
        created.setPaymentNo(request.paymentNo());
        created.setOrderNo(request.orderNo());
        created.setChannel("yeahpay");
        created.setAmount(request.amount());
        created.setCurrency(request.currency().toUpperCase(Locale.ROOT));
        created.setStatus(2);
        created.setChannelTxnNo(request.channelTxnNo());
        created.setIdempotencyKey(request.paymentNo());
        created.setNotifyCount(0);
        created.setPaidAt(LocalDateTime.now());
        try {
            transactionMapper.insert(created);
            return created;
        } catch (DuplicateKeyException ex) {
            PaymentTransaction concurrent = transactionMapper.selectOne(
                    new LambdaQueryWrapper<PaymentTransaction>()
                            .eq(PaymentTransaction::getPaymentNo, request.paymentNo())
                            .eq(PaymentTransaction::getDeleted, 0)
                            .last("LIMIT 1"));
            if (concurrent == null) {
                throw ex;
            }
            verifySamePayment(concurrent, request);
            return concurrent;
        }
    }

    private static void verifySamePayment(
            PaymentTransaction transaction, ExternalRefundRequest request) {
        if (!request.orderNo().equals(transaction.getOrderNo())
                || request.amount().compareTo(transaction.getAmount()) != 0
                || !request.currency().equalsIgnoreCase(transaction.getCurrency())
                || !request.channelTxnNo().equals(transaction.getChannelTxnNo())
                || transaction.getStatus() == null
                || (transaction.getStatus() != 2 && transaction.getStatus() != 5)) {
            throw new BusinessException(
                    ErrorCode.CONFLICT.getCode(), "支付单与已登记交易不一致，拒绝退款");
        }
    }

    private static void validate(ExternalRefundRequest request) {
        if (request == null
                || !hasText(request.paymentNo())
                || !hasText(request.orderNo())
                || request.amount() == null
                || request.amount().signum() <= 0
                || !hasText(request.currency())
                || !hasText(request.channelTxnNo())
                || !hasText(request.providerOrderNo())
                || !hasText(request.merchantRefundId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "商城退款参数不完整");
        }
        if (!hasText(request.channel())
                || !Set.of("yeahpay", "paynow", "wechat", "alipay")
                        .contains(request.channel().toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "原支付不是 YeahPay 交易");
        }
        if (!request.merchantRefundId().matches("[A-Za-z0-9]{16,64}")) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST.getCode(), "退款单号必须为 16-64 位字母或数字");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
