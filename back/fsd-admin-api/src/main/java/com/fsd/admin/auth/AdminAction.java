package com.fsd.admin.auth;

/** 管理端权限动作（路线图 Phase 4）。 */
public enum AdminAction {
    /** 读取查询。 */
    READ,
    /** 派车类操作：自动派车、手动派车、改派、批量派车。 */
    ASSIGN,
    /** 取消类操作：单条取消、批量取消、人工接管退回。 */
    CANCEL,
    /** 配置写入：新增/修改/删除。 */
    WRITE,
    /** 导出执行。 */
    EXECUTE
}
