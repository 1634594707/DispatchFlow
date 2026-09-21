package com.fsd.dispatch.config;

import com.fsd.dispatch.core.DecisionPolicy;
import com.fsd.dispatch.core.RulePolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 决策策略插槽的装配点（§2.1）。
 *
 * <p>{@code com.fsd.dispatch.core} 刻意不带任何 Spring 依赖，策略实现要在这里显式成为 bean。
 * 新增策略（预测感知、Jev）时在此按 §2.2 的 SHADOW → GRAY → PRIMARY 顺序替换或路由，
 * 派单热路径的代码不需要再改动。
 */
@Configuration
public class DecisionPolicyConfiguration {

    @Bean
    public DecisionPolicy decisionPolicy() {
        return new RulePolicy();
    }
}
