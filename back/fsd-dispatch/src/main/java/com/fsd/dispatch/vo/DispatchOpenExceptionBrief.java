package com.fsd.dispatch.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DispatchOpenExceptionBrief {

    private Long exceptionId;

    private String exceptionType;

    private String exceptionMsg;

    private String severity;

    private String exceptionStatus;

    private LocalDateTime occurTime;
}
