package com.fsd.admin.service;

import com.fsd.admin.vo.AdminAnalyticsChainKpiResponse;
import com.fsd.admin.vo.AdminAnalyticsChargingOverviewResponse;
import com.fsd.admin.vo.AdminAnalyticsDailySummaryResponse;
import com.fsd.admin.vo.AdminAnalyticsEfficiencyResponse;
import com.fsd.admin.vo.AdminAnalyticsEnergyForecastResponse;
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

    default AdminAnalyticsChargingOverviewResponse getChargingOverview(Long parkId) {
        return getChargingOverview();
    }

    String exportCsv(String dataset, String period, Long parkId);

    List<AdminAnalyticsParkCompareItem> getParkComparison(String period);

    AdminAnalyticsChainKpiResponse getChainKpi(String period, Long parkId);

    AdminPeakCompareResponse getPeakCompare(String period, Long parkId);

    /**
     * 补能需求预测 24 小时剖面（ALG-FC，只读）。
     *
     * @param date   预测日期，为空取当天
     * @param parkId 园区，为空取默认园区
     */
    AdminAnalyticsEnergyForecastResponse getEnergyForecast(LocalDate date, Long parkId);

    byte[] exportPdf(LocalDate date, Long parkId);
}
