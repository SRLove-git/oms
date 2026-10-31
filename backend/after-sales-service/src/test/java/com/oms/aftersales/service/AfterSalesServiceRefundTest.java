package com.oms.aftersales.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oms.aftersales.client.IntegrationClient;
import com.oms.aftersales.client.InventoryClient;
import com.oms.aftersales.client.MallCallbackNotifier;
import com.oms.aftersales.client.OrderClient;
import com.oms.aftersales.client.PaymentClient;
import com.oms.aftersales.dto.AfterSalesDtos.RefundRequest;
import com.oms.aftersales.entity.RefundRecord;
import com.oms.aftersales.entity.ReturnOrder;
import com.oms.aftersales.mapper.RefundRecordMapper;
import com.oms.aftersales.mapper.RepairLogMapper;
import com.oms.aftersales.mapper.RepairOrderMapper;
import com.oms.aftersales.mapper.ReturnItemMapper;
import com.oms.aftersales.mapper.ReturnOrderMapper;
import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.Result;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AfterSalesServiceRefundTest {

    private ReturnOrderMapper returnOrderMapper;
    private RefundRecordMapper refundRecordMapper;
    private OrderClient orderClient;
    private PaymentClient paymentClient;
    private AfterSalesService service;

    @BeforeEach
    void setUp() {
        returnOrderMapper = mock(ReturnOrderMapper.class);
        refundRecordMapper = mock(RefundRecordMapper.class);
        orderClient = mock(OrderClient.class);
        paymentClient = mock(PaymentClient.class);
        service = new AfterSalesService(
                returnOrderMapper,
                mock(ReturnItemMapper.class),
                refundRecordMapper,
                mock(RepairOrderMapper.class),
                mock(RepairLogMapper.class),
                orderClient,
                paymentClient,
                mock(InventoryClient.class),
                mock(IntegrationClient.class),
                mock(MallCallbackNotifier.class));
        ReflectionTestUtils.setField(service, "defaultCurrency", "SGD");
        ReflectionTestUtils.setField(service, "internalServiceSecret", "internal-test");
    }

    @Test
    void pendingYeahPayRefundMustNotCompleteAfterSalesOrder() {
        ReturnOrder returnOrder = refundingReturnOrder();
        when(returnOrderMapper.selectByReturnNoForUpdate("AS202610310001")).thenReturn(returnOrder);
        when(refundRecordMapper.selectOne(any())).thenReturn(null);
        when(refundRecordMapper.insert(any(RefundRecord.class))).thenAnswer(invocation -> {
            RefundRecord record = invocation.getArgument(0);
            record.setId(20L);
            return 1;
        });
        when(orderClient.successfulPayment("internal-test", "ORDER202610310001"))
                .thenReturn(Result.ok(new OrderClient.SuccessfulPayment(
                        "YP-1001", "paynow", new BigDecimal("10.00"), "SGD", "YP-1001", "D1001")));
        when(paymentClient.refundExternal(eq("internal-test"), any()))
                .thenReturn(Result.ok(new PaymentClient.RefundOutcome(3, "YR-1001", "退款处理中")));

        var result = service.refund(
                "AS202610310001", new RefundRequest("", new BigDecimal("10.00"), 1), 1L, "admin");

        assertEquals(3, result.status());
        assertEquals(5, returnOrder.getStatus());
        verify(returnOrderMapper, never()).updateById(returnOrder);
    }

    @Test
    void unknownProviderResultMustKeepStableRefundRecordPending() {
        ReturnOrder returnOrder = refundingReturnOrder();
        when(returnOrderMapper.selectByReturnNoForUpdate("AS202610310001")).thenReturn(returnOrder);
        when(refundRecordMapper.selectOne(any())).thenReturn(null);
        when(refundRecordMapper.insert(any(RefundRecord.class))).thenAnswer(invocation -> {
            RefundRecord record = invocation.getArgument(0);
            record.setId(20L);
            return 1;
        });
        when(orderClient.successfulPayment("internal-test", "ORDER202610310001"))
                .thenReturn(Result.ok(new OrderClient.SuccessfulPayment(
                        "YP-1001", "paynow", new BigDecimal("10.00"), "SGD", "YP-1001", "D1001")));
        when(paymentClient.refundExternal(eq("internal-test"), any()))
                .thenThrow(new IllegalStateException("provider response lost"));

        var result = service.refund(
                "AS202610310001", new RefundRequest("", null, 1), 1L, "admin");

        assertEquals(3, result.status());
        assertEquals(5, returnOrder.getStatus());
        verify(refundRecordMapper).insert(any(RefundRecord.class));
        verify(returnOrderMapper, never()).updateById(returnOrder);
    }

    @Test
    void nonYeahPayPaymentMustBeRejectedBeforeCreatingRefundRecord() {
        when(returnOrderMapper.selectByReturnNoForUpdate("AS202610310001"))
                .thenReturn(refundingReturnOrder());
        when(refundRecordMapper.selectOne(any())).thenReturn(null);
        when(orderClient.successfulPayment("internal-test", "ORDER202610310001"))
                .thenReturn(Result.ok(new OrderClient.SuccessfulPayment(
                        "PI-1001", "stripe", new BigDecimal("10.00"), "SGD", "PI-1001", "ORDER1001")));

        assertThrows(BusinessException.class, () -> service.refund(
                "AS202610310001", new RefundRequest("", null, 1), 1L, "admin"));

        verify(refundRecordMapper, never()).insert(any(RefundRecord.class));
        verify(paymentClient, never()).refundExternal(any(), any());
    }

    private static ReturnOrder refundingReturnOrder() {
        ReturnOrder order = new ReturnOrder();
        order.setId(1L);
        order.setReturnNo("AS202610310001");
        order.setOrderId(2L);
        order.setOrderNo("ORDER202610310001");
        order.setMerchantId(3L);
        order.setType(1);
        order.setStatus(5);
        order.setTotalAmount(new BigDecimal("10.00"));
        return order;
    }
}
