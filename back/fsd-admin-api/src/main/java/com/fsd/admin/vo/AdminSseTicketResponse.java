package com.fsd.admin.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminSseTicketResponse {

    private String ticket;

    private long expiresInSeconds;
}
