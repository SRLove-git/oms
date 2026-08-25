package com.oms.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.ErrorCode;
import com.oms.common.core.result.PageResult;
import com.oms.payment.dto.BalanceDtos.BalanceResponse;
import com.oms.payment.dto.BalanceDtos.BalanceTransactionResponse;
import com.oms.payment.dto.BalanceDtos.RechargeRequest;
import com.oms.payment.entity.BalanceAccount;
import com.oms.payment.entity.BalanceTransaction;
import com.oms.payment.mapper.BalanceAccountMapper;
import com.oms.payment.mapper.BalanceTransactionMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BalanceService {

    private static final int TYPE_RECHARGE = 1;
    private static final int TYPE_PAY = 2;
    private static final int TYPE_REFUND = 3;

    private final BalanceAccountMapper accountMapper;
    private final BalanceTransactionMapper transactionMapper;

    public BalanceService(
            BalanceAccountMapper accountMapper, BalanceTransactionMapper transactionMapper) {
        this.accountMapper = accountMapper;
        this.transactionMapper = transactionMapper;
    }

    public BalanceResponse get(Long merchantId) {
        BalanceAccount account = findAccount(merchantId);
        return toResponse(account);
    }

    @Transactional
    public BalanceResponse recharge(RechargeRequest request) {
        Long merchantId = request.merchantId();
        BigDecimal amount = request.amount();
        if (merchantId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "商户 ID 不能为空");
        }
        requirePositive(amount);

        BalanceAccount account = lockAccount(merchantId);
        BigDecimal before = account.getAvailableAmount();
        BigDecimal after = before.add(amount);
        account.setAvailableAmount(after);
        accountMapper.updateById(account);
        insertTransaction(account, TYPE_RECHARGE, amount, before, after, request.remark());
        return toResponse(account);
    }

    @Transactional
    public String debit(Long merchantId, BigDecimal amount, String remark) {
        if (merchantId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "余额支付缺少商户");
        }
        requirePositive(amount);

        BalanceAccount account = lockAccount(merchantId);
        if (account.getAvailableAmount().compareTo(amount) < 0) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "账户余额不足");
        }
        BigDecimal before = account.getAvailableAmount();
        BigDecimal after = before.subtract(amount);
        account.setAvailableAmount(after);
        accountMapper.updateById(account);
        return insertTransaction(account, TYPE_PAY, amount, before, after, remark);
    }

    @Transactional
    public void credit(Long merchantId, BigDecimal amount, String remark) {
        if (merchantId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "退款入账缺少商户");
        }
        requirePositive(amount);

        BalanceAccount account = lockAccount(merchantId);
        BigDecimal before = account.getAvailableAmount();
        BigDecimal after = before.add(amount);
        account.setAvailableAmount(after);
        accountMapper.updateById(account);
        insertTransaction(account, TYPE_REFUND, amount, before, after, remark);
    }

    public PageResult<BalanceTransactionResponse> page(Long merchantId, int page, int size) {
        LambdaQueryWrapper<BalanceTransaction> wrapper = new LambdaQueryWrapper<BalanceTransaction>()
                .eq(merchantId != null, BalanceTransaction::getMerchantId, merchantId)
                .orderByDesc(BalanceTransaction::getId);
        Page<BalanceTransaction> result =
                transactionMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(
                result.getTotal(),
                result.getRecords().stream().map(this::toTransactionResponse).toList());
    }

    private BalanceAccount findAccount(Long merchantId) {
        if (merchantId == null) {
            return emptyAccount(merchantId);
        }
        BalanceAccount account = accountMapper.selectOne(new QueryWrapper<BalanceAccount>()
                .eq("merchant_id", merchantId)
                .eq("deleted", 0)
                .last("LIMIT 1"));
        return account == null ? emptyAccount(merchantId) : account;
    }

    private BalanceAccount lockAccount(Long merchantId) {
        BalanceAccount account = accountMapper.selectOne(new QueryWrapper<BalanceAccount>()
                .eq("merchant_id", merchantId)
                .eq("deleted", 0)
                .last("LIMIT 1 FOR UPDATE"));
        if (account == null) {
            account = new BalanceAccount();
            account.setMerchantId(merchantId);
            account.setAvailableAmount(BigDecimal.ZERO);
            account.setFrozenAmount(BigDecimal.ZERO);
            account.setVersion(0);
            account.setDeleted(0);
            accountMapper.insert(account);
        }
        return account;
    }

    private String insertTransaction(
            BalanceAccount account,
            int type,
            BigDecimal amount,
            BigDecimal before,
            BigDecimal after,
            String remark) {
        BalanceTransaction transaction = new BalanceTransaction();
        transaction.setMerchantId(account.getMerchantId());
        transaction.setAccountId(account.getId());
        transaction.setBizNo(generateNo("B"));
        transaction.setType(type);
        transaction.setAmount(amount);
        transaction.setBeforeAmount(before);
        transaction.setAfterAmount(after);
        transaction.setRemark(remark);
        transactionMapper.insert(transaction);
        return transaction.getBizNo();
    }

    private void requirePositive(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "金额必须大于 0");
        }
    }

    private String generateNo(String prefix) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return prefix + ts + ThreadLocalRandom.current().nextInt(10000, 100000);
    }

    private BalanceResponse toResponse(BalanceAccount account) {
        return new BalanceResponse(
                account.getMerchantId(), account.getAvailableAmount(), account.getFrozenAmount());
    }

    private BalanceAccount emptyAccount(Long merchantId) {
        BalanceAccount account = new BalanceAccount();
        account.setMerchantId(merchantId);
        account.setAvailableAmount(BigDecimal.ZERO);
        account.setFrozenAmount(BigDecimal.ZERO);
        return account;
    }

    private BalanceTransactionResponse toTransactionResponse(BalanceTransaction transaction) {
        return new BalanceTransactionResponse(
                transaction.getId(),
                transaction.getMerchantId(),
                transaction.getBizNo(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getBeforeAmount(),
                transaction.getAfterAmount(),
                transaction.getRemark(),
                transaction.getCreatedAt());
    }
}
