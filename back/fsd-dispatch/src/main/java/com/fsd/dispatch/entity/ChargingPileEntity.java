package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_charging_pile")
public class ChargingPileEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parkId;

    private String pileCode;

    private String pileName;

    private Long parkingSlotId;

    private String status;

    private Long occupiedVehicleId;

    private BigDecimal maxPowerKw;

    /** 进站点（充电区入口道路节点编码） */
    private String entryNodeCode;

    /** 出站点（充电区出口道路节点编码；与 entry 不同则需单向循环） */
    private String exitNodeCode;

    /** 充电枪类型：CCS2/GB_T_DC/CHAOJI/AC_GENERIC/WIRELESS */
    private String plugType;

    /** 预约状态：FREE/RESERVED/CHARGING/FAULT */
    private String reservationState;

    /** 预计释放时间（用于调度成本与排队估算） */
    private java.time.LocalDateTime estimatedReleaseAt;

    private Integer sortOrder;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;

    private Integer deleted;
}
