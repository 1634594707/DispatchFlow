package com.fsd.admin.controller;

import com.fsd.admin.service.AnalyticsAdminService;
import com.fsd.admin.vo.AdminAnalyticsChargingOverviewResponse;
import com.fsd.admin.vo.AdminAnalyticsDailySummaryResponse;
import com.fsd.admin.vo.AdminAnalyticsEfficiencyResponse;
import com.fsd.admin.vo.AdminAnalyticsExceptionResponse;
import com.fsd.admin.vo.AdminAnalyticsParkCompareItem;
import java.util.List;
import com.fsd.common.model.ApiResponse;
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
public class AdminAnalyticsController {

    private final AnalyticsAdminService analyticsAdminService;

    public AdminAnalyticsController(AnalyticsAdminService analyticsAdminService) {
        this.analyticsAdminService = analyticsAdminService;
    }

    @GetMapping("/efficiency")
    public ApiResponse<AdminAnalyticsEfficiencyResponse> efficiency(
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(required = false) Long parkId) {
        return ApiResponse.success(analyticsAdminService.getEfficiency(period, parkId));
    }

    @GetMapping("/exceptions")
    public ApiResponse<AdminAnalyticsExceptionResponse> exceptions(
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(required = false) Long parkId) {
        return ApiResponse.success(analyticsAdminService.getExceptionAnalysis(period, parkId));
    }

    @GetMapping("/daily-summary")
    public ApiResponse<AdminAnalyticsDailySummaryResponse> dailySummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long parkId) {
        return ApiResponse.success(analyticsAdminService.getDailySummary(date, parkId));
    }

    @GetMapping("/charging")
    public ApiResponse<AdminAnalyticsChargingOverviewResponse> chargingOverview() {
        return ApiResponse.success(analyticsAdminService.getChargingOverview());
    }

    @GetMapping("/park-comparison")
    public ApiResponse<List<AdminAnalyticsParkCompareItem>> parkComparison(
            @RequestParam(defaultValue = "week") String period) {
        return ApiResponse.success(analyticsAdminService.getParkComparison(period));
    }

    @GetMapping("/export/csv")
    public void exportCsv(@RequestParam String dataset,
                          @RequestParam(defaultValue = "week") String period,
                          HttpServletResponse response) throws IOException {
        String csv = analyticsAdminService.exportCsv(dataset, period);
        String filename = dataset + "-" + period + ".csv";
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        response.getOutputStream().write(("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8));
    }
}
