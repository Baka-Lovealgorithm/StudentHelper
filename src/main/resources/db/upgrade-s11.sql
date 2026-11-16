-- =====================================================================
-- S11 升级脚本（幂等）：TCR-065 / CR-039 回答意图自判与学科规范三段化（T-506）
-- 技术规范 5.33 / SRS 13.44 / 任务表 v5.21；MySQL 8.0.37 / utf8mb4
--
-- 内容：
--   1) t_subject_skill 加 spec_json（{"solve","followUp","explain"}），
--      并把旧单段 spec_md 迁移为 solve，避免升级后学科规范丢失；
--   2) t_message 加 intent（SOLVE / FOLLOW_UP / EXPLAIN）；历史行为 NULL，
--      前端按 section_schema 推断（FREE_TEXT → 追问，其余 → 解题）；
--   3) 删除 t_feature_toggle 的 subjectSkill 行——该功能开关按用户裁决移除，
--      学科规范恒可用（会话三态 AUTO / MANUAL / OFF 保留）。
--
-- 说明：
--   * 应用不会自动执行升级脚本（spring.sql.init.mode=never），需人工执行；
--   * 脚本可重复执行：加列走 information_schema 判断，数据迁移只填空值，
--     删除开关行用 DELETE（已不存在时影响 0 行）；
--   * MySQL 客户端须带 --default-character-set=utf8mb4，否则中文规范报 ERROR 1366。
--
-- 执行方式：mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u root -p student_helper < upgrade-s11.sql
-- =====================================================================

USE student_helper;

-- ---------------------------------------------------------------------
-- 1) t_subject_skill.spec_json
-- ---------------------------------------------------------------------
SET @col_spec_json = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'student_helper' AND TABLE_NAME = 't_subject_skill' AND COLUMN_NAME = 'spec_json');
SET @ddl_spec_json = IF(@col_spec_json = 0,
  'ALTER TABLE t_subject_skill ADD COLUMN spec_json TEXT NULL COMMENT ''CR-039 三段规范 {"solve","followUp","explain"}；空段回落系统默认''',
  'SELECT ''t_subject_skill.spec_json already exists, skipped'' AS upgrade_result');
PREPARE stmt_spec_json FROM @ddl_spec_json;
EXECUTE stmt_spec_json;
DEALLOCATE PREPARE stmt_spec_json;

-- 旧单段 spec_md → spec_json.solve（只迁移空值，重复执行不覆盖用户后续编辑）
UPDATE t_subject_skill
SET spec_json = JSON_OBJECT('solve', spec_md, 'followUp', '', 'explain', '')
WHERE (spec_json IS NULL OR JSON_VALID(spec_json) = 0)
  AND spec_md IS NOT NULL AND spec_md <> '';

-- ---------------------------------------------------------------------
-- 2) t_message.intent
-- ---------------------------------------------------------------------
SET @col_intent = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'student_helper' AND TABLE_NAME = 't_message' AND COLUMN_NAME = 'intent');
SET @ddl_intent = IF(@col_intent = 0,
  'ALTER TABLE t_message ADD COLUMN intent VARCHAR(16) NULL COMMENT ''CR-039 回答形态 SOLVE/FOLLOW_UP/EXPLAIN；NULL 为历史消息''',
  'SELECT ''t_message.intent already exists, skipped'' AS upgrade_result');
PREPARE stmt_intent FROM @ddl_intent;
EXECUTE stmt_intent;
DEALLOCATE PREPARE stmt_intent;

-- 历史消息按落库结构回填形态，便于前端统一按 intent 渲染（FREE_TEXT → 追问，其余 → 解题）
UPDATE t_message
SET intent = CASE WHEN section_schema = 'FREE_TEXT' THEN 'FOLLOW_UP' ELSE 'SOLVE' END
WHERE intent IS NULL AND role = 'ASSISTANT';

-- ---------------------------------------------------------------------
-- 3) 移除 subjectSkill 功能开关
-- ---------------------------------------------------------------------
DELETE FROM t_feature_toggle WHERE feature_key = 'subjectSkill';

-- 自检
SELECT 'upgrade-s11 done' AS result,
  (SELECT COUNT(*) FROM t_subject_skill WHERE spec_json IS NOT NULL) AS spec_json_rows,
  (SELECT COUNT(*) FROM t_message WHERE intent IS NOT NULL) AS intent_rows,
  (SELECT COUNT(*) FROM t_feature_toggle) AS feature_rows,
  (SELECT COUNT(*) FROM t_feature_toggle WHERE feature_key = 'subjectSkill') AS subject_skill_toggle;
