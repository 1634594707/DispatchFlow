-- W4-b 撤桩：把"近场新增 4 处 ×10 = 40 位"这四座充电站从现役设施里摘掉。
-- 为什么该撤：§0.2/§1.12 那笔充电桩预算是"谁插枪"没有解时的替代方案；换电柜（W4-a 的 35 点）
--   + 车端自动补能已经消掉了形态问题，这笔钱不该再花。
-- ⚠ 刻意只动 `ZJF-CHG-06..09`。`ZJF-CHG-02..05` **保持 INACTIVE 不动** —— 它们那条
--   `avg_service_seconds`（1,800 / 3,600 混着写）口径还没定（§10.2 第 3 问），顺手改会污染那个待决问题。
-- ⚠ 撤的是**站点行**，不是 `t_charging_pile`：仿真走的是 `SimulationMotionState.chargingPoint`
--   这一个点（见 §13.103），这四座站从来没有被选桩逻辑消费过 ⇒ 本条不改变派单/充电行为。
--   顺序上必须排在 zjf_geo.sql 之后（那份把这四行写成 ACTIVE）。
-- ⚠ 用 `remark NOT LIKE` 兜住幂等：这条 seed 会被两条初始化路径反复灌（`verify-geo-init-paths.sh`
--   每跑一次灌一次），少了这个条件就会每跑一次多拼一段尾巴，station 指纹随之漂。
UPDATE t_station SET status='INACTIVE',
       remark=CONCAT(IFNULL(remark,''),' [W4-b 撤桩：换电柜替代近场 40 位充电桩预算]')
 WHERE station_code IN ('ZJF-CHG-06','ZJF-CHG-07','ZJF-CHG-08','ZJF-CHG-09') AND deleted=0
   AND (remark IS NULL OR remark NOT LIKE '%W4-b 撤桩%');
