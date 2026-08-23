package com.fsd.admin.auth;

/** 管理端权限资源（路线图 Phase 4：统一权限资源表）。 */
public enum AdminResource {
    /** 调度任务：读取 / 派车 / 取消。 */
    TASK,
    /** 车辆：读取 / 管理。 */
    VEHICLE,
    /** 基础设施（园区、站点、路线等配置）：读取 / 写入。 */
    INFRASTRUCTURE,
    /** 分析数据导出。 */
    ANALYTICS_EXPORT
}
