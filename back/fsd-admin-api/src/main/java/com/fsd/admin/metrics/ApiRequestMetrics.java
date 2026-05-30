package com.fsd.admin.metrics;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Component;

@Component
public class ApiRequestMetrics {

    private static final int MAX_SAMPLES = 200;

    private final LongAdder requestCount = new LongAdder();
    private final LongAdder totalDurationMs = new LongAdder();
    private final AtomicLong maxDurationMs = new AtomicLong(0);
    private final long[] recentSamples = new long[MAX_SAMPLES];
    private int sampleIndex;
    private int sampleSize;

    public synchronized void record(long durationMs) {
        requestCount.increment();
        totalDurationMs.add(durationMs);
        maxDurationMs.updateAndGet(current -> Math.max(current, durationMs));
        recentSamples[sampleIndex] = durationMs;
        sampleIndex = (sampleIndex + 1) % MAX_SAMPLES;
        if (sampleSize < MAX_SAMPLES) {
            sampleSize++;
        }
    }

    public long getRequestCount() {
        return requestCount.sum();
    }

    public long getAverageDurationMs() {
        long count = requestCount.sum();
        return count == 0 ? 0 : totalDurationMs.sum() / count;
    }

    public long getMaxDurationMs() {
        return maxDurationMs.get();
    }

    public synchronized long getRecentAverageDurationMs() {
        if (sampleSize == 0) {
            return 0;
        }
        long sum = 0;
        for (int i = 0; i < sampleSize; i++) {
            sum += recentSamples[i];
        }
        return sum / sampleSize;
    }
}
