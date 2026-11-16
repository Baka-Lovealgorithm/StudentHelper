-- CR-014 / TCR-035：用量看板调试数据隔离幂等升级（2026-11-10）
-- 执行方式：mysql -h 127.0.0.1 -P 3306 -u root < upgrade-s5.sql
-- 可重复执行；只加列与索引，不改任何存量数据、不删任何行。
-- 说明：t_call_log.is_debug 用于看板聚合过滤；t_provider_model.is_debug 是「以后」调试 / 桩服务
--       模型条目的显式判据，落库时由 ResolvedModelConfig.debug() 带出。存量行一律保持 0。

USE student_helper;

SET @col_log = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'student_helper'
    AND TABLE_NAME = 't_call_log'
    AND COLUMN_NAME = 'is_debug'
);
SET @ddl_log = IF(@col_log = 0,
  'ALTER TABLE t_call_log ADD COLUMN is_debug TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''CR-014 调试/桩服务调用标记，用量看板不统计''',
  'SELECT ''t_call_log.is_debug already exists, skipped'' AS upgrade_result');
PREPARE stmt_log FROM @ddl_log;
EXECUTE stmt_log;
DEALLOCATE PREPARE stmt_log;

SET @idx_log = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = 'student_helper'
    AND TABLE_NAME = 't_call_log'
    AND INDEX_NAME = 'idx_log_debug_created'
);
SET @ddl_idx = IF(@idx_log = 0,
  'ALTER TABLE t_call_log ADD KEY idx_log_debug_created (is_debug, created_at)',
  'SELECT ''idx_log_debug_created already exists, skipped'' AS upgrade_result');
PREPARE stmt_idx FROM @ddl_idx;
EXECUTE stmt_idx;
DEALLOCATE PREPARE stmt_idx;

SET @col_model = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'student_helper'
    AND TABLE_NAME = 't_provider_model'
    AND COLUMN_NAME = 'is_debug'
);
SET @ddl_model = IF(@col_model = 0,
  'ALTER TABLE t_provider_model ADD COLUMN is_debug TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''CR-014 调试模型条目，其调用不计入用量看板''',
  'SELECT ''t_provider_model.is_debug already exists, skipped'' AS upgrade_result');
PREPARE stmt_model FROM @ddl_model;
EXECUTE stmt_model;
DEALLOCATE PREPARE stmt_model;

SELECT 'upgrade-s5 done' AS result,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = 'student_helper'
     AND TABLE_NAME IN ('t_call_log', 't_provider_model')
     AND COLUMN_NAME = 'is_debug') AS marker_columns,
  (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = 'student_helper'
     AND TABLE_NAME = 't_call_log'
     AND INDEX_NAME = 'idx_log_debug_created') AS marker_indexes,
  (SELECT COUNT(*) FROM t_call_log WHERE is_debug = 1) AS debug_rows;
