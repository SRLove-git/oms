package com.oms.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.ErrorCode;
import com.oms.common.core.result.PageResult;
import com.oms.payment.dto.ReconciliationDtos.DiffItem;
import com.oms.payment.dto.ReconciliationDtos.ReconciliationResponse;
import com.oms.payment.entity.PaymentTransaction;
import com.oms.payment.entity.ReconciliationRecord;
import com.oms.payment.mapper.PaymentTransactionMapper;
import com.oms.payment.mapper.ReconciliationRecordMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PaymentTransactionMapper transactionMapper;
    private final ReconciliationRecordMapper recordMapper;

    public ReconciliationService(
            PaymentTransactionMapper transactionMapper, ReconciliationRecordMapper recordMapper) {
        this.transactionMapper = transactionMapper;
        this.recordMapper = recordMapper;
    }

    @Transactional
    public ReconciliationResponse run(LocalDate bizDate, String channel, boolean simulateDiff) {
        LocalDate date = bizDate == null ? LocalDate.now() : bizDate;
        String ch = channel == null ? "mock" : channel;
        List<PaymentTransaction> local = transactionMapper.selectList(new LambdaQueryWrapper<PaymentTransaction>()
                .eq(PaymentTransaction::getChannel, ch)
                .in(PaymentTransaction::getStatus, List.of(2, 5))
                .ge(PaymentTransaction::getPaidAt, date.atStartOfDay())
                .lt(PaymentTransaction::getPaidAt, date.plusDays(1).atStartOfDay())
                .eq(PaymentTransaction::getDeleted, 0));

        // 模拟渠道账单：默认与本地一致；simulateDiff=true 时丢弃一笔或篡改金额
        List<PaymentTransaction> channelBill = new ArrayList<>(local);
        if (simulateDiff && !channelBill.isEmpty()) {
            PaymentTransaction dropped = channelBill.remove(channelBill.size() - 1);
            log.info("模拟对账差异：渠道账单缺失本地流水 paymentNo={}", dropped.getPaymentNo());
            if (channelBill.size() >= 1) {
                PaymentTransaction original = channelBill.get(0);
                PaymentTransaction altered = new PaymentTransaction();
                altered.setPaymentNo(original.getPaymentNo() + "-CH");
                altered.setOrderNo("CH-" + original.getOrderNo());
                altered.setAmount(original.getAmount().add(BigDecimal.ONE));
                altered.setStatus(2);
                channelBill.add(altered);
            }
        }

        Map<String, PaymentTransaction> localByNo = new HashMap<>();
        local.forEach(t -> localByNo.put(t.getPaymentNo(), t));
        Map<String, PaymentTransaction> channelByNo = new HashMap<>();
        channelBill.forEach(t -> channelByNo.put(t.getPaymentNo(), t));

        List<DiffItem> diffs = new ArrayList<>();
        for (PaymentTransaction localTxn : local) {
            PaymentTransaction channelTxn = channelByNo.get(localTxn.getPaymentNo());
            if (channelTxn == null) {
                diffs.add(new DiffItem(
                        localTxn.getPaymentNo(),
                        localTxn.getOrderNo(),
                        BigDecimal.ZERO,
                        localTxn.getAmount(),
                        "LOCAL_ONLY"));
            } else if (channelTxn.getAmount().compareTo(localTxn.getAmount()) != 0) {
                diffs.add(new DiffItem(
                        localTxn.getPaymentNo(),
                        localTxn.getOrderNo(),
                        channelTxn.getAmount(),
                        localTxn.getAmount(),
                        "AMOUNT_MISMATCH"));
            }
        }
        for (PaymentTransaction channelTxn : channelBill) {
            if (!localByNo.containsKey(channelTxn.getPaymentNo())) {
                diffs.add(new DiffItem(
                        channelTxn.getPaymentNo(),
                        channelTxn.getOrderNo(),
                        channelTxn.getAmount(),
                        BigDecimal.ZERO,
                        "CHANNEL_ONLY"));
            }
        }

        BigDecimal channelAmount = channelBill.stream()
                .map(PaymentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal localAmount = local.stream()
                .map(PaymentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        ReconciliationRecord record = recordMapper.selectOne(new LambdaQueryWrapper<ReconciliationRecord>()
                .eq(ReconciliationRecord::getBizDate, date)
                .eq(ReconciliationRecord::getChannel, ch)
                .last("LIMIT 1"));
        if (record == null) {
            record = new ReconciliationRecord();
            record.setBizDate(date);
            record.setChannel(ch);
        }
        record.setChannelAmount(channelAmount);
        record.setLocalAmount(localAmount);
        record.setDiffCount(diffs.size());
        record.setStatus(diffs.isEmpty() ? 3 : 1);
        record.setHandledAt(diffs.isEmpty() ? LocalDateTime.now() : null);
        record.setDetailJson(toJson(diffs));
        if (record.getId() == null) {
            recordMapper.insert(record);
        } else {
            recordMapper.updateById(record);
        }
        return toResponse(record, diffs);
    }

    public PageResult<ReconciliationResponse> page(
            LocalDate bizDate, String channel, Integer status, int page, int size) {
        LambdaQueryWrapper<ReconciliationRecord> wrapper = new LambdaQueryWrapper<ReconciliationRecord>()
                .orderByDesc(ReconciliationRecord::getBizDate)
                .orderByDesc(ReconciliationRecord::getId);
        if (bizDate != null) {
            wrapper.eq(ReconciliationRecord::getBizDate, bizDate);
        }
        if (channel != null) {
            wrapper.eq(ReconciliationRecord::getChannel, channel);
        }
        if (status != null) {
            wrapper.eq(ReconciliationRecord::getStatus, status);
        }
        Page<ReconciliationRecord> result = recordMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(
                result.getTotal(), result.getRecords().stream().map(r -> toResponse(r, parseDiffs(r))).toList());
    }

    @Transactional
    public void handle(Long id) {
        ReconciliationRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (record.getStatus() == 3) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "该对账日无差异，无需处理");
        }
        record.setStatus(2);
        record.setHandledAt(LocalDateTime.now());
        recordMapper.updateById(record);
    }

    private String toJson(List<DiffItem> diffs) {
        try {
            return MAPPER.writeValueAsString(diffs);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private List<DiffItem> parseDiffs(ReconciliationRecord record) {
        if (record.getDetailJson() == null || record.getDetailJson().isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(record.getDetailJson(), new TypeReference<List<DiffItem>>() {
            });
        } catch (Exception ex) {
            log.warn("解析对账明细失败: {}", record.getDetailJson());
            return List.of();
        }
    }

    private ReconciliationResponse toResponse(ReconciliationRecord record, List<DiffItem> diffs) {
        return new ReconciliationResponse(
                record.getId(),
                record.getBizDate(),
                record.getChannel(),
                record.getChannelAmount(),
                record.getLocalAmount(),
                record.getDiffCount(),
                record.getStatus(),
                diffs,
                record.getHandledAt(),
                record.getCreatedAt());
    }
}
