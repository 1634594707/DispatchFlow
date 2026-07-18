#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
叠石桥家纺城坐标基准换算与校验脚本。
- 实现 WGS-84 <-> GCJ-02 <-> BD-09 标准换算（中国坐标系）。
- 用 poi86 公开实测的"同点四系统"坐标校验算法精度。
- 输出关键锚点的三套坐标系坐标，供 DispatchFlow 站点落库参照。

坐标系说明：
  WGS-84 : GPS / OpenStreetMap / Leaflet 默认
  GCJ-02 : 国测局火星坐标，高德/腾讯/天地图底图
  BD-09  : 百度坐标
"""
import math

A = 6378245.0
EE = 0.00669342162296594323


def out_of_china(lng, lat):
    return not (73.66 < lng < 135.05 and 3.86 < lat < 53.55)


def _t_lat(x, y):
    ret = -100.0 + 2.0*x + 3.0*y + 0.2*y*y + 0.1*x*y + 0.2*math.sqrt(abs(x))
    ret += (20.0*math.sin(6.0*x*math.pi) + 20.0*math.sin(2.0*x*math.pi)) * 2.0/3.0
    ret += (20.0*math.sin(y*math.pi) + 40.0*math.sin(y/3.0*math.pi)) * 2.0/3.0
    ret += (160.0*math.sin(y/12.0*math.pi) + 320.0*math.sin(y*math.pi/30.0)) * 2.0/3.0
    return ret


def _t_lng(x, y):
    ret = 300.0 + x + 2.0*y + 0.1*x*x + 0.1*x*y + 0.1*math.sqrt(abs(x))
    ret += (20.0*math.sin(6.0*x*math.pi) + 20.0*math.sin(2.0*x*math.pi)) * 2.0/3.0
    ret += (20.0*math.sin(x*math.pi) + 40.0*math.sin(x/3.0*math.pi)) * 2.0/3.0
    ret += (150.0*math.sin(x/12.0*math.pi) + 300.0*math.sin(x/30.0*math.pi)) * 2.0/3.0
    return ret


def wgs84_to_gcj02(lng, lat):
    if out_of_china(lng, lat):
        return lng, lat
    dlat = _t_lat(lng-105.0, lat-35.0)
    dlng = _t_lng(lng-105.0, lat-35.0)
    radlat = lat/180.0*math.pi
    magic = math.sin(radlat)
    magic = 1 - EE*magic*magic
    sqrtmagic = math.sqrt(magic)
    dlat = (dlat*180.0)/((A*(1-EE))/(magic*sqrtmagic)*math.pi)
    dlng = (dlng*180.0)/(A/sqrtmagic*math.cos(radlat)*math.pi)
    return lng + dlng, lat + dlat


def gcj02_to_wgs84(lng, lat):
    glng, glat = wgs84_to_gcj02(lng, lat)
    return lng*2 - glng, lat*2 - glat


def gcj02_to_bd09(lng, lat):
    z = math.sqrt(lng*lng + lat*lat) + 0.00002*math.sin(lat*math.pi)
    theta = math.atan2(lat, lng) + 0.000003*math.cos(lng*math.pi)
    return z*math.cos(theta) + 0.0065, z*math.sin(theta) + 0.006


def bd09_to_gcj02(lng, lat):
    x = lng - 0.0065
    y = lat - 0.006
    z = math.sqrt(x*x + y*y) - 0.00002*math.sin(y*math.pi)
    theta = math.atan2(y, x) - 0.000003*math.cos(x*math.pi)
    return z*math.cos(theta), z*math.sin(theta)


def haversine(lng1, lat1, lng2, lat2):
    R = 6371000.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp = math.radians(lat2-lat1)
    dl = math.radians(lng2-lng1)
    a = math.sin(dp/2)**2 + math.cos(p1)*math.cos(p2)*math.sin(dl/2)**2
    return 2*R*math.asin(math.sqrt(a))


print("=" * 70)
print("1) 算法校验：用 poi86 公开实测'同点四系统'坐标 (叠石桥步行街)")
print("   实测 WGS-84 = 121.074624, 31.966346")
print("   实测 GCJ-02 = 121.079292, 31.964544")
print("   实测 BD-09  = 121.085710, 31.970911")
print("-" * 70)
w_lng, w_lat = 121.074624, 31.966346
g_lng, g_lat = wgs84_to_gcj02(w_lng, w_lat)
b_lng, b_lat = gcj02_to_bd09(g_lng, g_lat)
print(f"   算法 WGS-84->GCJ-02 = {g_lng:.6f}, {g_lat:.6f}  (误差 {haversine(g_lng,g_lat,121.079292,31.964544):.1f} m)")
print(f"   算法 GCJ-02->BD-09  = {b_lng:.6f}, {b_lat:.6f}  (误差 {haversine(b_lng,b_lat,121.085710,31.970911):.1f} m)")

print()
print("=" * 70)
print("2) 关键锚点三系统坐标")
print("-" * 70)

anchors = [
    # (名称, 已知系统, lng, lat, 备注)
    ("叠石桥国际家纺城(主市场·腾讯GCJ-02)", "gcj02", 121.076301, 31.966722, "大岛路88号"),
    ("叠石桥国际家纺城·西门(腾讯GCJ-02)", "gcj02", 121.073272, 31.967058, "叠林路×大岛路"),
    ("深国际·综合物流港(腾讯GCJ-02)", "gcj02", 121.101561, 31.91841, "茅珵路/S336×叠港公路"),
    ("三星镇(海门区)镇中心(Nominatim WGS-84)", "wgs84", 121.1105422, 31.9679393, "行政区划质心"),
    ("温馨网咖·叠石桥步行街(poi86 WGS-84 实测)", "wgs84", 121.074624, 31.966346, "市场内实测基准点"),
]

for name, sys_, lng, lat, note in anchors:
    if sys_ == "gcj02":
        g = (lng, lat)
        w = gcj02_to_wgs84(lng, lat)
        b = gcj02_to_bd09(lng, lat)
    else:  # wgs84
        w = (lng, lat)
        g = wgs84_to_gcj02(lng, lat)
        b = gcj02_to_bd09(g[0], g[1])
    print(f"\n  【{name}】  ({note})")
    print(f"    WGS-84 : {w[1]:.6f}, {w[0]:.6f}")
    print(f"    GCJ-02 : {g[1]:.6f}, {g[0]:.6f}")
    print(f"    BD-09  : {b[1]:.6f}, {b[0]:.6f}")

print()
print("=" * 70)
print("3) 与前期报告 P0 风险关联：坐标系统一建议")
print("-" * 70)
print("   - 项目代码 coordLng/coordLat 标注为 GCJ-02；前端 Leaflet 默认 WGS-84。")
print("   - 若底图用高德/腾讯/天地图(GCJ-02)：存储 GCJ-02，无需转换，零漂移。")
print("   - 若底图用 OSM/Leaflet 默认(WGS-84)：存储 GCJ-02 并在渲染层转 WGS-84。")
print("   - 切勿两套坐标混存后又直接做欧氏/Haversine 距离比较（路线 4.6 风险）。")
