package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_park")
public class ParkEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String parkCode;

    private String parkName;

    private Integer mapWidth;

    private Integer mapHeight;

    private Integer minZoom;

    private Integer maxZoom;

    private BigDecimal vehicleSpeedPxPerSecond;

    private BigDecimal centerLng;

    private BigDecimal centerLat;

    private String mapProvider;

    private String status;

    private Integer defaultFlag;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;

    private Integer deleted;
}
