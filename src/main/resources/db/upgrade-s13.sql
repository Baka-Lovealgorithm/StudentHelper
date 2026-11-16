-- =====================================================================
-- S13 升级脚本（幂等）：TCR-069 / CR-042 退役「文件工作区」（T-208 / FR-208）
-- 技术规范 5.36 / SRS 13.47 / 任务表 v5.25 第 6 章；MySQL 8.0.37 / utf8mb4
--
-- 内容（纯数据清理，无 DDL）：
--   1) t_feature_toggle 删除 workspace 开关行（功能开关 13 → 12 项）
--
-- 说明：
--   * 该功能**从未实现**：只有 S2Feature.WORKSPACE 开关项与空表 t_workspace，
--     没有任何 Service / Controller / 仓储 / 前端面板，也从未提供 /api/workspace。
--   * t_workspace 空表**保留留档、不 DROP**（与 t_subject 同口径）；当前 0 行、无代码读写。
--   * 代码侧开关枚举与前端 FEATURE_CATEGORY 映射已删除；本脚本只清历史数据。
--
-- 执行方式：mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u root student_helper \
--           < src/main/resources/db/upgrade-s13.sql
-- 应用不会自动执行本脚本（spring.sql.init.mode=never）。脚本可重复执行。
-- =====================================================================

USE student_helper;

-- 1) 开关行
DELETE FROM t_feature_toggle WHERE feature_key = 'workspace';

-- 自检：开关应为 0 行；留档表应仍存在且 0 行
SELECT COUNT(*) AS toggle_rows_left FROM t_feature_toggle WHERE feature_key = 'workspace';
SELECT COUNT(*) AS workspace_rows FROM t_workspace;
