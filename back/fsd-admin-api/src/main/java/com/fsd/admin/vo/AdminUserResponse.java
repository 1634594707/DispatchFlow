package com.fsd.admin.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminUserResponse {

    private Long id;

    private String username;

    private String displayName;

    private String role;

    private String status;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;
}
