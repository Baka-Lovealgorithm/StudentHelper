-- =====================================================================
-- S6 升级脚本（幂等）：TCR-041 / CR-018 学科规范与输出结构可插拔
-- 技术规范 5.11 / SRS 13.21 / 任务表 v4.7 第 14 章；MySQL 8.0.37 / utf8mb4
--
-- 内容：
--   1) t_subject_skill 新表 + 两条内置 seed（DATA_STRUCTURE / ADVANCED_MATH）
--   2) t_session 加 subject_mode / subject_skill_id
--   3) t_message 加 section_schema
--   4) t_call_log 加 subject_hit（命中学科与理由，供审计）
--   5) t_feature_toggle 预置 subjectSkill 开关（默认关闭，DD-01）
--
-- 执行方式：mysql -h 127.0.0.1 -P 3306 -u root student_helper < src/main/resources/db/upgrade-s6.sql
-- 应用不会自动执行本脚本（spring.sql.init.mode=never）。脚本可重复执行，
-- 重复执行不会覆盖用户已编辑 / 已停用的内置行。
-- =====================================================================

USE student_helper;

CREATE TABLE IF NOT EXISTS t_subject_skill (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  code          VARCHAR(64)  NULL COMMENT '内置稳定标识；自定义学科为 NULL',
  name          VARCHAR(64)  NOT NULL,
  description   VARCHAR(255) NULL,
  scope         VARCHAR(16)  NOT NULL DEFAULT 'CUSTOM' COMMENT 'BUILTIN / CUSTOM',
  enabled       TINYINT(1)   NOT NULL DEFAULT 0,
  keywords      VARCHAR(512) NULL,
  spec_md       TEXT         NOT NULL COMMENT '回答规范（只写怎么答，不写教材知识）',
  sections_json TEXT         NOT NULL COMMENT '{"full":[...],"idea":[...]}，标题须以 ## 开头',
  created_at    DATETIME(3)  NOT NULL,
  updated_at    DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_subject_skill_code (code),
  UNIQUE KEY uk_subject_skill_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CR-018 学科规范（与通用技能 t_skill 两条独立链路）';

-- ---------------------------------------------------------------------
-- 幂等加列
-- ---------------------------------------------------------------------
SET @col_session_mode = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'student_helper' AND TABLE_NAME = 't_session' AND COLUMN_NAME = 'subject_mode');
SET @ddl_session_mode = IF(@col_session_mode = 0,
  'ALTER TABLE t_session ADD COLUMN subject_mode VARCHAR(16) NOT NULL DEFAULT ''AUTO'' COMMENT ''CR-018 AUTO/MANUAL/OFF''',
  'SELECT ''t_session.subject_mode already exists, skipped'' AS upgrade_result');
PREPARE stmt_session_mode FROM @ddl_session_mode;
EXECUTE stmt_session_mode;
DEALLOCATE PREPARE stmt_session_mode;

SET @col_session_skill = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'student_helper' AND TABLE_NAME = 't_session' AND COLUMN_NAME = 'subject_skill_id');
SET @ddl_session_skill = IF(@col_session_skill = 0,
  'ALTER TABLE t_session ADD COLUMN subject_skill_id BIGINT NULL COMMENT ''CR-018 首轮判定后固化的学科规范 id''',
  'SELECT ''t_session.subject_skill_id already exists, skipped'' AS upgrade_result');
PREPARE stmt_session_skill FROM @ddl_session_skill;
EXECUTE stmt_session_skill;
DEALLOCATE PREPARE stmt_session_skill;

SET @col_message_schema = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'student_helper' AND TABLE_NAME = 't_message' AND COLUMN_NAME = 'section_schema');
SET @ddl_message_schema = IF(@col_message_schema = 0,
  'ALTER TABLE t_message ADD COLUMN section_schema TEXT NULL COMMENT ''CR-018 该回答所用输出结构；NULL 按默认六段；FREE_TEXT 为追问''',
  'SELECT ''t_message.section_schema already exists, skipped'' AS upgrade_result');
PREPARE stmt_message_schema FROM @ddl_message_schema;
EXECUTE stmt_message_schema;
DEALLOCATE PREPARE stmt_message_schema;

SET @col_message_label = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'student_helper' AND TABLE_NAME = 't_message' AND COLUMN_NAME = 'subject_label');
SET @ddl_message_label = IF(@col_message_label = 0,
  'ALTER TABLE t_message ADD COLUMN subject_label VARCHAR(128) NULL COMMENT ''CR-018 该回答采用的学科与结构标签，供前端展示''',
  'SELECT ''t_message.subject_label already exists, skipped'' AS upgrade_result');
PREPARE stmt_message_label FROM @ddl_message_label;
EXECUTE stmt_message_label;
DEALLOCATE PREPARE stmt_message_label;

