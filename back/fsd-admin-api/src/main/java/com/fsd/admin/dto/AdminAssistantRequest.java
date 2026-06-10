package com.fsd.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminAssistantRequest {

    @NotBlank
    private String instruction;

    private Long parkId;
}
