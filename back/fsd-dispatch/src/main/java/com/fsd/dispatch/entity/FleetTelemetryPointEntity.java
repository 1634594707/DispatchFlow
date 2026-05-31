package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_fleet_telemetry_point")
public class FleetTelemetryPointEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long vehicleId;

    private Long parkId;

    private BigDecimal coordX;

    private BigDecimal coordY;

    private Integer soc;

    private LocalDateTime recordedAt;

    private LocalDateTime createdAt;
}
