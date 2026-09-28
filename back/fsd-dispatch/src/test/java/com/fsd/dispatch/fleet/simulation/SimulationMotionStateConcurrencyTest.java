package com.fsd.dispatch.fleet.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import com.fsd.dispatch.vo.ParkPointResponse;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #25 回归：轨迹容器被 tick 线程写（add + 淘汰）的同时被 HTTP 读线程迭代
 * （buildSnapshots 在请求线程上直接调 publishTelemetry）。曾是 ArrayDeque/ArrayList
 * 时这个形状必抛 ConcurrentModificationException（压测第 1 轮 /park/vehicles 5xx 一次），
 * 换 ConcurrentLinkedDeque 后必须全程无异常，且淘汰语义（超限先出最旧）不变。
 */
class SimulationMotionStateConcurrencyTest {

    private static ParkPointResponse point(int i) {
        return ParkPointResponse.builder()
                .code("P-" + i)
                .x(BigDecimal.valueOf(i))
                .y(BigDecimal.valueOf(i))
                .build();
    }

    @Test
    @DisplayName("tick 写 + 读线程迭代并发跑 2 秒：零 CME，淘汰上限保持")
    void concurrentTrailReadWriteNeverThrows() throws Exception {
        SimulationMotionState state = new SimulationMotionState();
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> writerError = new AtomicReference<>();
        Thread writer = new Thread(() -> {
            try {
                start.await();
                for (int i = 0; i < 20_000; i++) {
                    state.trail.addLast(point(i));
                    while (state.trail.size() > 50) {
                        state.trail.removeFirst();
                    }
                    state.geoTrail.add(new GeoPoint(BigDecimal.valueOf(i), BigDecimal.valueOf(i)));
                    while (state.geoTrail.size() > 50) {
                        state.geoTrail.pollFirst();
                    }
                }
            } catch (Throwable t) {
                writerError.set(t);
            }
        });
        writer.start();
        start.countDown();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        int reads = 0;
        while (System.nanoTime() < deadline) {
            for (Iterator<ParkPointResponse> it = state.trail.iterator(); it.hasNext(); ) {
                it.next();
            }
            for (Iterator<GeoPoint> it = state.geoTrail.iterator(); it.hasNext(); ) {
                it.next();
            }
            reads++;
        }
        writer.join(10_000);
        assertTrue(!writer.isAlive(), "写线程未结束");
        assertEquals(null, writerError.get(), "写线程不得抛错");
        assertTrue(reads > 100, "读线程实际迭代次数过少: " + reads);
        assertTrue(state.trail.size() <= 50 && state.geoTrail.size() <= 50, "淘汰上限失效");
    }

    @Test
    @DisplayName("淘汰语义：超限先出最旧（pollFirst 与原 remove(0) 等价）")
    void evictionDropsOldest() {
        SimulationMotionState state = new SimulationMotionState();
        for (int i = 0; i < 5; i++) {
            state.geoTrail.add(new GeoPoint(BigDecimal.valueOf(i), BigDecimal.ZERO));
        }
        while (state.geoTrail.size() > 3) {
            state.geoTrail.pollFirst();
        }
        assertEquals(3, state.geoTrail.size());
        assertEquals(2L, state.geoTrail.peekFirst().longitude().longValue(),
                "留下的必须是最新的三个（最旧 0、1 被淘汰）");
    }
}
