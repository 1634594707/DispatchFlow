package com.fsd.admin.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminUserUpdateRequest {

    @Size(max = 64, message = "显示名称不能超过 64 位")
    private String displayName;

    private String role;

    private String status;
}
