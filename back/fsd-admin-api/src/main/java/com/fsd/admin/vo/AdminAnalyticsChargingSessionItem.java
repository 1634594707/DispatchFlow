package com.fsd.admin.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminAnalyticsChargingSessionItem {

    private Long sessionId;

    private Long vehicleId;

    private String vehicleCode;

    private Long chargingPileId;

    private String pileCode;

    private Integer startSoc;

    private Integer currentSoc;

    private LocalDateTime startTime;

    private Long elapsedMinutes;
}
