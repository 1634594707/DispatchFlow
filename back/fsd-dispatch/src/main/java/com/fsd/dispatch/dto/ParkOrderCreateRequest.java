package com.fsd.dispatch.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

    @NotNull(message = "pickupStationId is required")
    private Long pickupStationId;

    @NotNull(message = "dropoffStationId is required")
    private Long dropoffStationId;

    private Long routeId;

    private String priority;

    private String remark;
}
