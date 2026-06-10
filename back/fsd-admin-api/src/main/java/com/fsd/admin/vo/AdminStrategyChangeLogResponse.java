package com.fsd.admin.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminStrategyChangeLogResponse {

    private Long id;

    private Long profileId;

    private String profileName;

    private String changeType;

    private String operatorName;

    private String changeSummary;

    private LocalDateTime createdAt;
}
