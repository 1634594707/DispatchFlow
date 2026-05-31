package com.fsd.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminTotpCodeRequest {

    @NotBlank(message = "TOTP code is required")
    private String code;
}
