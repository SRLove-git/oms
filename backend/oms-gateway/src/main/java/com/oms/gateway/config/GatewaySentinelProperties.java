package com.oms.gateway.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 网关 Sentinel 规则配置。
 *
 * <pre>
 * oms:
 *   sentinel:
 *     enabled: true
 *     flow-qps:        # 资源（路由 id / API 分组名）→ 每秒阈值
 *       order-service: 300
 *     degrade-rt-ms:   # 资源 → 平均响应时间阈值（毫秒），超过触发降级
 *       order-service: 1000
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "oms.sentinel")
public class GatewaySentinelProperties {

    private boolean enabled = true;
    private Map<String, Double> flowQps = new HashMap<>();
    private Map<String, Double> degradeRtMs = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, Double> getFlowQps() {
        return flowQps;
    }

    public void setFlowQps(Map<String, Double> flowQps) {
        this.flowQps = flowQps;
    }

    public Map<String, Double> getDegradeRtMs() {
        return degradeRtMs;
    }

    public void setDegradeRtMs(Map<String, Double> degradeRtMs) {
        this.degradeRtMs = degradeRtMs;
    }
}
