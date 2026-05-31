package com.fsd.admin.service;

import com.fsd.admin.dto.AdminAutomationRuleUpsertRequest;
import com.fsd.admin.dto.AdminDispatchRouteUpsertRequest;
import com.fsd.admin.dto.AdminPeakModeUpsertRequest;
import com.fsd.admin.vo.AdminAutomationRuleAuditResponse;
import com.fsd.admin.vo.AdminAutomationRuleResponse;
import com.fsd.admin.vo.AdminDispatchRouteResponse;
import com.fsd.admin.vo.AdminHubOverviewResponse;
import com.fsd.admin.vo.AdminPeakModeResponse;
import java.util.List;

public interface VerticalAdminService {

    List<AdminDispatchRouteResponse> listRoutes(Long parkId);

    AdminDispatchRouteResponse createRoute(AdminDispatchRouteUpsertRequest request);

    AdminDispatchRouteResponse updateRoute(Long routeId, AdminDispatchRouteUpsertRequest request);

    AdminDispatchRouteResponse toggleRouteStatus(Long routeId);

    AdminPeakModeResponse getPeakMode(Long parkId);

    AdminPeakModeResponse updatePeakMode(AdminPeakModeUpsertRequest request);

    AdminHubOverviewResponse getHubOverview(Long parkId);

    List<AdminAutomationRuleResponse> listAutomationRules(Long parkId);

    AdminAutomationRuleResponse createAutomationRule(AdminAutomationRuleUpsertRequest request, String operator);

    AdminAutomationRuleResponse updateAutomationRule(Long ruleId, AdminAutomationRuleUpsertRequest request, String operator);

    void deleteAutomationRule(Long ruleId, String operator);

    AdminAutomationRuleResponse toggleAutomationRule(Long ruleId, String operator);

    List<AdminAutomationRuleAuditResponse> listAutomationRuleAudit(Long ruleId);
}
