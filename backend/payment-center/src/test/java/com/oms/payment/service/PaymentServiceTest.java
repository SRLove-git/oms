package com.oms.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.Result;
import com.oms.payment.adapter.MockPaymentAdapter;
import com.oms.payment.adapter.MastercardPaymentAdapter;
import com.oms.payment.adapter.VisaPaymentAdapter;
import com.oms.payment.client.OrderClient;
import com.oms.payment.client.OrderClient.OrderPaymentState;
import com.oms.payment.dto.PaymentDtos.CallbackRequest;
import com.oms.payment.dto.PaymentDtos.CreatePaymentRequest;
import com.oms.payment.dto.PaymentDtos.CreatePaymentResponse;
import com.oms.payment.dto.PaymentDtos.RefundRequest;
import com.oms.payment.entity.PaymentNotifyLog;
import com.oms.payment.entity.PaymentTransaction;
import com.oms.payment.mapper.PaymentNotifyLogMapper;
import com.oms.payment.mapper.PaymentTransactionMapper;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PaymentServiceTest {

    private PaymentTransactionMapper transactionMapper;
    private PaymentNotifyLogMapper notifyLogMapper;
    private OrderClient orderClient;
    private BalanceService balanceService;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() throws Exception {
        transactionMapper = mock(PaymentTransactionMapper.class);
        notifyLogMapper = mock(PaymentNotifyLogMapper.class);
        orderClient = mock(OrderClient.class);
        balanceService = mock(BalanceService.class);
        paymentService =
                new PaymentService(
                        transactionMapper,
                        notifyLogMapper,
                        orderClient,
                        balanceService,
                        List.of(new MockPaymentAdapter(), new VisaPaymentAdapter(), new MastercardPaymentAdapter()));
        setField(paymentService, "mockOnly", true);
    }

    // ---------- 创建支付 ----------

    @Test
    void mockChannelShouldCreatePayment() {
        stubPaymentState(BigDecimal.ZERO);
        when(transactionMapper.insert(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            ((PaymentTransaction) invocation.getArgument(0)).setId(1L);
            return 1;
        });

        CreatePaymentResponse response = paymentService.create(
                new CreatePaymentRequest("O001", new BigDecimal("100.00"), "SGD", "mock", 1L));

        assertThat(response.paymentNo()).isNotBlank();
        assertThat(response.channel()).isEqualTo("mock");
        assertThat(response.amount()).isEqualByComparingTo("100.00");
        verify(transactionMapper).insert(any(PaymentTransaction.class));
    }

    @Test
    void createShouldRejectNonMockChannelInMockOnlyMode() {
        assertThatThrownBy(() -> paymentService.create(
                        new CreatePaymentRequest("O001", new BigDecimal("100.00"), "SGD", "wechat", 1L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅支持 mock/visa/mastercard/balance 渠道");
        verify(transactionMapper, never()).insert(any(PaymentTransaction.class));
    }

    @Test
    void createShouldDefaultNullChannelToMock() {
        stubPaymentState(BigDecimal.ZERO);
        when(transactionMapper.insert(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            ((PaymentTransaction) invocation.getArgument(0)).setId(1L);
            return 1;
        });

        CreatePaymentResponse response = paymentService.create(
                new CreatePaymentRequest("O001", new BigDecimal("100.00"), "SGD", null, 1L));

        assertThat(response.channel()).isEqualTo("mock");
        verify(transactionMapper).insert(any(PaymentTransaction.class));
    }

    @Test
    void createShouldRejectUnsupportedChannelWhenMockOnlyDisabled() throws Exception {
        setField(paymentService, "mockOnly", false);
        stubPaymentState(BigDecimal.ZERO);

        assertThatThrownBy(() -> paymentService.create(
                        new CreatePaymentRequest("O001", new BigDecimal("100.00"), "SGD", "unknown", 1L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("暂不支持该支付渠道");
        verify(transactionMapper, never()).insert(any(PaymentTransaction.class));
    }

    @Test
    void createShouldRejectWhenOrderAlreadyPaid() {
        stubPaymentState(BigDecimal.ZERO, 2);

        assertThatThrownBy(() -> paymentService.create(
                        new CreatePaymentRequest("O001", new BigDecimal("100.00"), "SGD", "mock", 1L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("订单状态不允许支付");
        verify(transactionMapper, never()).insert(any(PaymentTransaction.class));
    }

    @Test
    void createShouldRejectAmountExceedingOutstanding() {
        stubPaymentState(new BigDecimal("60.00"));

        assertThatThrownBy(() -> paymentService.create(
                        new CreatePaymentRequest("O001", new BigDecimal("50.00"), "SGD", "mock", 1L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超过订单待支付金额");
        verify(transactionMapper, never()).insert(any(PaymentTransaction.class));
    }

    @Test
    void createShouldAllowPartialPayment() {
        stubPaymentState(new BigDecimal("60.00"));
        when(transactionMapper.insert(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            ((PaymentTransaction) invocation.getArgument(0)).setId(1L);
            return 1;
        });

        CreatePaymentResponse response = paymentService.create(
                new CreatePaymentRequest("O001", new BigDecimal("30.00"), "SGD", "visa", 1L));

        assertThat(response.channel()).isEqualTo("visa");
        assertThat(response.amount()).isEqualByComparingTo("30.00");
        verify(transactionMapper).insert(any(PaymentTransaction.class));
    }

    @Test
    void createShouldPayByBalance() {
        stubPaymentState(BigDecimal.ZERO);
        when(balanceService.debit(any(), any(), any())).thenReturn("B0001");
        when(transactionMapper.insert(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            ((PaymentTransaction) invocation.getArgument(0)).setId(1L);
            return 1;
        });

        CreatePaymentResponse response = paymentService.create(
                new CreatePaymentRequest("O001", new BigDecimal("100.00"), "SGD", "balance", 1L));

        assertThat(response.channel()).isEqualTo("balance");
        assertThat(response.payUrl()).isNull();
        verify(balanceService).debit(1L, new BigDecimal("100.00"), "订单余额支付 O001");
        verify(orderClient).notifyPaymentSuccess(any());
    }

    // ---------- 回调处理 ----------

    @Test
    void callbackShouldNotifyOrderOnce() {
        PaymentTransaction transaction = transaction(1);
        when(transactionMapper.selectOne(any(Wrapper.class))).thenReturn(transaction);
        when(orderClient.notifyPaymentSuccess(any())).thenReturn(null);

        CallbackRequest callback =
                new CallbackRequest("P001", "TXN1", new BigDecimal("100.00"), "SUCCESS");
        paymentService.handleCallback("mock", callback);
        transaction.setStatus(2);
        paymentService.handleCallback("mock", callback);

        verify(orderClient, times(1)).notifyPaymentSuccess(any());
    }

    @Test
    void callbackShouldRejectAmountMismatch() {
        PaymentTransaction transaction = transaction(1);
        when(transactionMapper.selectOne(any(Wrapper.class))).thenReturn(transaction);

        assertThatThrownBy(() -> paymentService.handleCallback(
                        "mock", new CallbackRequest("P001", "TXN1", new BigDecimal("99.00"), "SUCCESS")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void callbackShouldRejectUnknownPayment() {
        when(transactionMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> paymentService.handleCallback(
                        "mock", new CallbackRequest("NOPE", "TXN1", new BigDecimal("100.00"), "SUCCESS")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("支付单不存在");
    }

    @Test
    void callbackShouldRejectNonSuccessStatusAndRecordVerifyFailure() {
        PaymentTransaction transaction = transaction(1);
        when(transactionMapper.selectOne(any(Wrapper.class))).thenReturn(transaction);

        assertThatThrownBy(() -> paymentService.handleCallback(
                        "mock", new CallbackRequest("P001", "TXN1", new BigDecimal("100.00"), "FAILED")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("支付状态非成功");

        verify(transactionMapper, never()).updateById(any(PaymentTransaction.class));
        verify(orderClient, never()).notifyPaymentSuccess(any());
        ArgumentCaptor<PaymentNotifyLog> logCaptor = ArgumentCaptor.forClass(PaymentNotifyLog.class);
        verify(notifyLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getVerifyResult()).isZero();
        assertThat(logCaptor.getValue().getHandleResult()).contains("支付状态非成功");
        assertThat(logCaptor.getValue().getPaymentNo()).isEqualTo("P001");
    }

    @Test
    void callbackDuplicateShouldNotUpdateOrNotifyAgain() {
        PaymentTransaction transaction = transaction(1);
        when(transactionMapper.selectOne(any(Wrapper.class))).thenReturn(transaction);
        when(orderClient.notifyPaymentSuccess(any())).thenReturn(null);

        CallbackRequest callback =
                new CallbackRequest("P001", "TXN1", new BigDecimal("100.00"), "SUCCESS");
        paymentService.handleCallback("mock", callback);
        paymentService.handleCallback("mock", callback);

        verify(transactionMapper, times(1)).updateById(transaction);
        verify(notifyLogMapper, times(1)).insert(any(PaymentNotifyLog.class));
        verify(orderClient, times(1)).notifyPaymentSuccess(any());
        assertThat(transaction.getStatus()).isEqualTo(2);
        assertThat(transaction.getChannelTxnNo()).isEqualTo("TXN1");
        assertThat(transaction.getNotifyCount()).isEqualTo(1);
    }

    @Test
    void callbackShouldIgnoreOrderNotifyFailure() {
        PaymentTransaction transaction = transaction(1);
        when(transactionMapper.selectOne(any(Wrapper.class))).thenReturn(transaction);
        when(orderClient.notifyPaymentSuccess(any())).thenThrow(new RuntimeException("order down"));

        org.assertj.core.api.Assertions.assertThatCode(() -> paymentService.handleCallback(
                        "mock", new CallbackRequest("P001", "TXN1", new BigDecimal("100.00"), "SUCCESS")))
                .doesNotThrowAnyException();
        assertThat(transaction.getStatus()).isEqualTo(2);
        verify(transactionMapper).updateById(transaction);
    }

    // ---------- 退款 ----------

    @Test
    void refundShouldRejectUnknownPayment() {
        when(transactionMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() ->
                        paymentService.refund("NOPE", new RefundRequest(new BigDecimal("10.00"), 1)))
                .isInstanceOf(BusinessException.class);
        verify(transactionMapper, never()).updateById(any(PaymentTransaction.class));
    }

    @Test
    void refundShouldRejectWhenNotPaid() {
        when(transactionMapper.selectOne(any(Wrapper.class))).thenReturn(transaction(1));

        assertThatThrownBy(() ->
                        paymentService.refund("P001", new RefundRequest(new BigDecimal("10.00"), 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅已支付订单可退款");
        verify(transactionMapper, never()).updateById(any(PaymentTransaction.class));
    }

    @Test
    void refundShouldMarkRefunded() {
        PaymentTransaction transaction = transaction(2);
        when(transactionMapper.selectOne(any(Wrapper.class))).thenReturn(transaction);

        paymentService.refund("P001", new RefundRequest(new BigDecimal("10.00"), 1));

        assertThat(transaction.getStatus()).isEqualTo(5);
        verify(transactionMapper).updateById(transaction);
    }

    @Test
    void refundShouldRejectAmountExceedingPayment() {
        PaymentTransaction transaction = transaction(2);
        when(transactionMapper.selectOne(any(Wrapper.class))).thenReturn(transaction);

        assertThatThrownBy(() ->
                        paymentService.refund("P001", new RefundRequest(new BigDecimal("100.01"), 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超过支付金额");
        verify(transactionMapper, never()).updateById(any(PaymentTransaction.class));
    }

    private void stubPaymentState(BigDecimal paidAmount) {
        stubPaymentState(paidAmount, 1);
    }

    private void stubPaymentState(BigDecimal paidAmount, int status) {
        when(orderClient.getPaymentState(any()))
                .thenReturn(Result.ok(new OrderPaymentState(
                        "O001", 1L, new BigDecimal("100.00"), "SGD", paidAmount, status)));
    }

    private PaymentTransaction transaction(int status) {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setId(1L);
        tx.setPaymentNo("P001");
        tx.setOrderNo("O001");
        tx.setChannel("mock");
        tx.setAmount(new BigDecimal("100.00"));
        tx.setCurrency("SGD");
        tx.setStatus(status);
        tx.setNotifyCount(0);
        return tx;
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
