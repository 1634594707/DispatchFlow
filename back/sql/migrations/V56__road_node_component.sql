-- V56: 路网连通分量表（§1.8 验收要求"回退到可达子集并记录断点"的落点）。
--
-- 为什么要有这张表：OSM 提取出来的园区路网本来就不是一张连通图 —— 实测 3.88 km² 提取框下
-- 93 个 ACTIVE 节点分成 2 个分量（84 + 9），邻近 snapping 从 0 m 扫到 60 m 都并不掉那个 9
-- 点的小分量（它是真的被河/快速路隔开，不是数据没接上）。跨分量的取货必然 UNREACHABLE，
-- 所以派单与演示数据落位都必须知道"这个节点在不在可派子集里"，而不是事后靠失败原因猜。
--
-- 内容按 node_code 幂等覆盖，由 scripts/geo/reanchor_facilities.py 重算。

CREATE TABLE IF NOT EXISTS `t_road_node_component` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `park_id` BIGINT NOT NULL COMMENT '园区ID',
  `node_code` VARCHAR(64) NOT NULL COMMENT '路网节点编码',
  `component_id` INT NOT NULL COMMENT '分量编号（按规模降序，1 = 最大分量）',
  `component_size` INT NOT NULL COMMENT '该分量节点数',
  `is_largest` TINYINT NOT NULL DEFAULT 0 COMMENT '1 = 属于最大连通分量，可作为派单/落位子集',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_park_node` (`park_id`, `node_code`),
  KEY `idx_component` (`park_id`, `component_id`),
  KEY `idx_largest` (`park_id`, `is_largest`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='路网节点连通分量（可派子集与断点记录）';
