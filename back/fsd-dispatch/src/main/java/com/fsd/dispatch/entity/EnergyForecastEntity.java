package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 站点补能需求预测结果（ALG-FC）。
 *
 * <p>由离线训练脚本产出后导入，供返充时机与站点容量判断读取；派单链路只读不写。
 */
@Data
@TableName("t_energy_forecast")
public class EnergyForecastEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parkId;

    private Long stationId;

    private String stationCode;

    /** 预测日期。 */
    private LocalDate forecastDate;

    /** 预测小时（0-23）。 */
    private Integer hourOfDay;

    /** 补能需求中位数（次/小时）。 */
    private BigDecimal demandP50;

    /** 补能需求 P90 上界（次/小时）。 */
    private BigDecimal demandP90;

    /** 滑动窗口 P95 到站压力峰值。 */
    private BigDecimal pressureP95;

    /** 训练样本数，用于可追溯性核对。 */
    private Integer sampleCount;

    /** 模型版本/训练轮次标识。 */
    private String modelVersion;

    /** 预测生成时间；超过 {@code max-data-age-hours} 后视为失效。 */
    private LocalDateTime generatedAt;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;

    private Integer deleted;
}
