package com.oms.payment.config;

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
 * 支付中心流控与降级规则：资源名与 {@code PaymentService} 上 {@code @SentinelResource} 一致。
 */
@Configuration
@ConditionalOnProperty(prefix = "oms.sentinel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SentinelRulesConfig {

    private static final Logger log = LoggerFactory.getLogger(SentinelRulesConfig.class);

    public SentinelRulesConfig(
            @Value("${oms.sentinel.payment-create-qps:300}") double createQps,
            @Value("${oms.sentinel.payment-callback-qps:1000}") double callbackQps,
            @Value("${oms.sentinel.payment-create-rt-ms:800}") double createRtMs) {
        List<FlowRule> flowRules = List.of(
                new FlowRule("payment.create")
                        .setGrade(RuleConstant.FLOW_GRADE_QPS)
                        .setCount(createQps),
                new FlowRule("payment.handleCallback")
                        .setGrade(RuleConstant.FLOW_GRADE_QPS)
                        .setCount(callbackQps));
        FlowRuleManager.loadRules(flowRules);

        List<DegradeRule> degradeRules = List.of(
                new DegradeRule("payment.create")
                        .setGrade(RuleConstant.DEGRADE_GRADE_RT)
                        .setCount(createRtMs)
                        .setTimeWindow(10)
                        .setMinRequestAmount(5)
                        .setStatIntervalMs(10_000));
        DegradeRuleManager.loadRules(degradeRules);
        log.info("支付中心 Sentinel 规则已加载: flow={}", flowRules);
    }
}
