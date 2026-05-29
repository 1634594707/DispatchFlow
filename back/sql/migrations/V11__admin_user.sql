-- Phase 4: Admin user & session tables

USE `fsd_core`;

CREATE TABLE IF NOT EXISTS `t_admin_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` VARCHAR(64) NOT NULL COMMENT '登录用户名',
  `password_hash` VARCHAR(128) NOT NULL COMMENT '密码哈希',
  `display_name` VARCHAR(64) NOT NULL COMMENT '显示名称',
  `role` VARCHAR(32) NOT NULL COMMENT '角色: VIEWER/OPERATOR/ADMIN',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/DISABLED',
  `last_login_at` DATETIME DEFAULT NULL COMMENT '最后登录时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_role_status` (`role`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='管理后台用户表';

CREATE TABLE IF NOT EXISTS `t_admin_session` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `token` VARCHAR(64) NOT NULL COMMENT '会话令牌',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `expires_at` DATETIME NOT NULL COMMENT '过期时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_token` (`token`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='管理后台会话表';

-- Default users: admin/admin123, operator/operator123, viewer/viewer123
INSERT INTO `t_admin_user` (`username`, `password_hash`, `display_name`, `role`, `status`)
VALUES
  ('admin', '$2b$10$vojppRI7O0uN8xZxVnWB2OyQ1lJBU2te3G1XKNHewk7zuor1ffBVK', '系统管理员', 'ADMIN', 'ACTIVE'),
  ('operator', '$2b$10$isvIzqX36e9IJhZUAriQau./LLezprSsYrUVn8T8FLiQ/accARG0O', '调度员', 'OPERATOR', 'ACTIVE'),
  ('viewer', '$2b$10$xQjOeQGheBJBZa9uIGbhn.tMELCJUDROpPjRfO6uO4vmbz.U9cI0y', '观察员', 'VIEWER', 'ACTIVE')
ON DUPLICATE KEY UPDATE `username` = `username`;
