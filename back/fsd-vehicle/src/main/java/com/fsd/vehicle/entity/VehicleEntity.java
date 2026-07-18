package com.fsd.vehicle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_vehicle")
public class VehicleEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String vehicleCode;

    private String vehicleName;

    private String vehicleType;

    private String linkMode;

    private String vdaManufacturer;

    private String vdaSerialNumber;

    private String vdaInterfaceName;

    private String onlineStatus;

    private String dispatchStatus;

    private Long currentTaskId;

    private Long currentOrderId;

    private BigDecimal currentLatitude;

    private BigDecimal currentLongitude;

    private Integer batteryLevel;

    private LocalDateTime lastReportTime;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;

    /** 配送区域: GEO_DELIVERY / SCHEMATIC / BOTH */
    private String deliveryZone;

    /** 最大载重(kg) */
    private Integer maxLoadCapacity;

    /** 当前载重(kg) */
    private Integer currentLoad;

    /** 车辆宽度（厘米），用于道路宽度可用性检查 */
    private Integer widthCm;

    /** 车辆长度（厘米） */
    private Integer lengthCm;

    /** V43: 车辆高度（厘米），用于限高检查 */
    private Integer heightCm;

    /** 最小转弯半径（米），用于窄路/急弯过滤 */
    private java.math.BigDecimal turningRadiusM;

    /** 允许道路等级（逗号分隔，NULL=全部；如 ARTERIAL,SECONDARY） */
    private String allowedRoadClasses;

    /** V43: 最大速度（km/h），用于 ETA 估算 */
    private Integer maxSpeedKmh;

    /** V43: 当前速度（km/h），运行态上报 */
    private java.math.BigDecimal currentSpeedKmh;

    /** V43: 当前车头朝向（度，0=北，顺时针） */
    private java.math.BigDecimal currentHeading;

    /** V43: 人工接管状态（1=人工接管，0=自动） */
    private Integer manualOverride;

    /** V43: 紧急模式（1=紧急停车，0=正常） */
    private Integer emergencyMode;

    /** V43: 安全缓冲（米），车辆包络膨胀，用于碰撞检查 */
    private java.math.BigDecimal safetyBufferMeters;

    /** V43: 当前地图数据版本ID（关联 t_map_data_version.id） */
    private Long currentMapVersionId;

    private Integer deleted;
}
