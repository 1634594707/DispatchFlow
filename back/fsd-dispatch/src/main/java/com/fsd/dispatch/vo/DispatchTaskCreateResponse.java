package com.fsd.dispatch.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DispatchTaskCreateResponse {

    private Long taskId;

    private String taskNo;

    private String status;
}
