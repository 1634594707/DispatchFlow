package com.fsd.admin.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminAssistantResponse {

    private String intent;

    private String reply;

    private List<String> suggestions;

    private List<AdminAssistantAction> actions;
}
