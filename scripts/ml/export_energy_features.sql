-- ALG-FC 特征导出：站点 × 小时的补能需求序列。
--
-- 输出列（CSV，UTF-8，含表头）：
--   park_id, station_id, station_code, slot_start, arrivals, energy_kwh
--
-- arrivals    = 该小时在该补能站开始的充电会话数（补能需求）
-- energy_kwh  = 该小时会话的 SOC 增量总和（能耗序列代理量）
--
-- 归属规则：t_charging_pile 未直接关联站点，故按"最近充电站"（t_station.station_type =
-- 'CHARGING_STATION'）做空间归属，使用 MySQL 5.7.6+ 的 ST_Distance_Sphere。
--
-- 用法（示例）：
--   mysql -h127.0.0.1 -P3306 -uroot -p fsd_core --batch --raw --default-character-set=utf8mb4 \
--     -e "source scripts/ml/export_energy_features.sql" > reports/energy_features.csv
--
-- 本查询只读，不写任何业务表。

USE `fsd_core`;

SELECT
    st.park_id                                   AS park_id,
    st.id                                        AS station_id,
    st.station_code                              AS station_code,
    DATE_FORMAT(cs.start_time, '%Y-%m-%d %H:00:00') AS slot_start,
    COUNT(*)                                     AS arrivals,
    COALESCE(SUM(GREATEST(COALESCE(cs.end_soc, cs.start_soc) - cs.start_soc, 0)), 0) AS energy_kwh
FROM t_charging_session cs
JOIN t_charging_pile cp ON cp.id = cs.charging_pile_id
JOIN t_parking_slot ps  ON ps.id = cp.parking_slot_id
JOIN t_station st ON st.id = (
        SELECT st2.id
        FROM t_station st2
        WHERE st2.deleted = 0
          AND st2.station_type = 'CHARGING_STATION'
          AND st2.park_id = ps.park_id
          AND st2.coord_lng IS NOT NULL
          AND st2.coord_lat IS NOT NULL
        ORDER BY ST_Distance_Sphere(
                     POINT(ps.coord_lng, ps.coord_lat),
                     POINT(st2.coord_lng, st2.coord_lat)) ASC
        LIMIT 1
    )
WHERE cs.deleted = 0
  AND cs.start_time IS NOT NULL
  AND cs.session_status IN ('ACTIVE', 'COMPLETED')
  AND ps.coord_lng IS NOT NULL
  AND ps.coord_lat IS NOT NULL
GROUP BY st.park_id, st.id, st.station_code, DATE_FORMAT(cs.start_time, '%Y-%m-%d %H:00:00')
ORDER BY st.id, slot_start;
