package com.fsd.dispatch.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 灰度分桶的契约：稳定、有界、空键保守侧，生产与实验台共用这一份实现。 */
class GrayBucketsTest {

    @Test
    @DisplayName("同一键重放必落同一桶（灰度重掷缺陷的反面）")
    void sameKeyAlwaysSameBucket() {
        for (long i = 0; i < 1000; i++) {
            String key = "ord-" + i;
            assertEquals(GrayBuckets.of(key), GrayBuckets.of(key));
        }
    }

    @Test
    @DisplayName("桶号有界 0..99，且不是全部塌进一个桶")
    void bucketsAreBoundedAndSpread() {
        Set<Integer> seen = new HashSet<>();
        for (long i = 0; i < 2000; i++) {
            int bucket = GrayBuckets.of("ord-" + i);
            assertTrue(bucket >= 0 && bucket < 100, "桶号越界: " + bucket);
            seen.add(bucket);
        }
        assertTrue(seen.size() > 50, "2000 个键只落进 " + seen.size() + " 个桶，分桶函数可疑");
    }

    @Test
    @DisplayName("空键落 0 号桶（保守侧 = 在位策略）")
    void blankKeyGoesToBucketZero() {
        assertEquals(0, GrayBuckets.of(null));
        assertEquals(0, GrayBuckets.of(""));
        assertEquals(0, GrayBuckets.of("   "));
    }
}
