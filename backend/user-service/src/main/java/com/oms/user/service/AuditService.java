package com.oms.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oms.common.core.result.PageResult;
import com.oms.user.dto.AuditLogDtos.AuditLogResponse;
import com.oms.user.entity.OperationLog;
import com.oms.user.mapper.OperationLogMapper;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final OperationLogMapper operationLogMapper;

    public AuditService(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    public void append(
            Long operatorId,
            String operatorName,
            String module,
            String action,
            String bizId,
            String beforeData,
            String afterData) {
        OperationLog log = new OperationLog();
        log.setOperatorId(operatorId);
        log.setOperatorName(operatorName);
        log.setModule(module);
        log.setAction(action);
        log.setBizId(bizId);
        log.setBeforeData(beforeData);
        log.setAfterData(afterData);
        operationLogMapper.insert(log);
    }

    public PageResult<AuditLogResponse> page(int page, int size) {
        Page<OperationLog> result = operationLogMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<OperationLog>().orderByDesc(OperationLog::getCreatedAt));
        return PageResult.of(
                result.getTotal(),
                result.getRecords().stream()
                        .map(this::toResponse)
                        .toList());
    }

    private AuditLogResponse toResponse(OperationLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getOperatorId(),
                log.getOperatorName(),
                log.getModule(),
                log.getAction(),
                log.getBizId(),
                log.getBeforeData(),
                log.getAfterData(),
                log.getCreatedAt());
    }
}
