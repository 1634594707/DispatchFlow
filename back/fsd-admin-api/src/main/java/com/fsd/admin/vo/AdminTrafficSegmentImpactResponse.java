package com.fsd.admin.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminTrafficSegmentImpactResponse {

    private Long segmentId;

    private Integer affectedTaskCount;

    private Integer unreachableStationCount;

    private List<String> alternativePathHints;
}
