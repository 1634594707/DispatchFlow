package com.fsd.dispatch.vo;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParkPointResponse {

    private String code;

    private BigDecimal x;

    private BigDecimal y;

    private BigDecimal longitude;

    private BigDecimal latitude;

    /**
     * 只有从 {@code t_parking_slot} 来的点才填（车位类型 STANDBY / CHARGING_ONLY）；
     * 站点、充电点等其它来源留 null，前端据此决定画不画车位层。
     */
    private String slotType;

    /** 车位状态（FREE / RESERVED / OCCUPIED / …），同上：非车位点为 null。 */
    private String status;

    /**
     * 占着这个位的车 id（给"点车位看谁占着"用），空位为 null。
     *
     * <p>刻意只带 id 不带车号：车位层是页面载入时一次读的，而要看车号的那两个视图
     * （大屏、工作台）本来就轮询着整车队快照，在那边按 id 查表比在这里多打一次 join 便宜。
     */
    private Long occupiedVehicleId;
}
