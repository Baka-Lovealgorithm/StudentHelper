-- =====================================================================
-- S4.1 升级脚本（幂等）：TCR-040 / CR-017 对话压缩开关独立
-- 技术规范 5.10；MySQL 8.0.37 / utf8mb4
--
-- 背景：对话压缩原由 memory 开关控制，与长期记忆注入耦合。本脚本新增
-- conversationCompaction 开关，压缩不再依赖 memory。
--
-- 初始值取 1（开启）而非 DD-01 的「新开关默认关闭」：它承接的是 memory
-- 开关原本就在执行的压缩行为，置 0 会造成升级后长会话压缩能力静默消失。
-- 仅此一次初始值，之后完全由用户在功能面板控制。
--
-- 执行方式：mysql -h 127.0.0.1 -P 3306 -u root student_helper < src/main/resources/db/upgrade-s4.1.sql
-- 应用不会自动执行本脚本（spring.sql.init.mode=never）。脚本可重复执行。
-- =====================================================================

USE student_helper;

INSERT IGNORE INTO t_feature_toggle (feature_key, enabled, updated_at) VALUES
  ('conversationCompaction', 1, NOW(3));

-- 自检：开关行数应为 15，且 conversationCompaction 已存在
SELECT
  (SELECT COUNT(*) FROM t_feature_toggle) AS toggle_rows,
  (SELECT enabled FROM t_feature_toggle WHERE feature_key = 'conversationCompaction') AS compaction_enabled;
