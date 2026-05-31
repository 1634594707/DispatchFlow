package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_battery_swap_cabinet")
public class BatterySwapCabinetEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parkId;

    private String cabinetCode;

    private String cabinetName;

    private BigDecimal coordX;

    private BigDecimal coordY;

    private Integer slotCount;

    private String status;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer deleted;
}
