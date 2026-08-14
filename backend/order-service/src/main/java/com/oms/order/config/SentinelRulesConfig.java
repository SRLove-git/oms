package com.oms.order.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * 订单服务流控与降级规则：资源名与 {@code OrderService} 上 {@code @SentinelResource} 一致。
 * 阈值可通过配置覆盖（oms.sentinel.order-*），默认值面向本地/预发容量。
 */
@Configuration
@ConditionalOnProperty(prefix = "oms.sentinel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SentinelRulesConfig {

    private static final Logger log = LoggerFactory.getLogger(SentinelRulesConfig.class);

    public SentinelRulesConfig(
            @Value("${oms.sentinel.order-create-qps:300}") double createQps,
            @Value("${oms.sentinel.order-pay-qps:200}") double payQps,
            @Value("${oms.sentinel.order-callback-qps:1000}") double callbackQps,
            @Value("${oms.sentinel.order-create-rt-ms:1000}") double createRtMs) {
        List<FlowRule> flowRules = List.of(
                flow("order.create", createQps),
                flow("order.pay", payQps),
                flow("order.handlePaymentSuccess", callbackQps));
        FlowRuleManager.loadRules(flowRules);

        List<DegradeRule> degradeRules = List.of(
                new DegradeRule("order.create")
                        .setGrade(RuleConstant.DEGRADE_GRADE_RT)
                        .setCount(createRtMs)
                        .setTimeWindow(10)
                        .setMinRequestAmount(5)
                        .setStatIntervalMs(10_000));
        DegradeRuleManager.loadRules(degradeRules);
        log.info("订单服务 Sentinel 规则已加载: flow={}", flowRules);
    }

    private FlowRule flow(String resource, double qps) {
        return new FlowRule(resource)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(qps);
    }
}