SET @col_log_hit = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'student_helper' AND TABLE_NAME = 't_call_log' AND COLUMN_NAME = 'subject_hit');
SET @ddl_log_hit = IF(@col_log_hit = 0,
  'ALTER TABLE t_call_log ADD COLUMN subject_hit VARCHAR(500) NULL COMMENT ''CR-018 命中学科 code 与理由''',
  'SELECT ''t_call_log.subject_hit already exists, skipped'' AS upgrade_result');
PREPARE stmt_log_hit FROM @ddl_log_hit;
EXECUTE stmt_log_hit;
DEALLOCATE PREPARE stmt_log_hit;

-- ---------------------------------------------------------------------
-- 内置 seed（全文见技术规范 5.11；助手 2026-11-11 定稿）
-- ---------------------------------------------------------------------
INSERT INTO t_subject_skill
  (code, name, description, scope, enabled, keywords, spec_md, sections_json, created_at, updated_at)
SELECT
  'DATA_STRUCTURE', '数据结构',
  '数据结构与算法课程题目的术语、复杂度与推导口径，沿用默认六段结构',
  'BUILTIN', 1,
  '栈、队列、链表、树、二叉树、图、堆、哈希表、排序、查找、递归、遍历、时间复杂度、空间复杂度、大O',
  '【适用范围】数据结构与算法题目（线性结构、树、图、排序查找、复杂度分析）。
【记号与术语】Θ / O / Ω 严格区分上下界；未说明时「复杂度」默认指最坏情况；log 默认以 2 为底并写明；n 为问题规模，须声明含义；树的高度按边数计并注明口径。
【推理要求】② 必须点出所用结构及其性质；③ 先给思路再给步骤；④ 每步说明对数据结构状态的改变；⑤ 结论必须可复算；⑥ 至少列 1 个易错点或验算方式。
【依据】凡涉及复杂度或算法性质，必须给出推导或反例，不得凭印象断言；题目条件不足时明确指出缺什么条件，不得编造性质。
【禁止】不使用未定义的自定义记号；不输出 ⑦ 及相似练习题；不用 JSON 包裹答案。',
  '{"full":["## ① 题目理解","## ② 涉及知识点","## ③ 解题思路","## ④ 详细步骤","## ⑤ 最终答案","## ⑥ 易错点 / 验算"],"idea":["## ① 题目理解","## ② 涉及知识点","## ③ 解题思路"]}',
  NOW(3), NOW(3)
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM t_subject_skill s WHERE s.code = 'DATA_STRUCTURE');

INSERT INTO t_subject_skill
  (code, name, description, scope, enabled, keywords, spec_md, sections_json, created_at, updated_at)
SELECT
  'ADVANCED_MATH', '高等数学',
  '极限、导数、积分与级数等推导型题目的规范，采用五段推导结构',
  'BUILTIN', 1,
  '极限、连续、导数、微分、中值定理、积分、不定积分、定积分、级数、收敛、泰勒、洛必达、偏导、重积分、微分方程',
  '【适用范围】极限、连续、导数与微分、中值定理、积分、级数、多元函数与微分方程等推导型题目。
【记号与术语】变量与参数含义须先声明；lim / ∫ / ∑ 必须写清上下限与趋近方向；使用 o、O、~ 时给出定义；分段函数须写明分界点处的处理方式。
【推理要求】② 必须写出所用定理名称及其适用条件（如洛必达需 0/0 或 ∞/∞ 且导数比极限存在）；③ 逐步推导、不跳步，每步注明依据；④ 结论须回代检验（定义域、收敛性、常数项、量纲）；⑤ 至少列 1 个常见误用或边界情况。
【依据】不得编造公式或定理；条件不足时明确指出并说明还需什么条件，不得强行给数值答案；数值近似须标明方法与误差，不得当作精确结论。
【仅思路】按 idea 数组的 3 段输出，③ 只写思路要点，不写完整推导、不给最终数值。
【禁止】不用 JSON 包裹答案；不输出 ⑦ 及相似练习题。',
  '{"full":["## ① 题意与已知条件","## ② 定理与公式","## ③ 推导过程","## ④ 结论与验证","## ⑤ 易错点与适用边界"],"idea":["## ① 题意与已知条件","## ② 定理与公式","## ③ 思路要点"]}',
  NOW(3), NOW(3)
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM t_subject_skill s WHERE s.code = 'ADVANCED_MATH');

-- ---------------------------------------------------------------------
-- 功能开关（默认关闭，DD-01）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO t_feature_toggle (feature_key, enabled, updated_at) VALUES
  ('subjectSkill', 0, NOW(3));

-- 自检
SELECT 'upgrade-s6 done' AS result,
  (SELECT COUNT(*) FROM t_subject_skill) AS subject_skill_rows,
  (SELECT COUNT(*) FROM t_subject_skill WHERE scope = 'BUILTIN') AS builtin_rows,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = 'student_helper' AND TABLE_NAME = 't_message' AND COLUMN_NAME = 'section_schema') AS message_schema_column,
  (SELECT COUNT(*) FROM t_feature_toggle WHERE feature_key = 'subjectSkill') AS subject_skill_toggle;
