package com.fsd.admin.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminAnalyticsChargingHistoryItem {

    private Long sessionId;

    private Long vehicleId;

    private String vehicleCode;

    private String pileCode;

    private Integer startSoc;

    private Integer endSoc;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long durationMinutes;

    private Double chargeSpeedPerHour;
}
