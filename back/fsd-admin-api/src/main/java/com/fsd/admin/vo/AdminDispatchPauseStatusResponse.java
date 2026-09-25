package com.fsd.admin.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminDispatchPauseStatusResponse {

    private Long parkId;

    private Boolean globalPaused;

    private Boolean parkPaused;

    /** 生效中那次暂停的审计信息；未暂停时三项均为 null。 */
    private String pauseReason;

    private String pausedBy;

    private LocalDateTime pausedAt;
}
