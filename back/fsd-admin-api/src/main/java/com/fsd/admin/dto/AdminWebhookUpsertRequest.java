package com.fsd.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminWebhookUpsertRequest {

    private Long id;

    @NotBlank
    private String name;

    @NotBlank
    private String callbackUrl;

    private String secretToken;

    @NotBlank
    private String eventTypes;

    private Boolean enabled = true;
}
