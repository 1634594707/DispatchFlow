package com.fsd.admin.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminDetailedMetricsResponse {

    private List<MqQueueBacklog> mqBacklogs;

    private DbConnectionPool dbConnectionPool;

    private RedisMemory redisMemory;

    private SseConnection sseConnections;

    private DispatchLockMetric dispatchLock;

    private ApiP99Latency apiP99Latency;

    @Data
    @Builder
    public static class MqQueueBacklog {
        private String queueName;
        private Integer backlog;
        private String status;
    }

    @Data
    @Builder
    public static class DbConnectionPool {
        private Integer active;
        private Integer idle;
        private Integer max;
        private Double usagePercent;
        private String status;
    }

    @Data
    @Builder
    public static class RedisMemory {
        private Long usedBytes;
        private Long maxBytes;
        private Double usagePercent;
        private String status;
    }

    @Data
    @Builder
    public static class SseConnection {
        private Integer activeConnections;
        private String status;
    }

    @Data
    @Builder
    public static class DispatchLockMetric {
        private Long acquireFailureCount;
        private Long heldDurationCount;
        private Long averageHeldDurationMs;
        private Long maxHeldDurationMs;
        private String status;
    }

    @Data
    @Builder
    public static class ApiP99Latency {
        private Long currentMs;
        private Long p50Ms;
        private Long p95Ms;
        private Long p99Ms;
        private String status;
        private List<ApiLatencyHistoryPoint> history;
    }

    @Data
    @Builder
    public static class ApiLatencyHistoryPoint {
        private String time;
        private Long value;
    }
}
