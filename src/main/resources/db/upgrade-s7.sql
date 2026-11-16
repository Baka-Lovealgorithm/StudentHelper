-- =====================================================================
-- S7 升级脚本（幂等）：TCR-049 / CR-024 退役「可选输出段落」（T-204）
-- 技术规范 5.18 / SRS 13.29 / 任务表 v5.6 第 5 章；MySQL 8.0.37 / utf8mb4
--
-- 内容（纯数据清理，无 DDL）：
--   1) t_feature_toggle 删除 optionalSections 开关行（功能开关 15 → 14 项）
--   2) t_user_profile.profile_json 移除 optionalSections 键
--
-- 说明：代码侧开关枚举、Service、接口、提示词段已全部删除；本脚本只清历史数据。
-- 历史消息里已生成的「## 补充：…」正文不改写——解析器与前端渲染保留，旧回答照常显示。
--
-- 执行方式：mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u root student_helper \
--           < src/main/resources/db/upgrade-s7.sql
-- 应用不会自动执行本脚本（spring.sql.init.mode=never）。脚本可重复执行。
-- =====================================================================

USE student_helper;

-- 1) 开关行
DELETE FROM t_feature_toggle WHERE feature_key = 'optionalSections';

-- 2) 画像里的子选项键（存在才删，重复执行无副作用）
UPDATE t_user_profile
   SET profile_json = JSON_REMOVE(profile_json, '$.optionalSections')
 WHERE profile_json IS NOT NULL
   AND JSON_VALID(profile_json)
   AND JSON_CONTAINS_PATH(profile_json, 'one', '$.optionalSections');

-- 自检
SELECT COUNT(*) AS toggle_rows_left FROM t_feature_toggle WHERE feature_key = 'optionalSections';
SELECT COUNT(*) AS profile_rows_with_key FROM t_user_profile
 WHERE profile_json IS NOT NULL
   AND JSON_VALID(profile_json)
   AND JSON_CONTAINS_PATH(profile_json, 'one', '$.optionalSections');
