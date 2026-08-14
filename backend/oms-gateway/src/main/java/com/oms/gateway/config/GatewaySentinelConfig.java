package com.oms.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiDefinition;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPathPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.GatewayApiDefinitionManager;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter;
import com.alibaba.csp.sentinel.adapter.gateway.sc.exception.SentinelGatewayBlockExceptionHandler;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.result.view.ViewResolver;
import org.springframework.web.server.WebExceptionHandler;

/**
 * 网关流控与降级规则加载（启动时写入 Sentinel，无需依赖控制台）。
 *
 * <p>支付回调等外部入口单独划分 API 分组限流，避免渠道回调高峰冲击业务路由。
 * sentinel-spring-cloud-gateway-adapter 未提供 Spring Boot 自动装配，
 * 此处显式注册 SentinelGatewayFilter 与限流阻塞异常处理器。
 */
@Configuration
@ConditionalOnProperty(prefix = "oms.sentinel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GatewaySentinelConfig {

    private static final Logger log = LoggerFactory.getLogger(GatewaySentinelConfig.class);

    public static final String API_PAYMENT_CALLBACK = "payment-callback-api";

    public GatewaySentinelConfig(GatewaySentinelProperties properties) {
        loadApiGroups();
        loadFlowRules(properties);
        loadDegradeRules(properties);
        log.info("网关 Sentinel 规则已加载: flow={} degrade={}",
                properties.getFlowQps(), properties.getDegradeRtMs());
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SentinelGatewayBlockExceptionHandler sentinelGatewayBlockExceptionHandler(
            ObjectProvider<List<ViewResolver>> viewResolversProvider,
            ServerCodecConfigurer serverCodecConfigurer) {
        return new SentinelGatewayBlockExceptionHandler(
                viewResolversProvider.getIfAvailable(Collections::emptyList), serverCodecConfigurer);
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public GlobalFilter sentinelGatewayFilter() {
        return new SentinelGatewayFilter();
    }

    private void loadApiGroups() {
        Set<ApiDefinition> definitions = new HashSet<>();
        definitions.add(new ApiDefinition(API_PAYMENT_CALLBACK)
                .setPredicateItems(new HashSet<>(Set.of(
                        new ApiPathPredicateItem()
                                .setPattern("/api/v1/payment-callbacks/**")
                                .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX)))));
        GatewayApiDefinitionManager.loadApiDefinitions(definitions);
    }

    private void loadFlowRules(GatewaySentinelProperties properties) {
        Set<GatewayFlowRule> rules = new HashSet<>();
        properties.getFlowQps().forEach((resource, qps) -> rules.add(
                new GatewayFlowRule(resource)
                        .setCount(qps)
                        .setIntervalSec(1)
                        .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT)));
        GatewayRuleManager.loadRules(rules);
    }

    private void loadDegradeRules(GatewaySentinelProperties properties) {
        java.util.List<DegradeRule> rules = new java.util.ArrayList<>();
        properties.getDegradeRtMs().forEach((resource, rtMs) -> rules.add(
                new DegradeRule(resource)
                        .setGrade(RuleConstant.DEGRADE_GRADE_RT)
                        .setCount(rtMs)
                        .setTimeWindow(10)
                        .setMinRequestAmount(5)
                        .setStatIntervalMs(10_000)));
        DegradeRuleManager.loadRules(rules);
    }
}
