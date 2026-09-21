package com.fsd.dispatch.mapf;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * MAPF 预约结果的计数器（路线图 §7.2「冲突时仍返回未预约的最优候选」的可观测面）。
 *
 * <p>现状是行为取舍未定：冲突重规划用尽之后，代码**照样返回一条没有预约的路线**，
 * 派单继续 —— 也就是说 MAPF 从不真正拦下一单。该不该拦是业务口径（等本人定），
 * 但"有多少比例没预约成功"必须先能被看见，否则这个开关是死是活无人知道。
 *
 * <p>{@code result} 取值：
 * <ul>
 *   <li>{@code reserved} —— 拿到时空预约，路线按预约走；</li>
 *   <li>{@code conflict} —— 重规划用尽后返回未预约路线（含 fallback 那条）；</li>
 *   <li>{@code disabled} —— MAPF 关闭或无车辆 id，直接规划，不做预约。</li>
 * </ul>
 * 三个加起来就是 {@code planAndReserve} 的总调用数，可以直接算冲突率。
 */
@Component
public class MapfReservationMetrics {

    static final String RESERVATION_METRIC = "dispatchflow.mapf.reservation";

    private final MeterRegistry registry;

    public MapfReservationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void reserved() {
        registry.counter(RESERVATION_METRIC, "result", "reserved").increment();
    }

    public void conflict() {
        registry.counter(RESERVATION_METRIC, "result", "conflict").increment();
    }

    public void disabled() {
        registry.counter(RESERVATION_METRIC, "result", "disabled").increment();
    }
}
