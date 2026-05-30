package com.fsd.vehicle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_vehicle_maintenance")
public class VehicleMaintenanceEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long vehicleId;

    private String maintenanceType;

    private String description;

    private LocalDateTime maintenanceAt;

    private String operatorName;

    private String status;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;

    private Integer deleted;
}
