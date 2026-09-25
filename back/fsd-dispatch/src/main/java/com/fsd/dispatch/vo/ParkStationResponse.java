package com.fsd.dispatch.vo;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParkStationResponse {

    private Long parkId;

    private String parkCode;

    private Long stationId;

    private String stationCode;

    private String stationName;

    private String stationType;

    private BigDecimal x;

    private BigDecimal y;

    /** 可选 GCJ-02，M4 物流矩阵 N-1 评分。 */
    private BigDecimal coordLng;

    private BigDecimal coordLat;

    private String area;

    /** 营业时间窗口，例如 "06:00-22:00" */
    private String serviceHours;

    /** 平均服务时长（秒） */
    private Integer avgServiceSeconds;

    /** 站点承载上限 */
    private Integer capacityLimit;

    /**
     * 站点启停状态（ACTIVE / INACTIVE）。§6.4 前端要按"可服务与否"筛点，
     * 之前只能靠 `ZJF-CHG-` 编码前缀猜（库里 CHG-02…05 恰好都是 INACTIVE 才碰巧对）。
     */
    private String status;
}
