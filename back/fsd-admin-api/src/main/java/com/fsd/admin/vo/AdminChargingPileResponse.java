package com.fsd.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminChargingPileResponse {

    private Long id;

    private Long parkId;

    private String parkName;

    private String pileCode;

    private String pileName;

    private Long parkingSlotId;

    private String parkingSlotCode;

    private String status;

    private Long occupiedVehicleId;

    private BigDecimal maxPowerKw;

    private Integer sortOrder;

    /** P1-6: 进站点编码 */
    private String entryNodeCode;

    /** P1-6: 出站点编码 */
    private String exitNodeCode;

    /** P1-6: 充电枪类型（CCS2/GB/T-DC/CHAdeMO/特斯拉） */
    private String plugType;

    /** P1-6: 预约状态（FREE/RESERVED/OCCUPIED/MAINTENANCE） */
    private String reservationState;

    /** P1-6: 预计释放时间 */
    private LocalDateTime estimatedReleaseAt;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
