package com.fsd.dispatch.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class ParkOrderCreateRequest {

    @Schema(description = "幂等键：客户端为每个下单意图生成一次（建议 UUID），重试必须复用；重复提交返回原订单",
            example = "550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "idempotencyKey is required")
    @Pattern(regexp = "^[A-Za-z0-9._:-]{8,128}$", message = "idempotencyKey must match [A-Za-z0-9._:-]{8,128}")
    private String idempotencyKey;

    private String externalOrderNo;

    private Long parkId;

    /** 取货站点。与下面的坐标二选一；两边都缺由受理侧拒单。 */
    private Long pickupStationId;

    /** 送货站点。与下面的坐标二选一。 */
    private Long dropoffStationId;

    /** 快递式任意点下单：地图上直接点的 GCJ-02 位置，站点退化成"常用点"而非派单前提。 */
    @Schema(description = "取货点 GCJ-02 经度；未给 pickupStationId 时必填")
    private BigDecimal pickupLng;

    @Schema(description = "取货点 GCJ-02 纬度")
    private BigDecimal pickupLat;

    @Schema(description = "送货点 GCJ-02 经度；未给 dropoffStationId 时必填")
    private BigDecimal dropoffLng;

    @Schema(description = "送货点 GCJ-02 纬度")
    private BigDecimal dropoffLat;

    private Long routeId;

    private String priority;

    private String remark;
}
