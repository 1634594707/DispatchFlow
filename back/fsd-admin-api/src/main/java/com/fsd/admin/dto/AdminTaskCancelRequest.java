package com.fsd.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminTaskCancelRequest {

    private String remark;
}
