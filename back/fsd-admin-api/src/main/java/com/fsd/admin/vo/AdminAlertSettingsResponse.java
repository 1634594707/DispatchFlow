package com.fsd.admin.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminAlertSettingsResponse {

    private String rulesJson;
}
