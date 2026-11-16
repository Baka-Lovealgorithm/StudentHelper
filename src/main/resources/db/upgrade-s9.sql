-- =====================================================================
-- S9 升级脚本（幂等）：TCR-062 / CR-037 历史图片入上下文与尺寸压缩（T-504）
-- 技术规范 5.31 / SRS 13.42 / 任务表 v5.18；MySQL 8.0.37 / utf8mb4
--
-- 内容（纯数据，无 DDL）：
--   新增第 15 个功能开关 historyImage，默认开启（用户 2026-11-13 裁决）。
--   脚本幂等：已存在该行时不覆盖用户当前的 enabled 值，仅保留原状态。
--
-- 说明：应用不会自动执行升级脚本（spring.sql.init.mode=never），需人工执行。
-- 关闭态 ChatContextAssembler 逐字回到 CR-036 后的纯文本口径，当前轮也不压缩。
--
-- 执行方式：mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u root -p student_helper < upgrade-s9.sql
-- =====================================================================

INSERT INTO t_feature_toggle (feature_key, enabled, updated_at)
VALUES ('historyImage', 1, NOW(3))
ON DUPLICATE KEY UPDATE feature_key = feature_key;
