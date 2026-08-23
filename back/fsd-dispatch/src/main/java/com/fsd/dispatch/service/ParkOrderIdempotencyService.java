package com.fsd.dispatch.service;

import com.fsd.dispatch.dto.ParkOrderCreateRequest;
import com.fsd.dispatch.vo.ParkOrderCreateResponse;
import jakarta.validation.constraints.NotNull;

/**
 * 移动端订单创建幂等契约（路线图 2.2 / 3.3）。
 *
 * <p>规则：</p>
 * <ul>
 *   <li>客户端为每个“意图下单”生成一次 {@code idempotencyKey}（建议 UUID），网络重试必须复用同一键；</li>
 *   <li>同一键首次成功创建后，重复提交直接返回原订单/任务结果（响应快照重放），不产生新资源；</li>
 *   <li>同一键但请求语义指纹不同时拒绝（IDEMPOTENCY_KEY_MISMATCH），防止误覆盖；</li>
 *   <li>同一键仍在处理中时拒绝并发提交（IDEMPOTENCY_IN_PROGRESS）；</li>
 *   <li>首次提交失败整体回滚，幂等键自动释放，允许使用同一键重试。</li>
 * </ul>
 */
public interface ParkOrderIdempotencyService {

    /**
     * 占用幂等键。
     *
     * @return null 表示首次提交且已占用成功，调用方继续执行订单创建；
     *         非 null 表示重复提交，直接返回原响应（已标记 replayed）。
     */
    ParkOrderCreateResponse tryReserve(ParkOrderCreateRequest request, @NotNull Long parkId);

    /** 首次提交成功后写入完成状态与响应快照。 */
    void completeReservation(ParkOrderCreateRequest request, ParkOrderCreateResponse response);
}
