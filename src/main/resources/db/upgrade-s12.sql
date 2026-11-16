-- =====================================================================
-- S12 升级脚本（幂等）：TCR-068 / CR-041 退役「反馈闭环」（T-207 / FR-207）
-- 技术规范 5.35 / SRS 13.46 / 任务表 v5.24 第 6 章；MySQL 8.0.37 / utf8mb4
--
-- 内容（纯数据清理，无 DDL）：
--   1) t_feature_toggle 删除 feedback 开关行（功能开关 14 → 13 项）
--   2) t_message_feedback 清掉 kind = INCORRECT / REGENERATE 的历史行（当前 0 行，纯防御）
--
-- 说明：
--   * 代码侧 FeedbackController / FeedbackApplicationService / FeedbackRequest 已删除，
--     前端「标记有误」「重新生成」入口一并下线；本脚本只清历史数据。
--   * t_message_feedback 表与 kind = 'WRONG' 行**必须保留**——错题本（T-202 / FR-202）
--     以 WRONG 复用同一张表，删表或删行会破坏错题本。
--   * 表结构、唯一键 uk_feedback、索引 idx_fb_wrong 一律不动。
--
-- 执行方式：mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u root student_helper \
--           < src/main/resources/db/upgrade-s12.sql
-- 应用不会自动执行本脚本（spring.sql.init.mode=never）。脚本可重复执行。
-- =====================================================================

USE student_helper;

-- 1) 开关行
DELETE FROM t_feature_toggle WHERE feature_key = 'feedback';

-- 2) 反馈闭环的历史标记（错题本的 WRONG 不能动）
DELETE FROM t_message_feedback WHERE kind IN ('INCORRECT', 'REGENERATE');

-- 自检：开关应为 0 行，WRONG 行数应保持不变
SELECT COUNT(*) AS toggle_rows_left FROM t_feature_toggle WHERE feature_key = 'feedback';
SELECT kind, COUNT(*) AS rows_left FROM t_message_feedback GROUP BY kind;
