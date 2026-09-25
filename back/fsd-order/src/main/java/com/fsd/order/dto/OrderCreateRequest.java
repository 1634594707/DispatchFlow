package com.fsd.order.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class OrderCreateRequest {

    @NotBlank(message = "externalOrderNo is required")
    private String externalOrderNo;

    @NotBlank(message = "sourceType is required")
    private String sourceType;

    @NotBlank(message = "bizType is required")
    private String bizType;

    private Long parkId;

    /** 取货站点。与 {@link #pickupLng}/{@link #pickupLat} 二选一，两边都缺由受理侧拒单。 */
    private Long pickupPointId;

    /** 送货站点。与 {@link #dropoffLng}/{@link #dropoffLat} 二选一。 */
    private Long dropoffPointId;

    /** 快递式任意点下单：GCJ-02 原始坐标，站点退化为"常用点"而非派单前提。 */
    @DecimalMin(value = "-180.0", message = "pickupLng out of range")
    @DecimalMax(value = "180.0", message = "pickupLng out of range")
    private BigDecimal pickupLng;

    @DecimalMin(value = "-90.0", message = "pickupLat out of range")
    @DecimalMax(value = "90.0", message = "pickupLat out of range")
    private BigDecimal pickupLat;

    @DecimalMin(value = "-180.0", message = "dropoffLng out of range")
    @DecimalMax(value = "180.0", message = "dropoffLng out of range")
    private BigDecimal dropoffLng;

    @DecimalMin(value = "-90.0", message = "dropoffLat out of range")
    @DecimalMax(value = "90.0", message = "dropoffLat out of range")
    private BigDecimal dropoffLat;

    @NotBlank(message = "priority is required")
    private String priority;

    private Long routeId;

    private String remark;
}
