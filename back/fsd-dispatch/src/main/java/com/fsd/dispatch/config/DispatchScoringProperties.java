package com.fsd.dispatch.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "fsd.dispatch.scoring")
public class DispatchScoringProperties {

    /** Lower total score wins. */
    private double weightDistance = 1.0D;

    /** Penalty per SOC point below full charge. */
    private double weightSocMargin = 0.15D;

    /** Score bonus (subtract) when vehicle is plugged-in full standby. */
    private double weightPluggedStandbyBonus = 80.0D;

    /** 订单优先级权重（高优先级订单对距离和SOC更敏感） */
    private double weightPriority = 1.0D;

    /** 车辆均衡因子权重（闲置时间奖励） */
    private double weightFairness = 0.5D;

    /** 闲置时间奖励上限 */
    private double maxIdleBonus = 30.0D;

    /** 交通拥堵因子权重 */
    private double weightCongestion = 0.3D;
}
