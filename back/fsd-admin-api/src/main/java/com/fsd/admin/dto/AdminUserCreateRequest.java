package com.fsd.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminUserCreateRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 64, message = "用户名长度需在 3-64 位之间")
    @Pattern(regexp = "^[\\u4e00-\\u9fa5a-zA-Z0-9_\\-\\.]{2,32}$", message = "用户名仅允许中文、字母、数字、下划线、横线、点")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度需在 8-64 位之间")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
            message = "密码需包含大小写字母、数字和特殊字符（@$!%*?&），至少8位")
    private String password;

    @NotBlank(message = "显示名称不能为空")
    @Size(max = 64, message = "显示名称不能超过 64 位")
    @Pattern(regexp = "^[\\u4e00-\\u9fa5a-zA-Z0-9_\\-\\.]{2,32}$", message = "显示名称仅允许中文、字母、数字、下划线、横线、点")
    private String displayName;

    @NotBlank(message = "角色不能为空")
    private String role;
}
