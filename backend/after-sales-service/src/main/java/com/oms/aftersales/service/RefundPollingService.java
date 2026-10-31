package com.oms.aftersales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oms.aftersales.dto.AfterSalesDtos.RefundRequest;
import com.oms.aftersales.entity.RefundRecord;
import com.oms.aftersales.entity.ReturnOrder;
import com.oms.aftersales.mapper.RefundRecordMapper;
import com.oms.aftersales.mapper.ReturnOrderMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 定时复查已发起但渠道尚未给出终态的退款，不会自动创建新的退款单。 */
@Service
public class RefundPollingService {

    private static final Logger log = LoggerFactory.getLogger(RefundPollingService.class);
    private static final int REFUND_PENDING = 2;
    private static final int RETURN_REFUNDING = 5;

    private final RefundRecordMapper refundRecordMapper;
    private final ReturnOrderMapper returnOrderMapper;
    private final AfterSalesService afterSalesService;

    public RefundPollingService(
            RefundRecordMapper refundRecordMapper,
            ReturnOrderMapper returnOrderMapper,
            AfterSalesService afterSalesService) {
        this.refundRecordMapper = refundRecordMapper;
        this.returnOrderMapper = returnOrderMapper;
        this.afterSalesService = afterSalesService;
    }

    @Scheduled(
            initialDelayString = "${oms.aftersales.refund-poll-initial-delay-ms:30000}",
            fixedDelayString = "${oms.aftersales.refund-poll-ms:60000}")
    public void pollPendingRefunds() {
        List<RefundRecord> pending = refundRecordMapper.selectList(
                new LambdaQueryWrapper<RefundRecord>()
                        .eq(RefundRecord::getStatus, REFUND_PENDING)
                        .eq(RefundRecord::getDeleted, 0)
                        .orderByAsc(RefundRecord::getId)
                        .last("LIMIT 100"));
        for (RefundRecord record : pending) {
            ReturnOrder returnOrder = returnOrderMapper.selectById(record.getReturnId());
            if (returnOrder == null || returnOrder.getStatus() != RETURN_REFUNDING) {
                continue;
            }
            try {
                afterSalesService.refund(
                        returnOrder.getReturnNo(),
                        new RefundRequest(record.getPaymentNo(), record.getAmount(), record.getMethod()),
                        null,
                        "SYSTEM");
            } catch (RuntimeException ex) {
                log.warn(
                        "自动复查退款失败，保留待查询状态 refundNo={} returnNo={}",
                        record.getRefundNo(),
                        returnOrder.getReturnNo(),
                        ex);
            }
        }
    }
}
