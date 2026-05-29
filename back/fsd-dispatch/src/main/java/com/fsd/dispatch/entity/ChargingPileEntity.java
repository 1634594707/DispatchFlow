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

    private Integer sortOrder;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;

    private Integer deleted;
}
