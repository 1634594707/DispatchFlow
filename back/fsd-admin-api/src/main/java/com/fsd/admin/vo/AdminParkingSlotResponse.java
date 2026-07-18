package com.fsd.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminParkingSlotResponse {

    private Long id;

    private Long parkId;

    private String parkName;

    private String slotCode;

    private String slotName;

    private String slotType;

    private BigDecimal coordX;

    private BigDecimal coordY;

    private String status;

    private Long occupiedVehicleId;

    private Integer sortOrder;

    /** P1-8: 车位朝向（N/S/E/W/NE/NW/SE/SW） */
    private String facingDirection;

    /** P1-8: 进站节点编码 */
    private String entryNodeCode;

    /** P1-8: 出站节点编码 */
    private String exitNodeCode;

    /** P1-8: 是否阻塞主路（0=否, 1=是） */
    private Integer blockingMainRoad;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
