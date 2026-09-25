package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_park_geofence")
public class ParkGeofenceEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parkId;

    private String fenceCode;

    private String fenceName;

    /** BOUNDARY = alert on exit; RESTRICTED = alert on enter */
    private String fenceType;

    /**
     * 响应级别（阶段六 6.1）：
     * INFO = 仅记录日志，不写异常、不触发自动化规则；
     * WARN = 记录异常并告警，但不触发紧急停车/升级流程；
     * BLOCK = 记录异常并触发紧急停车（最高响应级别）。
     */
    private String responseLevel;

    /**
     * GPS 缓冲距离（米，阶段六 6.2）。
     * 用于 GEOFENCE_EXIT 时的边界容差判定，替代原硬编码 GPS_BUFFER_METERS=15.0。
     */
    private BigDecimal bufferMeters;

    private String polygonJson;

    private String status;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;


    private Integer deleted;

    /**
     * 围栏语义分档 —— 全仓**唯一**出处（原先只有 {@code ParkGeofenceServiceImpl} 里有一份 private 推导，
     * 越界监控要复用就得再抄一遍前缀判断，那就成了两套真相）。
     *
     * <p>注意兜底档是"展示包络"：既不是 {@code ZJF-ZONE-*} 也不是 {@code RESTRICTED} 的围栏，
     * 一律按"给人看的框"处理，不参与受理与越界告警。
     */
    public static final String SCOPE_L1_CORE = "L1_CORE";
    public static final String SCOPE_RESTRICTED = "SAFETY_RESTRICTED";
    public static final String SCOPE_DISPLAY_ENVELOPE = "L1_CANDIDATE_ENVELOPE";

    public static String scopeCode(ParkGeofenceEntity entity) {
        if (entity == null) {
            return SCOPE_DISPLAY_ENVELOPE;
        }
        String code = entity.getFenceCode() == null ? "" : entity.getFenceCode();
        if (code.startsWith("ZJF-ZONE-")) {
            return SCOPE_L1_CORE;
        }
        if ("RESTRICTED".equals(entity.getFenceType())) {
            return SCOPE_RESTRICTED;
        }
        return SCOPE_DISPLAY_ENVELOPE;
    }

    /**
     * 「这条围栏参与受理判据吗」—— 与越界监控共用 {@link #scopeCode}，别再各写一份前缀判断。
     *
     * <p>状态必须一起判：只看编码会把停用的围栏也算成可派单 —— 库里 {@code ZJF-ZONE-SXZ/WLG/CJ}
     * 三片末端场站 2026-09-23 已置 DISABLED。
     */
    public static boolean isServiceDispatchable(ParkGeofenceEntity entity) {
        return entity != null
                && "ACTIVE".equalsIgnoreCase(entity.getStatus())
                && SCOPE_L1_CORE.equals(scopeCode(entity));
    }
}
