package com.fsd.admin.controller;

import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.admin.service.AnalyticsAdminService;
import com.fsd.admin.vo.AdminPeakCompareResponse;
import com.fsd.admin.vo.AdminAnalyticsChainKpiResponse;
import com.fsd.admin.vo.AdminAnalyticsChargingOverviewResponse;
import com.fsd.admin.vo.AdminAnalyticsDailySummaryResponse;
import com.fsd.admin.vo.AdminAnalyticsEfficiencyResponse;
import com.fsd.admin.vo.AdminAnalyticsExceptionResponse;
import com.fsd.admin.vo.AdminAnalyticsParkCompareItem;
import java.util.List;
import com.fsd.common.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/analytics")
@Tag(name = "Analytics", description = "Efficiency, KPI, peak compare, and report export")
@SecurityRequirement(name = "adminToken")
public class AdminAnalyticsController {

    private final AnalyticsAdminService analyticsAdminService;

    public AdminAnalyticsController(AnalyticsAdminService analyticsAdminService) {
        this.analyticsAdminService = analyticsAdminService;
    }

    @GetMapping("/efficiency")
    @Operation(summary = "Efficiency metrics", description = "Task completion rate, average duration, and utilization by period")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Efficiency data returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiResponse<AdminAnalyticsEfficiencyResponse> efficiency(
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(required = false) Long parkId,
            HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(analyticsAdminService.getEfficiency(period, parkId));
    }

    @GetMapping("/exceptions")
    @Operation(summary = "Exception analysis", description = "Exception counts, trends, and severity distribution")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Exception analysis returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiResponse<AdminAnalyticsExceptionResponse> exceptions(
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(required = false) Long parkId,
            HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(analyticsAdminService.getExceptionAnalysis(period, parkId));
    }

    @GetMapping("/daily-summary")
    @Operation(summary = "Daily summary", description = "Aggregated metrics for a single day")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Daily summary returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiResponse<AdminAnalyticsDailySummaryResponse> dailySummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long parkId,
            HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(analyticsAdminService.getDailySummary(date, parkId));
    }

    @GetMapping("/charging")
    @Operation(summary = "Charging overview", description = "Charging pile utilization and session statistics")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Charging overview returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiResponse<AdminAnalyticsChargingOverviewResponse> chargingOverview(HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(analyticsAdminService.getChargingOverview());
    }

    @GetMapping("/park-comparison")
    @Operation(summary = "Cross-park comparison", description = "Side-by-side efficiency metrics across all parks")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Comparison data returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiResponse<List<AdminAnalyticsParkCompareItem>> parkComparison(
            @RequestParam(defaultValue = "week") String period,
            HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(analyticsAdminService.getParkComparison(period));
    }

    @GetMapping("/chain-kpi")
    @Operation(summary = "Chain KPI", description = "End-to-end dispatch chain key performance indicators")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Chain KPI returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiResponse<AdminAnalyticsChainKpiResponse> chainKpi(
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(required = false) Long parkId,
            HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(analyticsAdminService.getChainKpi(period, parkId));
    }

    @GetMapping("/peak-compare")
    @Operation(summary = "Peak period comparison", description = "Compare metrics between peak and off-peak hours")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Peak comparison returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiResponse<AdminPeakCompareResponse> peakCompare(
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(required = false) Long parkId,
            HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        return ApiResponse.success(analyticsAdminService.getPeakCompare(period, parkId));
    }

    @GetMapping("/export/pdf")
    @Operation(summary = "Export daily report as PDF")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "PDF file returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public void exportPdf(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                          @RequestParam(required = false) Long parkId,
                          HttpServletRequest httpRequest,
                          HttpServletResponse response) throws IOException {
        AdminAuthSupport.requireAuth(httpRequest);
        byte[] pdf = analyticsAdminService.exportPdf(date, parkId);
        String filename = "daily-report-" + (date == null ? LocalDate.now() : date) + ".pdf";
        response.setContentType(MediaType.APPLICATION_PDF_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        response.getOutputStream().write(pdf);
    }

    @GetMapping("/export/csv")
    @Operation(summary = "Export analytics dataset as CSV")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "CSV file returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public void exportCsv(@RequestParam String dataset,
                          @RequestParam(defaultValue = "week") String period,
                          HttpServletRequest httpRequest,
                          HttpServletResponse response) throws IOException {
        AdminAuthSupport.requireAuth(httpRequest);
        String csv = analyticsAdminService.exportCsv(dataset, period);
        String filename = dataset + "-" + period + ".csv";
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        response.getWriter().write(csv);
    }
}
