package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 交通管制暂停区（§7.4）：MySQL 真相表，Redis 只做带 TTL 的缓存。
 *
 * <p>矩形以「左下角 + 右上角」存储，与 {@code TrafficZoneControlService.PauseZone} 一致；
 * 坐标语义沿用调用方传入的园区坐标系（{@code pickup.getX/getY}），本表不做坐标换算。
 */
@Data
@TableName("t_traffic_pause_zone")
public class TrafficPauseZoneEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parkId;

    private Double minX;

    private Double minY;

    private Double maxX;

    private Double maxY;

    private String label;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
