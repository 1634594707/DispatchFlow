package com.fsd.admin.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * P1-1：站点×小时订单需求聚合。
 * 口径：站点 = 订单取货节点编码（{@code pickupNodeCode}，空记"未知"）；
 * 小时 = {@code createdAt} 本地小时 0–23；计数 = 统计窗口内的订单数。
 * 页面（运营分析）与导出（dataset=station-hourly）共用同一条计算路径。
 */
@Data
@Builder
public class AdminAnalyticsStationHourResponse {

    private String period;

    private List<Row> rows;

    @Data
    @Builder
    public static class Row {

        private String station;

        private int hour;

        private long orders;
    }
}
