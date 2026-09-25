package com.fsd.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminDispatchPauseRequest {

    private Long parkId;

    @NotNull(message = "paused is required")
    private Boolean paused;

    /** 暂停必填（服务端校验），恢复可空。上限与 {@code t_dispatch_pause_state.pause_reason} 一致。 */
    @Size(max = 128, message = "reason too long")
    private String reason;
}
