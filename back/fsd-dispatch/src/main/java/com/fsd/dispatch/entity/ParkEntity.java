package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_park")
public class ParkEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String parkCode;

    private String parkName;

    private Integer mapWidth;

    private Integer mapHeight;

    private Integer minZoom;

    private Integer maxZoom;

    private BigDecimal vehicleSpeedPxPerSecond;

    private BigDecimal centerLng;

    private BigDecimal centerLat;

    /** 阶段七 7.3：坐标转换锚点经度（GCJ-02），用于 schematic↔GPS 转换。 */
    private BigDecimal anchorLng;

    /** 阶段七 7.3：坐标转换锚点纬度（GCJ-02）。 */
    private BigDecimal anchorLat;

    /** 阶段七 7.3：园区真实宽度（米）。 */
    private BigDecimal parkWidthMeters;

    /** 阶段七 7.3：园区真实高度（米）。 */
    private BigDecimal parkHeightMeters;

    /** 阶段七 7.3：场景编码（如 ZJF_DIESHIQIAO_PILOT）。 */
    private String scenarioCode;

    private String mapProvider;

    private String status;

    private Integer defaultFlag;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;

    private Integer deleted;
}
