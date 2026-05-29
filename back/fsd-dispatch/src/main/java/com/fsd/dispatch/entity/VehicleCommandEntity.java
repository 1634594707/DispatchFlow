package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_vehicle_command")
public class VehicleCommandEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long vehicleId;

    private Long taskId;

    private Long orderId;

    private String commandType;

    private String commandStatus;

    private String payloadJson;

    private String failReason;

    private LocalDateTime issuedAt;

    private LocalDateTime deliveredAt;

    private LocalDateTime ackedAt;

    private LocalDateTime failedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
