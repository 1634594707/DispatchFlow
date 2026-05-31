package com.fsd.admin.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminLoginResponse {

    private String token;

    private boolean requiresTotp;

    private AdminUserResponse user;
}
