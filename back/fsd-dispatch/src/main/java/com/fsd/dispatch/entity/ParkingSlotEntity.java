package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_parking_slot")
public class ParkingSlotEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parkId;

    private String slotCode;

    private String slotName;

    private String slotType;

    /** 车位朝向：NORTH/SOUTH/EAST/WEST/NE/NW/SE/SW */
    private String facingDirection;

    /** 进站节点编码 */
    private String entryNodeCode;

    /** 出站节点编码 */
    private String exitNodeCode;

    /** 是否阻塞主路（1=是，禁止长时间占用） */
    private Integer blockingMainRoad;

    private BigDecimal coordX;

    private BigDecimal coordY;

    private BigDecimal coordLng;

    private BigDecimal coordLat;

    private String status;

    private Long occupiedVehicleId;

    private Integer sortOrder;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;

    private Integer deleted;
}
