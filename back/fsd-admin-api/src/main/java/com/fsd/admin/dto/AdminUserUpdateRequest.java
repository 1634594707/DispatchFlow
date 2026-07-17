package com.fsd.admin.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminUserUpdateRequest {

    @Size(max = 64, message = "显示名称不能超过 64 位")
    @Pattern(regexp = "^[\\u4e00-\\u9fa5a-zA-Z0-9_\\-\\.]{2,32}$", message = "显示名称仅允许中文、字母、数字、下划线、横线、点")
    private String displayName;

    private String role;

    private String status;
}
