package com.fsd.order.dto;

import java.math.BigDecimal;

/**
 * 受理阶段解析出的可派单端点，由派单侧（能读到路网图的那一侧）算好后交给下单侧落库。
 *
 * <p>刻意与 {@link OrderCreateRequest} 分开：原始坐标是**客户端给的**，吸附结果是**服务端量的**。
 * 混在一个 DTO 里就等于让外部调用方能自称"我这个点吸附到了 OSM0017"，绕过可达性判定。
 *
 * @param pickupNodeCode    取货端吸附到的路网节点；null 表示该端走站点，不需要吸附
 * @param dropoffNodeCode   送货端吸附到的路网节点
 * @param pickupSnapMeters  取货端吸附距离（米）；用于事后校准吸附半径
 * @param dropoffSnapMeters 送货端吸附距离（米）
 */
public record OrderEndpointResolution(
        String pickupNodeCode,
        String dropoffNodeCode,
        BigDecimal pickupSnapMeters,
        BigDecimal dropoffSnapMeters) {
}
