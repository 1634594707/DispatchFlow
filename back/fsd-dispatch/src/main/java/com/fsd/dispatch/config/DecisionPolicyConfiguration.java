package com.fsd.dispatch.config;

import com.fsd.dispatch.core.DecisionPolicy;
import com.fsd.dispatch.core.ForecastAwarePolicy;
import com.fsd.dispatch.core.RulePolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 决策策略插槽的装配点（§2.1）。
 *
 * <p>{@code com.fsd.dispatch.core} 刻意不带任何 Spring 依赖，策略实现要在这里显式成为 bean。
 *
 * <p>装配规则（§2.2）：这里注册的都是**候选**，"本单用哪个"由 {@code DecisionPolicyRouter} 按
 * OFF/SHADOW/GRAY/PRIMARY 与稳定分桶决定，不再由 bean 装配写死。在位策略标 {@code @Primary}
 * 只是为了让尚未接入路由的老消费者拿到的仍是 RULE，行为与本开关引入前一致。
 */
@Configuration
public class DecisionPolicyConfiguration {

    @Bean
    @Primary
    public DecisionPolicy decisionPolicy() {
        return new RulePolicy();
    }

    /** 预测感知策略：作为 §2.2 的挑战者候选存在，默认不被选中（{@code fsd.dispatch.policy.mode=OFF}）。 */
    @Bean
    public DecisionPolicy forecastAwarePolicy() {
        return new ForecastAwarePolicy();
    }
}
