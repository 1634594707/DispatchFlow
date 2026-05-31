package com.fsd.admin.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminDispatchPauseStatusResponse {

    private Long parkId;

    private Boolean globalPaused;

    private Boolean parkPaused;
}
