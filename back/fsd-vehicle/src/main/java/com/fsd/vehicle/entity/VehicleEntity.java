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

    private String linkMode;

    private String vdaManufacturer;

    private String vdaSerialNumber;

    private String vdaInterfaceName;

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

    /** 配送区域: GEO_DELIVERY / SCHEMATIC / BOTH */
    private String deliveryZone;

    /** 最大载重(kg) */
    private Integer maxLoadCapacity;

    /** 当前载重(kg) */
    private Integer currentLoad;

    private Integer deleted;
}
