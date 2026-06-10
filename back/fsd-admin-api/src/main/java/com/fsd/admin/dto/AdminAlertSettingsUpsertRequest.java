package com.fsd.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminAlertSettingsUpsertRequest {

    @NotBlank
    private String rulesJson;
}
