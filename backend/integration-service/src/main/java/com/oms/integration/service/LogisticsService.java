package com.oms.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.ErrorCode;
import com.oms.integration.dto.IntegrationDtos.LogisticsCallbackRequest;
import com.oms.integration.dto.IntegrationDtos.LogisticsResponse;
import com.oms.integration.entity.LogisticsTracking;
import com.oms.integration.mapper.LogisticsTrackingMapper;
import com.oms.integration.provider.LogisticsProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LogisticsService {

    private static final Logger log = LoggerFactory.getLogger(LogisticsService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LogisticsTrackingMapper trackingMapper;
    private final LogisticsProvider logisticsProvider;

    public LogisticsService(
            LogisticsTrackingMapper trackingMapper,
            @Qualifier("routingLogisticsProvider") LogisticsProvider logisticsProvider) {
        this.trackingMapper = trackingMapper;
        this.logisticsProvider = logisticsProvider;
    }

    @Transactional
    public LogisticsResponse upsert(LogisticsCallbackRequest request) {
        if (request.trackingNo() == null || request.carrier() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "trackingNo 与 carrier 必填");
        }
        LogisticsTracking tracking = trackingMapper.selectOne(new LambdaQueryWrapper<LogisticsTracking>()
                .eq(LogisticsTracking::getCarrier, request.carrier())
                .eq(LogisticsTracking::getTrackingNo, request.trackingNo())
                .eq(LogisticsTracking::getDeleted, 0)
                .last("LIMIT 1"));
        if (tracking == null) {
            tracking = new LogisticsTracking();
            tracking.setOrderNo(request.orderNo());
            tracking.setTrackingNo(request.trackingNo());
            tracking.setCarrier(request.carrier());
            tracking.setStatus(request.status());
            tracking.setTraceInfo("[]");
            tracking.setPushed(0);
            trackingMapper.insert(tracking);
        } else {
            tracking.setStatus(request.status());
            if (hasText(request.orderNo()) && !hasText(tracking.getOrderNo())) {
                tracking.setOrderNo(request.orderNo());
            }
            tracking.setPushed(0);
            trackingMapper.updateById(tracking);
        }
        appendTrace(tracking, request.trace());
        trackingMapper.updateById(tracking);
        return toResponse(tracking);
    }

    public LogisticsResponse get(String trackingNo, String carrier) {
        LogisticsTracking tracking = trackingMapper.selectOne(new LambdaQueryWrapper<LogisticsTracking>()
                .eq(LogisticsTracking::getCarrier, carrier)
                .eq(LogisticsTracking::getTrackingNo, trackingNo)
                .eq(LogisticsTracking::getDeleted, 0)
                .last("LIMIT 1"));
        if (tracking == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "物流轨迹不存在");
        }
        syncFromProvider(tracking, trackingNo, carrier);
        return toResponse(tracking);
    }

    public LogisticsResponse getByOrderNo(String orderNo) {
        LogisticsTracking tracking = findTrackingByOrderNo(orderNo);
        if (tracking == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "该订单暂无物流轨迹");
        }
        syncFromProvider(tracking, tracking.getTrackingNo(), tracking.getCarrier());
        return toResponse(tracking);
    }

    /**
     * 按订单号查最新一条物流记录，没有则返回 null。建单幂等复用和订单取消联动使用。
     */
    public LogisticsResponse findLatestByOrderNo(String orderNo) {
        LogisticsTracking tracking = findTrackingByOrderNo(orderNo);
        return tracking == null ? null : toResponse(tracking);
    }

    public LogisticsResponse findLatestByTrackingNo(String trackingNo, String carrier) {
        if (!hasText(trackingNo) || !hasText(carrier)) {
            return null;
        }
        LogisticsTracking tracking = trackingMapper.selectOne(new LambdaQueryWrapper<LogisticsTracking>()
                .eq(LogisticsTracking::getCarrier, carrier)
                .eq(LogisticsTracking::getTrackingNo, trackingNo)
                .eq(LogisticsTracking::getDeleted, 0)
                .last("LIMIT 1"));
        return tracking == null ? null : toResponse(tracking);
    }

    private LogisticsTracking findTrackingByOrderNo(String orderNo) {
        if (!hasText(orderNo)) {
            return null;
        }
        return trackingMapper.selectOne(new LambdaQueryWrapper<LogisticsTracking>()
                .eq(LogisticsTracking::getOrderNo, orderNo)
                .eq(LogisticsTracking::getDeleted, 0)
                .orderByDesc(LogisticsTracking::getId)
                .last("LIMIT 1"));
    }

    private void syncFromProvider(LogisticsTracking tracking, String trackingNo, String carrier) {
        if (!logisticsProvider.isConfigured() || trackingNo == null || carrier == null) {
            return;
        }
        List<String> remoteTraces;
        try {
            remoteTraces = logisticsProvider.query(trackingNo, carrier);
        } catch (RuntimeException ex) {
            // 物流查询是读取增强，承运商短暂不可用或响应新增字段时仍应返回 OMS
            // 已保存的运单信息，不能让订单管理页退化成 500。
            log.warn(
                    "同步承运商轨迹失败，返回本地物流记录 trackingNo={} carrier={} error={}",
                    trackingNo,
                    carrier,
                    ex.getMessage());
            return;
        }
        if (remoteTraces == null || remoteTraces.isEmpty()) {
            return;
        }
        List<String> local = new ArrayList<>();
        try {
            local.addAll(MAPPER.readValue(
                    tracking.getTraceInfo() == null ? "[]" : tracking.getTraceInfo(),
                    new TypeReference<List<String>>() {
                    }));
        } catch (Exception ex) {
            log.warn("解析本地轨迹失败: {}", tracking.getTraceInfo());
        }
        for (String trace : remoteTraces) {
            if (!local.contains(trace)) {
                local.add(trace);
            }
        }
        try {
            tracking.setTraceInfo(MAPPER.writeValueAsString(local));
            trackingMapper.updateById(tracking);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private void appendTrace(LogisticsTracking tracking, String trace) {
        if (trace == null || trace.isBlank()) {
            return;
        }
        List<String> traces = new ArrayList<>();
        try {
            traces.addAll(MAPPER.readValue(tracking.getTraceInfo(), new TypeReference<List<String>>() {
            }));
        } catch (Exception ex) {
            log.warn("解析轨迹失败: {}", tracking.getTraceInfo());
        }
        if (!traces.contains(trace)) {
            traces.add(trace);
        }
        try {
            tracking.setTraceInfo(MAPPER.writeValueAsString(traces));
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private LogisticsResponse toResponse(LogisticsTracking tracking) {
        List<String> traces = new ArrayList<>();
        try {
            traces = MAPPER.readValue(tracking.getTraceInfo(), new TypeReference<List<String>>() {
            });
        } catch (Exception ex) {
            log.warn("解析轨迹失败: {}", tracking.getTraceInfo());
        }
        return new LogisticsResponse(
                tracking.getId(),
                tracking.getOrderNo(),
                tracking.getTrackingNo(),
                tracking.getCarrier(),
                tracking.getStatus(),
                traces,
                tracking.getUpdatedAt());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
