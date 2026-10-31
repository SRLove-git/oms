package com.oms.payment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oms.payment.dto.PaymentDtos.ExternalRefundRequest;
import com.oms.payment.entity.PaymentTransaction;
import com.oms.payment.integration.yeahpay.YeahPayRefundClient;
import com.oms.payment.mapper.PaymentTransactionMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExternalPaymentRefundServiceTest {

    private PaymentTransactionMapper mapper;
    private YeahPayRefundClient client;
    private ExternalPaymentRefundService service;

    @BeforeEach
    void setUp() {
        mapper = mock(PaymentTransactionMapper.class);
        client = mock(YeahPayRefundClient.class);
        service = new ExternalPaymentRefundService(mapper, client);
    }

    @Test
    void pendingProviderResultMustNotBeRecordedAsRefunded() {
        when(mapper.selectOne(any())).thenReturn(null);
        when(mapper.insert(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction transaction = invocation.getArgument(0);
            transaction.setId(10L);
            return 1;
        });
        when(client.refund(eq("D1001"), eq("YP-1001"),
                eq("RF2026103112345600001")))
                .thenReturn(new YeahPayRefundClient.RefundResult(3, "YR-1001", "退款处理中"));

        var result = service.refund(request());

        assertEquals(3, result.status());
        verify(mapper, never()).updateById(any(PaymentTransaction.class));
    }

    @Test
    void confirmedProviderSuccessMarksExternalPaymentRefunded() {
        PaymentTransaction transaction = transaction(2);
        when(mapper.selectOne(any())).thenReturn(transaction);
        when(client.refund(eq("D1001"), eq("YP-1001"),
                eq("RF2026103112345600001")))
                .thenReturn(new YeahPayRefundClient.RefundResult(2, "YR-DONE", "退款成功"));

        var result = service.refund(request());

        assertEquals(2, result.status());
        assertEquals(5, transaction.getStatus());
        verify(mapper).updateById(transaction);
    }

    private static ExternalRefundRequest request() {
        return new ExternalRefundRequest(
                "YP-1001",
                "ORDER202610310001",
                "paynow",
                new BigDecimal("10.00"),
                "SGD",
                "YP-1001",
                "D1001",
                "RF2026103112345600001");
    }

    private static PaymentTransaction transaction(int status) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setId(10L);
        transaction.setPaymentNo("YP-1001");
        transaction.setOrderNo("ORDER202610310001");
        transaction.setChannel("yeahpay");
        transaction.setAmount(new BigDecimal("10.00"));
        transaction.setCurrency("SGD");
        transaction.setStatus(status);
        transaction.setChannelTxnNo("YP-1001");
        return transaction;
    }
}
