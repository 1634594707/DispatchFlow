package com.fsd.dispatch.geo.local;



import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;

import java.math.BigDecimal;

import java.math.RoundingMode;

import java.util.List;



/**

 * L1 试点禁行面域：产业园建筑块与服务场区（M8-R8）。

 * 坐标与 {@link LocalPilotRoadGraphService} 道路网格对齐；道路走廊保留约 20m 缓冲。

 */

public final class PilotForbiddenZones {



    private static final double ROAD_BUFFER = 0.00018;



    /** 中央服务场区禁行（对应旧版河道禁行语义）。 */

    public static final List<GeoPoint> SERVICE_YARD = block(

            121.076500, 31.962100, 121.078000, 31.962600);



    /** 道路网格之间的建筑块（2 行 × 3 列，东西向产业园网格）。 */

    public static final List<List<GeoPoint>> BUILDING_BLOCKS = List.of(

            cell(121.072862, 121.074980, 31.960826, 31.961797),

            cell(121.075340, 121.078992, 31.960826, 31.961797),

            cell(121.079332, 121.087842, 31.960826, 31.961797),

            cell(121.072862, 121.074980, 31.962157, 31.963343),

            cell(121.075340, 121.078992, 31.962157, 31.963343),

            cell(121.079332, 121.087842, 31.962157, 31.963343));



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

