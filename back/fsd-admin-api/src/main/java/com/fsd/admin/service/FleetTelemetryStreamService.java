package com.fsd.admin.service;

import com.fsd.dispatch.vo.ParkVehicleSnapshotResponse;
import java.util.List;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface FleetTelemetryStreamService {

    SseEmitter createStream(Long parkId);

    void broadcast(Long parkId, List<ParkVehicleSnapshotResponse> vehicles);

    /**
     * 该园区当前是否有活跃的 SSE 订阅者。判据与 {@link #broadcast} 的分组键完全一致
     * （{@code parkId == null} 同样归到 0 号键），所以"这里返回 false"必然意味着
     * "下一次 broadcast 会原地丢弃载荷"。
     */
    boolean hasClients(Long parkId);
}
