-- =====================================================================
-- S3 升级脚本（幂等）：可重复执行
-- T-307 学科模板 / T-302 技能 / T-301 MCP Server / 5 个新功能开关
-- 不修改 S1 的 4 张表，也不修改 S2 已建表结构
-- =====================================================================

CREATE TABLE IF NOT EXISTS t_subject (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL,
  prompt_hint TEXT,
  is_current TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_subject (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='T-307 学科与提示词模板';

CREATE TABLE IF NOT EXISTS t_skill (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL,
  description VARCHAR(255) DEFAULT NULL,
  content MEDIUMTEXT NOT NULL,
  enabled TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_skill (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='T-302 解题技能（Markdown 指令片段）';

CREATE TABLE IF NOT EXISTS t_mcp_server (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL,
  transport VARCHAR(24) NOT NULL DEFAULT 'STREAMABLE_HTTP',
  url VARCHAR(512) NOT NULL,
  enabled TINYINT NOT NULL DEFAULT 0,
  last_status VARCHAR(24) DEFAULT NULL,
  last_error VARCHAR(500) DEFAULT NULL,
  tool_count INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_mcp_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='T-301 远程 MCP Server（仅远程，不启用 stdio）';

INSERT IGNORE INTO t_feature_toggle (feature_key, enabled, updated_at) VALUES
  ('multiSolution', 0, NOW(3)),
  ('subjectTemplate', 0, NOW(3)),
  ('skill', 0, NOW(3)),
  ('mcp', 0, NOW(3)),
  ('learningStats', 0, NOW(3));

INSERT IGNORE INTO t_subject (name, prompt_hint, is_current, created_at, updated_at) VALUES
  ('数据结构', '术语使用数据结构课程的说法；复杂度分析给出量级与简要推导。', 1, NOW(3), NOW(3)),
  ('高等数学', '使用高等数学课程术语；关键变形与所用定理要写明名称。', 0, NOW(3), NOW(3));

SELECT 'upgrade-s3 done' AS result,
  (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME IN ('t_subject','t_skill','t_mcp_server')) AS new_tables,
  (SELECT COUNT(*) FROM t_feature_toggle) AS toggle_rows,
  (SELECT COUNT(*) FROM t_subject) AS subject_rows;
