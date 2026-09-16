package com.fsd.dispatch.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 补能需求预测接入配置（ALG-FC）。
 *
 * <p>默认开启读取，但预测表为空时所有判断都返回"无压力"，行为与接入前一致——
 * 即缺少模型数据不会改变生产派单/补能语义。
 */
@Data
@Component
@ConfigurationProperties(prefix = "fsd.energy-forecast")
public class EnergyForecastProperties {

    /** 预测读取总开关。 */
    private boolean enabled = true;

    /** 是否允许在高压力时段推迟返充（错峰补能）。 */
    private boolean deferReturnEnabled = true;

    /** 站点/园区压力阈值：pressureP95 达到该值视为高峰。 */
    private double pressureThreshold = 2.0D;

    /** 推迟返充所需的最小 SOC 余量（相对 criticalSocThreshold）；余量不足则安全优先、立即返充。 */
    private int deferMarginSoc = 10;

    /** 预测数据最大有效期（小时）；超过则忽略该预测。 */
    private int maxDataAgeHours = 24;
}
