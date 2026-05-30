package com.fsd.admin.service;

import com.fsd.admin.dto.AdminAssistantRequest;
import com.fsd.admin.vo.AdminAssistantResponse;

public interface DispatchAssistantAdminService {

    AdminAssistantResponse interpret(AdminAssistantRequest request);
}
