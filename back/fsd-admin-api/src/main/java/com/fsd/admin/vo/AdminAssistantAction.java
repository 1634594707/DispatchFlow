package com.fsd.admin.vo;

import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminAssistantAction {

    private String actionType;

    private String label;

    private Map<String, Object> payload;
}
