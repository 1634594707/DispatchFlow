package com.fsd.admin.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminHealthTimelineResponse {

    private List<AdminHealthTimelineItemResponse> items;

    @Data
    @Builder
    public static class AdminHealthTimelineItemResponse {
        private String time;
        private String component;
        private String status;
        private String message;
    }
}
