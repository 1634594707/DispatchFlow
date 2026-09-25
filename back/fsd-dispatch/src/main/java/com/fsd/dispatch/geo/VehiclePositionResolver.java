package com.fsd.dispatch.geo;

import com.fsd.common.enums.VehicleLinkMode;
import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import com.fsd.dispatch.geo.ParkGeoTransformService.ParkPoint;
import com.fsd.vehicle.entity.VehicleEntity;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 车辆位姿的唯一取位口（§7.2 车辆坐标语义接缝）。
 *
 * <p>{@code t_vehicle.current_longitude/current_latitude} 的<b>空间语义按车的接入链路而变</b>，不是全表统一：
 * <ul>
 *   <li>仿真车（{@code linkMode} 为 SIM 或历史空值）由仿真环按 schematic 像素 x/y 写入；</li>
 *   <li>真车的上报链路写入 GCJ-02（见 {@code VehicleServiceImpl#updateSnapshot} 的 WGS-84→GCJ-02 转换）。</li>
 * </ul>
 * 因此判定必须是<b>逐行</b>的，且判据与写入侧共用 {@link VehicleLinkMode#isSimulated(String)} 这一条规则；
 * 早先设想的"全局开关 + 一次性迁移"是错的设计：它只能表达两种语义之一，翻转那天真车路会反过来坏掉。
 *
 * <p>本类把这条规则收敛到一处，两个出口按<b>消费者所在的空间</b>命名：
 * <ul>
 *   <li>{@link #toPark(VehicleEntity)} —— 像素空间消费者（{@code nearestNode}/{@code buildRoute}/
 *       {@code isReachable}/MAPF/热力分桶/轨迹像素）；</li>
 *   <li>{@link #toGeo(VehicleEntity)} —— 经纬度空间消费者（haversine、快照对外位置、热力球）。</li>
 * </ul>
 * 换算域未配置（{@code reference-points} 缺失）时 {@code fromGcj02}/{@code toGcj02} 返回空，
 * 调用方按"位置未知"处理，而不是把另一种空间的数值当成自己的喂进路网。
 */
@Service
public class VehiclePositionResolver {

    private final ParkGeoTransformService parkGeoTransformService;

    public VehiclePositionResolver(ParkGeoTransformService parkGeoTransformService) {
        this.parkGeoTransformService = parkGeoTransformService;
    }

    /** 该车的坐标列是否存 schematic 像素（仿真链路），否则存 GCJ-02。 */
    public boolean storesSchematicXy(VehicleEntity vehicle) {
        return VehicleLinkMode.isSimulated(vehicle.getLinkMode());
    }

    /** 像素位（schematic x/y），供路由/吸附/渲染。列缺失或换算域不可用时返回空。 */
    public Optional<ParkPoint> toPark(VehicleEntity vehicle) {
        return toPark(vehicle.getLinkMode(), vehicle.getCurrentLongitude(), vehicle.getCurrentLatitude());
    }

    /** 同上，供只有 DTO（非实体）的消费者复用同一条判定。 */
    public Optional<ParkPoint> toPark(String linkMode, BigDecimal lng, BigDecimal lat) {
        if (lng == null || lat == null) {
            return Optional.empty();
        }
        if (VehicleLinkMode.isSimulated(linkMode)) {
            return Optional.of(new ParkPoint(lng, lat));
        }
        return parkGeoTransformService.fromGcj02(lng, lat);
    }

    /** GCJ-02 位（真实经纬度），供 haversine 距离与对外位置。 */
    public Optional<GeoPoint> toGeo(VehicleEntity vehicle) {
        return toGeo(vehicle.getLinkMode(), vehicle.getCurrentLongitude(), vehicle.getCurrentLatitude());
    }

    public Optional<GeoPoint> toGeo(String linkMode, BigDecimal lng, BigDecimal lat) {
        if (lng == null || lat == null) {
            return Optional.empty();
        }
        if (!VehicleLinkMode.isSimulated(linkMode)) {
            return Optional.of(new GeoPoint(lng, lat));
        }
        return parkGeoTransformService.toGcj02(lng, lat);
    }
}
