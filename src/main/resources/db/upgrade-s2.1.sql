-- 大学生解题助手 Agent — S2 结构演进脚本（v2.5 / TCR-016）
-- 对应 docs/02-技术规范.md 5.6、DD-01~DD-03；MySQL 8.0.37 / utf8mb4 / InnoDB
-- 执行方式（口令只经 MYSQL_PWD 临时环境变量传递，不写入任何文件）：
--   mysql -h 127.0.0.1 -P 3306 -u root < src/main/resources/db/upgrade-s2.1.sql
-- 应用不会自动执行本脚本（spring.sql.init.mode=never）。脚本可重复执行。
-- 约定：不改 upgrade-s2.sql，不修改 S1 的 4 张表，S2 表保持无外键、无 user_id。

USE student_helper;

-- 1) DD-01：逐功能独立开关，全部默认关闭。独立建表而非放 profile_json，
--    避免 T-201「清空画像」时误删开关，也免去 JSON 解析依赖。
CREATE TABLE IF NOT EXISTS t_feature_toggle (
  feature_key VARCHAR(64)  NOT NULL,
  enabled     TINYINT      NOT NULL DEFAULT 0 COMMENT '0=关闭（默认） / 1=开启',
  updated_at  DATETIME(3)  NOT NULL,
  PRIMARY KEY (feature_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='S2 逐功能独立开关，默认全部关闭（DD-01）';

INSERT IGNORE INTO t_feature_toggle (feature_key, enabled, updated_at) VALUES
  ('usagePanel',       0, NOW(3)),
  ('feedback',         0, NOW(3)),
  ('memory',           0, NOW(3)),
  ('wrongBook',        0, NOW(3)),
  ('knowledgeExtract', 0, NOW(3)),
  ('optionalSections', 0, NOW(3)),
  ('practice',         0, NOW(3)),
  ('workspace',        0, NOW(3)),
  ('stepUnlock',       0, NOW(3));

-- 2) DD-02：t_knowledge_point 增加大知识点 parent_name。
--    MySQL 唯一键中 NULL 不判重，故单级知识点用空串 '' 归一化，parent_name 为 NOT NULL。
SET @kp_col = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'student_helper' AND TABLE_NAME = 't_knowledge_point'
    AND COLUMN_NAME = 'parent_name'
);
SET @ddl = IF(@kp_col = 0,
  'ALTER TABLE t_knowledge_point ADD COLUMN parent_name VARCHAR(128) NOT NULL DEFAULT '''' COMMENT ''大知识点，单级时为空串'' AFTER subject',
  'SELECT ''t_knowledge_point.parent_name already exists, skipped'' AS upgrade_result');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) 唯一键由 (subject, name) 调整为 (subject, parent_name, name)。
SET @kp_old_idx = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = 'student_helper' AND TABLE_NAME = 't_knowledge_point'
    AND INDEX_NAME = 'uk_kp'
);
SET @ddl = IF(@kp_old_idx > 0,
  'ALTER TABLE t_knowledge_point DROP INDEX uk_kp',
  'SELECT ''uk_kp already dropped, skipped'' AS upgrade_result');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @kp_new_idx = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = 'student_helper' AND TABLE_NAME = 't_knowledge_point'
    AND INDEX_NAME = 'uk_kp2'
);
SET @ddl = IF(@kp_new_idx = 0,
  'ALTER TABLE t_knowledge_point ADD UNIQUE KEY uk_kp2 (subject, parent_name, name)',
  'SELECT ''uk_kp2 already exists, skipped'' AS upgrade_result');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4) DD-03：t_practice 直接记录练习对应的知识点，避免依赖消息 id 的三表关联。
SET @pr_col = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'student_helper' AND TABLE_NAME = 't_practice'
    AND COLUMN_NAME = 'kp_id'
);
SET @ddl = IF(@pr_col = 0,
  'ALTER TABLE t_practice ADD COLUMN kp_id BIGINT NULL COMMENT ''本次练习对应知识点，供 mastery 聚合'' AFTER session_id',
  'SELECT ''t_practice.kp_id already exists, skipped'' AS upgrade_result');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5) 幂等校验：应为 9（upgrade-s2.sql 的 8 张 + 本脚本的 t_feature_toggle）。
SELECT COUNT(*) AS s2_table_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'student_helper'
  AND TABLE_NAME IN (
    't_user_profile', 't_memory', 't_message_feedback', 't_knowledge_point',
    't_knowledge_hit', 't_practice', 't_workspace', 't_step_unlock', 't_feature_toggle'
  );

SELECT feature_key, enabled FROM t_feature_toggle ORDER BY feature_key;
