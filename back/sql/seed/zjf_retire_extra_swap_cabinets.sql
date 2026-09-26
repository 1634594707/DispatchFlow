-- 退役 23 个换电柜：ACTIVE 柜从 35 收到 12（本人 2026-09-25 裁"充电桩有些太多了"）
--
-- 为什么收：`zjf_facility_v2.sql` 铺了 35 个柜位，加上母港 6 根桩与 2 个卫星补能点，
--   大屏上一屏 43 个补能图标，把"车在哪"这件事压掉了。
--
-- 保留哪 12 个（可复现的规则，不是我手挑）：
--   `scripts/geo/swap_cabinet_placement.py` 的布点本身是 greedy farthest-point sampling
--   （k-center 的 2-近似，从基地节点起步）并按**被选中的先后**编号，所以"保留编号前 12 个"
--   等价于"k-center 意义下最分散的 12 个"。实测最近邻间距：
--     保留 01..12 → 最小 2,337 m；保留 13..24 → 1,564 m；保留 24..35 → 1,164 m。
--   复核脚本：tmp/pick-cabs.mjs（对 35 个坐标重跑最远点采样，结果与 01..12 一致）。
--
-- 为什么是 INACTIVE 而不是 DELETE：与设施 v2 同一套做法（`zjf_facility_v2.sql:26-31`），
--   行留着才能回退，历史会话/告警的外键也不悬空。受理与画图都只认 ACTIVE，效果等同于撤掉。
--   remark 用**整串覆盖**而不是 CONCAT 追加 —— 这份 seed 会被反复灌，追加写法每次多拼一段。
--
-- 保留：FSD-SWAP-01, FSD-SWAP-02, FSD-SWAP-03, FSD-SWAP-04, FSD-SWAP-05, FSD-SWAP-06, FSD-SWAP-07, FSD-SWAP-08, FSD-SWAP-09, FSD-SWAP-10, FSD-SWAP-11, FSD-SWAP-12
-- 退役 23 个：FSD-SWAP-13, FSD-SWAP-14, FSD-SWAP-15, FSD-SWAP-16, FSD-SWAP-17, FSD-SWAP-18, FSD-SWAP-19, FSD-SWAP-20, FSD-SWAP-21, FSD-SWAP-22, FSD-SWAP-23, FSD-SWAP-24, FSD-SWAP-25, FSD-SWAP-26, FSD-SWAP-27, FSD-SWAP-28, FSD-SWAP-29, FSD-SWAP-30, FSD-SWAP-31, FSD-SWAP-32, FSD-SWAP-33, FSD-SWAP-34, FSD-SWAP-35
UPDATE t_station
   SET status = 'INACTIVE',
       remark = '2026-09-25 补能网络收密：大屏图标过多，按 k-center 保留前 12 个，本柜退役（行保留可回退）'
 WHERE deleted = 0
   AND station_type = 'SWAP_CABINET'
   AND station_code IN ('FSD-SWAP-13', 'FSD-SWAP-14', 'FSD-SWAP-15', 'FSD-SWAP-16', 'FSD-SWAP-17', 'FSD-SWAP-18', 'FSD-SWAP-19', 'FSD-SWAP-20', 'FSD-SWAP-21', 'FSD-SWAP-22', 'FSD-SWAP-23', 'FSD-SWAP-24', 'FSD-SWAP-25', 'FSD-SWAP-26', 'FSD-SWAP-27', 'FSD-SWAP-28', 'FSD-SWAP-29', 'FSD-SWAP-30', 'FSD-SWAP-31', 'FSD-SWAP-32', 'FSD-SWAP-33', 'FSD-SWAP-34', 'FSD-SWAP-35');
