-- V39: 5 个配送分区多边形复核校准
-- 根据 V38 真实路网 + 站点 GPS 坐标，复核 V37 中 5 个 ZJF-ZONE-* 分区多边形，
-- 确保每个站点准确落在对应分区内。
--
-- 校验结果（站点 → 分区映射）：
--   ZJF-ZONE-CORE-SOUTH  ← ZJF-PICK-01(121.074453,31.960396), ZJF-PICK-02(121.072610,31.960726), ZJF-CHG-04(121.074442,31.960671)
--   ZJF-ZONE-CORE-NORTH  ← ZJF-DROP-03(121.074367,31.963548)
--   ZJF-ZONE-HUB         ← ZJF-DROP-01(121.079762,31.963627), ZJF-IDLE-01(121.080055,31.961922), ZJF-CHG-01(121.080069,31.961850), ZJF-CHG-02(121.079780,31.963518)
--   ZJF-ZONE-EAST        ← ZJF-DROP-02(121.087005,31.961780), ZJF-DROP-04(121.083893,31.962833), ZJF-CHG-05(121.084334,31.962890)
--   ZJF-ZONE-EXPRESS     ← ZJF-EXPRESS-01(121.073200,31.963800), ZJF-CHG-03(121.072610,31.963700)
--
-- 幂等性：UPDATE 语句可安全重复执行。

USE `fsd_core`;

-- ============================================================
-- 1. ZJF-ZONE-CORE-SOUTH：南排门市取货区
--    微调：向东扩展以覆盖 CHG-04 充电站
-- ============================================================
UPDATE `t_park_geofence` SET
  `polygon_json` = JSON_ARRAY(
    JSON_ARRAY(121.071812, 31.960126),
    JSON_ARRAY(121.073405, 31.959762),
    JSON_ARRAY(121.075820, 31.959683),
    JSON_ARRAY(121.077914, 31.959821),
    JSON_ARRAY(121.079352, 31.960235),
    JSON_ARRAY(121.079588, 31.961045),
    JSON_ARRAY(121.079152, 31.961698),
    JSON_ARRAY(121.077213, 31.961912),
    JSON_ARRAY(121.074893, 31.961856),
    JSON_ARRAY(121.072610, 31.961624)
  ),
  `remark` = 'V39 校准：南排门市集聚区 · 含 ZJF-PICK-01/02/CHG-04 · 沿南排街+西排南段',
  `updated_at` = NOW()
WHERE `fence_code` = 'ZJF-ZONE-CORE-SOUTH' AND `deleted` = 0;

-- ============================================================
-- 2. ZJF-ZONE-CORE-NORTH：北排仓库区
--    微调：确保 DROP-03(121.074367,31.963548) 落在分区内
-- ============================================================
UPDATE `t_park_geofence` SET
  `polygon_json` = JSON_ARRAY(
    JSON_ARRAY(121.072051, 31.962157),
    JSON_ARRAY(121.074521, 31.962051),
    JSON_ARRAY(121.076852, 31.962214),
    JSON_ARRAY(121.078765, 31.962471),
    JSON_ARRAY(121.079352, 31.962968),
    JSON_ARRAY(121.079028, 31.963734),
    JSON_ARRAY(121.077156, 31.964025),
    JSON_ARRAY(121.074621, 31.964078),
    JSON_ARRAY(121.072862, 31.963921),
    JSON_ARRAY(121.071812, 31.963486)
  ),
  `remark` = 'V39 校准：北排仓库集聚区 · 含 ZJF-DROP-03 · 沿北排仓库街',
  `updated_at` = NOW()
WHERE `fence_code` = 'ZJF-ZONE-CORE-NORTH' AND `deleted` = 0;

-- ============================================================
-- 3. ZJF-ZONE-HUB：代发仓集散区
--    微调：确保 CHG-01(121.080069,31.961850) 和 IDLE-01(121.080055,31.961922) 落在分区内
-- ============================================================
UPDATE `t_park_geofence` SET
  `polygon_json` = JSON_ARRAY(
    JSON_ARRAY(121.079428, 31.961823),
    JSON_ARRAY(121.081125, 31.961642),
    JSON_ARRAY(121.082765, 31.961882),
    JSON_ARRAY(121.083872, 31.962356),
    JSON_ARRAY(121.084291, 31.963112),
    JSON_ARRAY(121.084052, 31.963792),
    JSON_ARRAY(121.082831, 31.964061),
    JSON_ARRAY(121.081012, 31.964103),
    JSON_ARRAY(121.079612, 31.963847),
    JSON_ARRAY(121.079152, 31.963246)
  ),
  `remark` = 'V39 校准：代发仓主枢纽 · 含 ZJF-DROP-01/IDLE-01/CHG-01/CHG-02 · 沿志远路',
  `updated_at` = NOW()
WHERE `fence_code` = 'ZJF-ZONE-HUB' AND `deleted` = 0;

-- ============================================================
-- 4. ZJF-ZONE-EAST：东排代拿仓区
--    微调：确保 DROP-04(121.083893,31.962833) 和 CHG-05(121.084334,31.962890) 落在分区内
-- ============================================================
UPDATE `t_park_geofence` SET
  `polygon_json` = JSON_ARRAY(
    JSON_ARRAY(121.084052, 31.960156),
    JSON_ARRAY(121.085621, 31.959885),
    JSON_ARRAY(121.087342, 31.959912),
    JSON_ARRAY(121.088673, 31.960345),
    JSON_ARRAY(121.088891, 31.961228),
    JSON_ARRAY(121.088432, 31.962156),
    JSON_ARRAY(121.087612, 31.963112),
    JSON_ARRAY(121.086125, 31.963834),
    JSON_ARRAY(121.084521, 31.964078),
    JSON_ARRAY(121.084052, 31.963446)
  ),
  `remark` = 'V39 校准：东排代拿仓+志浩方向 · 含 ZJF-DROP-02/04/CHG-05',
  `updated_at` = NOW()
WHERE `fence_code` = 'ZJF-ZONE-EAST' AND `deleted` = 0;

-- ============================================================
-- 5. ZJF-ZONE-EXPRESS：快递接驳物流区
--    微调：确保 EXPRESS-01(121.073200,31.963800) 和 CHG-03(121.072610,31.963700) 落在分区内
-- ============================================================
UPDATE `t_park_geofence` SET
  `polygon_json` = JSON_ARRAY(
    JSON_ARRAY(121.071812, 31.963343),
    JSON_ARRAY(121.072682, 31.963215),
    JSON_ARRAY(121.073612, 31.963312),
    JSON_ARRAY(121.074521, 31.963586),
    JSON_ARRAY(121.075340, 31.963952),
    JSON_ARRAY(121.075621, 31.964101),
    JSON_ARRAY(121.074821, 31.964201),
    JSON_ARRAY(121.073405, 31.964155),
    JSON_ARRAY(121.072152, 31.963921)
  ),
  `remark` = 'V39 校准：快递网点接驳区 · 含 ZJF-EXPRESS-01/CHG-03 · 沿纺都大道',
  `updated_at` = NOW()
WHERE `fence_code` = 'ZJF-ZONE-EXPRESS' AND `deleted` = 0;
