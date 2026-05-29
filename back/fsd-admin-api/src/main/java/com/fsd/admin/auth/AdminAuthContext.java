package com.fsd.admin.auth;

import com.fsd.common.enums.AdminRole;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminAuthContext {

    private Long userId;

    private String username;

    private String displayName;

    private AdminRole role;

    private String token;
}
