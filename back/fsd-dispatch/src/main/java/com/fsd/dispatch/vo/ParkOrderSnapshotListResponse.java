package com.fsd.dispatch.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 整园订单快照的返回包（性能优化方案 P0-3）。
 *
 * <p>为什么不再是裸数组：这一页读的是"候选窗"而不是全表，窗被填满时列表就只代表窗内可见单量，
 * 不再等于"园区里所有能看的单"。调用方必须能区分"就这 20 条"和"我只读到 60 条里的 20 条"，
 * 否则大屏会把积压读成 20 单。历史单请走报表/分页接口，别指望这个窗。
 */
@Data
@Builder
public class ParkOrderSnapshotListResponse {

    private List<ParkOrderSnapshotResponse> items;

    /** true = 候选窗已用满，园区内可能还有匹配的单没出现在 {@code items} 里。 */
    private boolean truncated;
}
