package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_dispatch_route_station")
public class DispatchRouteStationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long routeId;

    private Long stationId;

    private Integer sequenceNo;
}
