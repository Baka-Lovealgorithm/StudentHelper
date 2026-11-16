-- =====================================================================
-- S10 升级脚本（幂等）：TCR-063 / CR-038 通用技能会话级固化与跨轮一致（T-505）
-- 技术规范 5.32 / SRS 13.43 / 任务表 v5.19；MySQL 8.0.37 / utf8mb4
--
-- 内容（纯 DDL，无数据变更）：
--   t_session 增加 skill_ids VARCHAR(128) NULL，逗号分隔技能 id，最多 2 条。
--   NULL / 空串表示未固化，仍走 CR-026 的「清单 + skill_get」按需链路。
--   无新增表、无新增功能开关项（仍 15 项）。
--
-- 说明：应用不会自动执行升级脚本（spring.sql.init.mode=never），需人工执行。
--
-- 执行方式：mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u root -p student_helper < upgrade-s10.sql
-- =====================================================================

SET @col_session_skills = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'student_helper' AND TABLE_NAME = 't_session' AND COLUMN_NAME = 'skill_ids');
SET @ddl_session_skills = IF(@col_session_skills = 0,
  'ALTER TABLE t_session ADD COLUMN skill_ids VARCHAR(128) NULL COMMENT ''CR-038 会话固化的通用技能 id，逗号分隔，最多 2 条''',
  'SELECT ''t_session.skill_ids already exists, skipped'' AS upgrade_result');
PREPARE stmt_session_skills FROM @ddl_session_skills;
EXECUTE stmt_session_skills;
DEALLOCATE PREPARE stmt_session_skills;
