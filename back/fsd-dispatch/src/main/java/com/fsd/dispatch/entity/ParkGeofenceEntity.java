package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_park_geofence")
public class ParkGeofenceEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parkId;

    private String fenceCode;

    private String fenceName;

    /** BOUNDARY = alert on exit; RESTRICTED = alert on enter */
    private String fenceType;

    private String polygonJson;

    private String status;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;

    private Integer deleted;
}
