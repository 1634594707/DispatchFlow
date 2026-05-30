package com.fsd.admin.vo;

import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminSystemComponentHealth {

    private String name;

    private String status;

    private String message;

    private Map<String, Object> details;
}
