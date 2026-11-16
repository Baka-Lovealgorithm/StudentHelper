-- =====================================================================
-- S17 升级脚本（幂等）：TCR-092 / CR-065 Prompt 前缀缓存优化
-- 技术规范 5.59 / SRS 13.70 / 任务表 v5.48；MySQL 8.0.37 / utf8mb4
--
-- 内容（DDL）：t_call_log 增加缓存命中 / 未命中 token 两列
--   1) cache_hit_tokens  INT NOT NULL DEFAULT 0
--   2) cache_miss_tokens INT NOT NULL DEFAULT 0
--   3) 校验：输出列名、是否可空、默认值与行数
--
-- 说明：
--   * 取值来自 AgentScope ChatUsage.getCachedTokens()（网关透传的
--     prompt_cache_hit_tokens / prompt_tokens_details.cached_tokens）；
--     miss = prompt_tokens - hit。网关不透传时 hit=0、miss=prompt。
--   * 历史行默认 0，不回填、不重算；用量看板只对新增行显示命中率。
--   * 应用不会自动执行本脚本（spring.sql.init.mode=never）。脚本可重复执行。
--
-- 执行方式：mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u root \
--           < src/main/resources/db/upgrade-s17.sql
-- =====================================================================

USE student_helper;

-- MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，用 information_schema 判断后动态执行，保证幂等。
SET @hit_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'student_helper' AND TABLE_NAME = 't_call_log'
    AND COLUMN_NAME = 'cache_hit_tokens'
);
SET @ddl_hit = IF(@hit_exists = 0,
  'ALTER TABLE t_call_log ADD COLUMN cache_hit_tokens INT NOT NULL DEFAULT 0 COMMENT ''缓存命中 prompt token（CR-065）'' AFTER completion_tokens',
  'SELECT ''t_call_log.cache_hit_tokens already exists, skipped'' AS upgrade_result');
PREPARE stmt_hit FROM @ddl_hit;
EXECUTE stmt_hit;
DEALLOCATE PREPARE stmt_hit;

SET @miss_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'student_helper' AND TABLE_NAME = 't_call_log'
    AND COLUMN_NAME = 'cache_miss_tokens'
);
SET @ddl_miss = IF(@miss_exists = 0,
  'ALTER TABLE t_call_log ADD COLUMN cache_miss_tokens INT NOT NULL DEFAULT 0 COMMENT ''缓存未命中 prompt token，prompt - hit（CR-065）'' AFTER cache_hit_tokens',
  'SELECT ''t_call_log.cache_miss_tokens already exists, skipped'' AS upgrade_result');
PREPARE stmt_miss FROM @ddl_miss;
EXECUTE stmt_miss;
DEALLOCATE PREPARE stmt_miss;

SELECT COLUMN_NAME, IS_NULLABLE, COLUMN_DEFAULT, DATA_TYPE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'student_helper' AND TABLE_NAME = 't_call_log'
  AND COLUMN_NAME IN ('cache_hit_tokens', 'cache_miss_tokens')
ORDER BY ORDINAL_POSITION;

SELECT COUNT(*) AS columns_added
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'student_helper' AND TABLE_NAME = 't_call_log'
  AND COLUMN_NAME IN ('cache_hit_tokens', 'cache_miss_tokens');
