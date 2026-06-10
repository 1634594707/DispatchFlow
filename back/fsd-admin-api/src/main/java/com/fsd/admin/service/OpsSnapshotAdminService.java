package com.fsd.admin.service;

import com.fsd.admin.vo.AdminOpsSnapshotResponse;

public interface OpsSnapshotAdminService {

    AdminOpsSnapshotResponse getSnapshot(Long parkId);
}
