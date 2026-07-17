package com.fsd.dispatch.geo.local;



import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;

import java.math.BigDecimal;

import java.math.RoundingMode;

import java.util.List;



/**
 * L1 试点禁行面域：叠石桥家纺城建筑块与服务场区（V37 分区版）。
 * 坐标与 {@link LocalPilotRoadGraphService} 道路网格对齐；道路走廊保留约 20m 缓冲。
 * 基于 V37 五大配送分区（CORE-SOUTH/CORE-NORTH/HUB/EAST/EXPRESS）的真实建筑分布。
 */
public final class PilotForbiddenZones {

    private static final double ROAD_BUFFER = 0.00018;

    /** 中央服务场区禁行（代发仓装卸区 · CORE-NORTH 与 HUB 交界）。 */
    public static final List<GeoPoint> SERVICE_YARD = block(
            121.076500, 31.962100, 121.078000, 31.962600);

    /**
     * 道路网格之间的建筑块（按 V37 五大配送分区布局）：
     * - CORE-SOUTH 南排门市：2 块（西/中）
     * - CORE-NORTH 北排仓库：2 块（西/中）
     * - HUB 代发仓集散区：1 块（大型仓）
     * - EAST 东排代拿仓：1 块
     * - EXPRESS 快递接驳区：1 块（西北角快递网点）
     */
    public static final List<List<GeoPoint>> BUILDING_BLOCKS = List.of(
            // CORE-SOUTH 南排门市建筑（西块）
            cell(121.072862, 121.074980, 31.960826, 31.961797),
            // CORE-SOUTH 南排门市建筑（中块）
            cell(121.075340, 121.078992, 31.960826, 31.961797),
            // CORE-NORTH 北排仓库建筑（西块）
            cell(121.072862, 121.074980, 31.962157, 31.963343),
            // CORE-NORTH 北排仓库建筑（中块）
            cell(121.075340, 121.078992, 31.962157, 31.963343),
            // HUB 代发仓大型建筑（中部枢纽）
            cell(121.079332, 121.083500, 31.961950, 31.964101),
            // EAST 东排代拿仓建筑
            cell(121.084500, 121.087842, 31.960826, 31.963343),
            // EXPRESS 快递网点建筑（西北角小地块）
            cell(121.072862, 121.074980, 31.963343, 31.964101));

    public static final List<List<GeoPoint>> RIVER_ZONES = List.of(SERVICE_YARD);



    private PilotForbiddenZones() {

    }



    private static List<GeoPoint> cell(double westLng, double eastLng, double southLat, double northLat) {

        double west = westLng + ROAD_BUFFER;

        double south = southLat + ROAD_BUFFER;

        double east = eastLng - ROAD_BUFFER;

        double north = northLat - ROAD_BUFFER;

        if (east <= west || north <= south) {

            return List.of();

        }

        return block(west, south, east, north);

    }



    private static List<GeoPoint> block(double westLng, double southLat, double eastLng, double northLat) {

        return List.of(

                g(westLng, southLat),

                g(eastLng, southLat),

                g(eastLng, northLat),

                g(westLng, northLat));

    }



    public static GeoPoint g(double lng, double lat) {

        return new GeoPoint(

                BigDecimal.valueOf(lng).setScale(6, RoundingMode.HALF_UP),

                BigDecimal.valueOf(lat).setScale(6, RoundingMode.HALF_UP));

    }

}

