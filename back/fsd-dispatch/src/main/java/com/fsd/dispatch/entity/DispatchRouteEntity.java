package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.Data;

@Data
@TableName("t_dispatch_route")
public class DispatchRouteEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parkId;

    private String routeCode;

    private String routeName;

    private String status;

    private LocalTime serviceStartTime;

    private LocalTime serviceEndTime;

    private String requiredVehicleType;

    private Integer maxConcurrentTasks;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;

    private Integer deleted;
}
