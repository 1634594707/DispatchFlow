package com.fsd.admin.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminGlobalSearchItem {

    private String type;

    private Long id;

    private String code;

    private String title;

    private String subtitle;

    private String routePath;
}
