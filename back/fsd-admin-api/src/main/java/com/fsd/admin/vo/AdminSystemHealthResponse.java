package com.fsd.admin.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminSystemHealthResponse {

    private String overallStatus;

    private LocalDateTime checkedAt;

    private List<AdminSystemComponentHealth> components;
}
