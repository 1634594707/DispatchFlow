package com.fsd.vehicle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_vehicle")
public class VehicleEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String vehicleCode;

    private String vehicleName;

    private String vehicleType;

    private String onlineStatus;

    private String dispatchStatus;

    private Long currentTaskId;

    private Long currentOrderId;

    private BigDecimal currentLatitude;

    private BigDecimal currentLongitude;

    private Integer batteryLevel;

    private LocalDateTime lastReportTime;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;

    private Integer deleted;
}
