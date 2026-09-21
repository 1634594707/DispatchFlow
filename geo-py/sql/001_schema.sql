-- FSD 调度地理服务 · PostGIS schema
--
-- 坐标系约定（重要）：
--   业务坐标全部是 **GCJ-02**（火星坐标），与 DispatchFlow 的 MySQL 存储、高德底图一致。
--   这里用 SRID 4326 只是把它当作「以度为单位的经纬度容器」，**不代表数据是 WGS-84**。
--   同一坐标系内两点间的距离/包含关系是自洽的（GCJ-02 在园区尺度上的偏移梯度很小），
--   但**绝对位置**与 WGS-84 相差约 300~700m。任何混用都必须走 datum.py 的显式声明。
--
-- 见 docs/坐标基准-叠石桥家纺城.md §1：项目已统一 GCJ-02 存储 + 高德底图，显示零漂移。

CREATE EXTENSION IF NOT EXISTS postgis;

-- 园区
CREATE TABLE IF NOT EXISTS park (
    park_id     BIGINT PRIMARY KEY,
    park_code   TEXT NOT NULL UNIQUE,
    park_name   TEXT NOT NULL
);

-- 站点（含充电桩站点）：GCJ-02 点位
CREATE TABLE IF NOT EXISTS station (
    station_id     BIGSERIAL PRIMARY KEY,
    park_id        BIGINT NOT NULL REFERENCES park (park_id),
    station_code   TEXT NOT NULL,
    station_name   TEXT NOT NULL,
    station_type   TEXT NOT NULL,
    status         TEXT NOT NULL,
    coord_lng      DOUBLE PRECISION NOT NULL,
    coord_lat      DOUBLE PRECISION NOT NULL,
    is_charging    BOOLEAN NOT NULL DEFAULT FALSE,
    geom           GEOMETRY (POINT, 4326)
        GENERATED ALWAYS AS (ST_SetSRID (ST_MakePoint (coord_lng, coord_lat), 4326)) STORED,
    UNIQUE (park_id, station_code)
);

-- 半径与 KNN 必须走 geography（否则 ST_DWithin 的单位是「度」而不是米，见 spatial.py
-- 的单位陷阱说明）。而 `geom::geography` 是表达式，Postgres **不会**用它去匹配 `GIST (geom)`——
-- 实测只建 geometry 索引时 EXPLAIN 显示位图扫描 10013 行 + quicksort，索引完全没用上。
-- 必须显式在 geography 表达式上建 GiST：
CREATE INDEX IF NOT EXISTS idx_station_geog ON station USING GIST (geography (geom));

-- 电子围栏：GCJ-02 多边形
CREATE TABLE IF NOT EXISTS geofence (
    fence_id     BIGSERIAL PRIMARY KEY,
    park_id      BIGINT NOT NULL REFERENCES park (park_id),
    fence_code   TEXT NOT NULL,
    fence_name   TEXT NOT NULL,
    fence_type   TEXT NOT NULL,          -- BOUNDARY=越界告警, RESTRICTED=禁入告警
    status       TEXT NOT NULL,
    geom         GEOMETRY (POLYGON, 4326) NOT NULL,
    UNIQUE (park_id, fence_code)
);

CREATE INDEX IF NOT EXISTS idx_geofence_geom ON geofence USING GIST (geom);
CREATE INDEX IF NOT EXISTS idx_geofence_park_status ON geofence (park_id, status);

-- 路网节点：A*/Dijkstra 的图顶点，供 ST_DWithin 做「最近节点吸附」
CREATE TABLE IF NOT EXISTS road_node (
    node_id    BIGSERIAL PRIMARY KEY,
    park_id    BIGINT NOT NULL REFERENCES park (park_id),
    node_code  TEXT NOT NULL,
    coord_lng  DOUBLE PRECISION NOT NULL,
    coord_lat  DOUBLE PRECISION NOT NULL,
    geom       GEOMETRY (POINT, 4326)
        GENERATED ALWAYS AS (ST_SetSRID (ST_MakePoint (coord_lng, coord_lat), 4326)) STORED,
    UNIQUE (park_id, node_code)
);

-- 同 station：吸附查询走 geography，索引也必须建在 geography 表达式上
CREATE INDEX IF NOT EXISTS idx_road_node_geog ON road_node USING GIST (geography (geom));

-- 同步水位：记录每个批次从 Flyway 迁移里导入了多少行，供幂等重跑与对账
CREATE TABLE IF NOT EXISTS sync_batch (
    batch_id     BIGSERIAL PRIMARY KEY,
    source_file  TEXT NOT NULL,
    entity_kind  TEXT NOT NULL,
    row_count    INTEGER NOT NULL,
    finished_at  TIMESTAMPTZ NOT NULL DEFAULT now (),
    UNIQUE (source_file, entity_kind)
);
