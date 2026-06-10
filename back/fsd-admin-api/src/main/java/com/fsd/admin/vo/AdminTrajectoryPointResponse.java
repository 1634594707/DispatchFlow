package com.fsd.admin.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminTrajectoryPointResponse {

    private LocalDateTime ts;

    private Double x;

    private Double y;

    private Integer soc;
}
