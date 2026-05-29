package com.fsd.vehicle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_vehicle_credential")
public class VehicleCredentialEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long vehicleId;

    private String apiKey;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
