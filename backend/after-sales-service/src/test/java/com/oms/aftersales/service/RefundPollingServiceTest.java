package com.oms.aftersales.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oms.aftersales.dto.AfterSalesDtos.RefundRequest;
import com.oms.aftersales.entity.RefundRecord;
import com.oms.aftersales.entity.ReturnOrder;
import com.oms.aftersales.mapper.RefundRecordMapper;
import com.oms.aftersales.mapper.ReturnOrderMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class RefundPollingServiceTest {

    @Test
    void pendingRefundIsRequeriedWithTheSameRefundRecord() {
        RefundRecordMapper refundMapper = mock(RefundRecordMapper.class);
        ReturnOrderMapper returnMapper = mock(ReturnOrderMapper.class);
        AfterSalesService afterSalesService = mock(AfterSalesService.class);
        RefundPollingService pollingService =
                new RefundPollingService(refundMapper, returnMapper, afterSalesService);

        RefundRecord record = new RefundRecord();
        record.setReturnId(10L);
        record.setRefundNo("RF2026103112345600001");
        record.setPaymentNo("YP-1001");
        record.setAmount(new BigDecimal("10.00"));
        record.setMethod(1);
        ReturnOrder returnOrder = new ReturnOrder();
        returnOrder.setId(10L);
        returnOrder.setReturnNo("AS202610310001");
        returnOrder.setStatus(5);

        when(refundMapper.selectList(any())).thenReturn(List.of(record));
        when(returnMapper.selectById(10L)).thenReturn(returnOrder);

        pollingService.pollPendingRefunds();

        verify(afterSalesService).refund(
                eq("AS202610310001"),
                eq(new RefundRequest("YP-1001", new BigDecimal("10.00"), 1)),
                eq(null),
                eq("SYSTEM"));
    }
}
