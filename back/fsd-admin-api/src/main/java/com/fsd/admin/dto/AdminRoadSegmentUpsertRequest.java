package com.fsd.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminRoadSegmentUpsertRequest {

    @NotNull(message = "园区 ID 不能为空")
    private Long parkId;

    @NotBlank(message = "起点节点编码不能为空")
    @Size(max = 64, message = "起点节点编码不能超过 64 位")
    private String fromNodeCode;

    @NotBlank(message = "终点节点编码不能为空")
    @Size(max = 64, message = "终点节点编码不能超过 64 位")
    private String toNodeCode;

    private String status;

    private Integer speedLimitKmh;

    private Integer congestionLevel;

    @Size(max = 255, message = "备注不能超过 255 位")
    private String remark;
}
