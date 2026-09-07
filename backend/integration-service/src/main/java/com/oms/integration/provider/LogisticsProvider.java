package com.oms.integration.provider;

import java.util.List;

/**
 * 物流轨迹真实查询抽象：配置真实承运商 API 后查询轨迹并回写本地。
 */
public interface LogisticsProvider {

    boolean isConfigured();

    List<String> query(String trackingNo, String carrier);
}
