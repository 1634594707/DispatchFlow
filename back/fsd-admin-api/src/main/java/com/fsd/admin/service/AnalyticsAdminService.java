package com.fsd.admin.service;

import com.fsd.admin.vo.AdminAnalyticsChainKpiResponse;
import com.fsd.admin.vo.AdminAnalyticsChargingOverviewResponse;
import com.fsd.admin.vo.AdminAnalyticsDailySummaryResponse;
import com.fsd.admin.vo.AdminAnalyticsEfficiencyResponse;
import com.fsd.admin.vo.AdminAnalyticsExceptionResponse;
import com.fsd.admin.vo.AdminAnalyticsParkCompareItem;
import com.fsd.admin.vo.AdminPeakCompareResponse;
import java.time.LocalDate;
import java.util.List;

public interface AnalyticsAdminService {

    AdminAnalyticsEfficiencyResponse getEfficiency(String period, Long parkId);

    AdminAnalyticsExceptionResponse getExceptionAnalysis(String period, Long parkId);

    AdminAnalyticsDailySummaryResponse getDailySummary(LocalDate date, Long parkId);

    AdminAnalyticsChargingOverviewResponse getChargingOverview();

    String exportCsv(String dataset, String period);

    List<AdminAnalyticsParkCompareItem> getParkComparison(String period);

    AdminAnalyticsChainKpiResponse getChainKpi(String period, Long parkId);

    AdminPeakCompareResponse getPeakCompare(String period, Long parkId);

    byte[] exportPdf(LocalDate date, Long parkId);
}
