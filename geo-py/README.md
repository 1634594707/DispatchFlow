# geo-py · 调度地理服务（PostGIS）

把 DispatchFlow 里用 Java 手算的空间判断搬到 PostGIS，并**逐点证明两者等价**。

对应 JD 技术栈里的 `Python 3` 与 `PostGIS` 两项。完整背景、陷阱与边界见
`../docs/实施记录-PostGIS地理服务-2026-09-20.md`。

## 三条能力

| 能力 | PostGIS | 原 Java |
|---|---|---|
| 点在围栏内 | `ST_Contains` | `GeoPolygonUtils.contains()` 射线法 |
| 最近站点召回 | `ST_DWithin(geography)` + `<->` KNN | 全表扫描逐个 `haversineMeters` 取最小 |
| 球面距离 | `ST_DistanceSphere` / `ST_Distance(geography)` | `haversineMeters()` 球面 R=6371000 |
| 路网节点吸附 | `ST_DWithin` + KNN | 寻路侧内存图吸附 |

## 坐标系（读代码前必须知道）

库内几何全部按 **GCJ-02** 存储，与 DispatchFlow 的 MySQL 和高德底图一致。
SRID 4326 在这里只是「以度为单位的容器」，**不代表数据是 WGS-84**。

- 同坐标系内两点距离/包含关系自洽（园区尺度上 GCJ-02 偏移梯度很小）
- 但**绝对位置**与 WGS-84 相差约 300~700m
- 所以 `datum.py` 对非 GCJ-02 输入**直接 400 拒绝**，而不是静默算出一个偏移结果
- 换算**不在本服务实现**——唯一真相源是 Java 的 `com.fsd.common.geo.Wgs84Gcj02Converter`（含单测）。
  抄第二份只会产生两个会漂移的实现

## 快速开始

```bash
docker compose up -d postgis                       # 5433，避开本机 5432 上的 PG18
docker exec -i fsd-geo-postgis psql -U postgres -d fsd_geo < sql/001_schema.sql
uv venv .venv --python 3.12
uv pip install --python .venv/Scripts/python.exe -e ".[dev]"
GEO_PG_PASSWORD=*** python scripts/seed_from_migrations.py   # 从 Flyway 迁移灌真实 ZJF 数据
GEO_PG_PASSWORD=*** python -m pytest -p no:warnings -q        # 39 passed
python -m uvicorn fsd_geo.app:app --port 8090
```

## 端点

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/health` | PostGIS 版本 + 存储坐标系 |
| POST | `/geo/fence/contains` | 点命中哪些 ACTIVE 围栏 |
| POST | `/geo/fence/segment-intersects` | 线段是否穿入围栏 |
| POST | `/geo/station/nearby` | 半径内最近 N 站点，可只要充电桩 |
| POST | `/geo/road-node/nearest` | 最近路网节点吸附 |
| POST | `/geo/distance/compare` | **三口径对照**：PostGIS 椭球 / PostGIS 球面 / Java Haversine |

## 数据从哪来

`scripts/seed_from_migrations.py` 直接解析 `../back/sql/migrations/V*.sql`，
而不是连 MySQL。理由：迁移脚本是 schema 的**唯一真相源**、离线可重放，
且开发机不一定有跑得起来的 MySQL（本次就是这样）。

解析**按列名映射不按位置**——16 处 `t_station` INSERT 的列顺序并不统一，
按位置解析会在下次加列时静默错位。解析不了的**跳过并计数上报，不猜**。

当前结果：13 站点 / 6 围栏 / 55 路网节点，39 条跳过
（全是 V04/V09/V16 时代只有平面 `coord_x/coord_y`、没有 GCJ-02 经纬度的历史数据）。

## ⚠️ 两个已踩过的坑（改 SQL 前先看）

1. **`ST_DWithin` 在 `geometry` 上单位是度不是米。** 半径必须走 `::geography`。
2. **`geom::geography` 会让 `GIST (geom)` 索引失效。** 必须建 `GIST (geography(geom))` 表达式索引，
   且**改完必须 EXPLAIN 验证**——第一版就是没验证，索引白建，10013 行走位图扫描 + quicksort。

## 测试为什么这么设计

`tests/test_java_parity.py` 把 `GeoPolygonUtils.contains()` 逐行移植成 Python，
对每张真实围栏的外接矩形打 **60×60 网格**逐点比对 `ST_Contains`，要求 100% 一致。

- 用网格不用随机点：随机点在凸多边形上几乎全落内部，测不出边界差异
- 选 `ST_Contains` 不选 `ST_Covers`：前者把边界上的点判为不包含，与 Java 射线法同口径
