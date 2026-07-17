package com.fsd.common.enums;

public enum ChargingSessionStatus {
    ACTIVE,
    COMPLETED,
    /**
     * ALG-10 fix: session was forcibly terminated by the charging-timeout scheduler
     * because it exceeded the configured max duration. The vehicle is released back
     * to IDLE so it doesn't get permanently stuck in CHARGING on a faulty pile.
     */
    TIMED_OUT
}
