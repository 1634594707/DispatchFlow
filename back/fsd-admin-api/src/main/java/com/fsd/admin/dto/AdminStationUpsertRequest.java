package com.fsd.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class AdminStationUpsertRequest {

    @NotNull(message = "园区 ID 不能为空")
    private Long parkId;

    @NotBlank(message = "站点编码不能为空")
    @Size(max = 64, message = "站点编码不能超过 64 位")
    private String stationCode;

    @NotBlank(message = "站点名称不能为空")
    @Size(max = 128, message = "站点名称不能超过 128 位")
    private String stationName;

    @NotBlank(message = "站点类型不能为空")
    private String stationType;

    @NotNull(message = "坐标 X 不能为空")
    private BigDecimal coordX;

    @NotNull(message = "坐标 Y 不能为空")
    private BigDecimal coordY;

    /** Optional GCJ-02 longitude; falls back to park x/y transform when absent. */
    private BigDecimal coordLng;

    /** Optional GCJ-02 latitude; falls back to park x/y transform when absent. */
    private BigDecimal coordLat;

    @Size(max = 32, message = "区域标识不能超过 32 位")
    private String area;

    private String status;

    private Integer sortOrder;

    private Integer capacityLimit;

    @Size(max = 255, message = "备注不能超过 255 位")
    private String remark;
}
