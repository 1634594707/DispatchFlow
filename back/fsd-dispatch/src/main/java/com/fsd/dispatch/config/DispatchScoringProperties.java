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
}
