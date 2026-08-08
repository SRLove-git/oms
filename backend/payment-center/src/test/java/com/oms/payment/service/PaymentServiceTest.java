package com.oms.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.oms.common.core.exception.BusinessException;
import com.oms.payment.adapter.MockPaymentAdapter;
import com.oms.payment.client.OrderClient;
import com.oms.payment.dto.PaymentDtos.CallbackRequest;
import com.oms.payment.dto.PaymentDtos.CreatePaymentRequest;
import com.oms.payment.dto.PaymentDtos.CreatePaymentResponse;
import com.oms.payment.entity.PaymentTransaction;
import com.oms.payment.mapper.PaymentNotifyLogMapper;
import com.oms.payment.mapper.PaymentTransactionMapper;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentServiceTest {

    private PaymentTransactionMapper transactionMapper;
    private PaymentNotifyLogMapper notifyLogMapper;
    private OrderClient orderClient;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() throws Exception {
        transactionMapper = mock(PaymentTransactionMapper.class);
        notifyLogMapper = mock(PaymentNotifyLogMapper.class);
        orderClient = mock(OrderClient.class);
        paymentService =
                new PaymentService(transactionMapper, notifyLogMapper, orderClient, List.of(new MockPaymentAdapter()));
        setField(paymentService, "mockOnly", true);
    }

    @Test
    void mockChannelShouldCreatePayment() {
        when(transactionMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(transactionMapper.insert(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            ((PaymentTransaction) invocation.getArgument(0)).setId(1L);
            return 1;
        });

        CreatePaymentResponse response = paymentService.create(
                new CreatePaymentRequest("O001", new BigDecimal("100.00"), "CNY", "mock", 1L));

        assertThat(response.paymentNo()).isNotBlank();
        assertThat(response.channel()).isEqualTo("mock");
    }

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

    private PaymentTransaction transaction(int status) {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setId(1L);
        tx.setPaymentNo("P001");
        tx.setOrderNo("O001");
        tx.setChannel("mock");
        tx.setAmount(new BigDecimal("100.00"));
        tx.setCurrency("CNY");
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
