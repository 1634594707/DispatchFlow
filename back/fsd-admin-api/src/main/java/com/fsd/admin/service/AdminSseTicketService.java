package com.fsd.admin.service;

import com.fsd.admin.auth.AdminAuthContext;

public interface AdminSseTicketService {

    String issue(AdminAuthContext context);

    AdminAuthContext consume(String ticket);
}
