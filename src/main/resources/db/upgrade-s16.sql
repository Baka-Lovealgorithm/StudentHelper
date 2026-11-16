-- =====================================================================
-- S16 升级脚本（幂等）：TCR-087 / CR-060 取消学科规范「内置只读」概念
-- 技术规范 5.54 / SRS 13.65 / 任务表 v5.43；MySQL 8.0.37 / utf8mb4
--
-- 内容（纯数据归一，无 DDL）：
--   1) t_subject_skill 的 scope 由 BUILTIN 归一为 CUSTOM
--   2) code 保留（DATA_STRUCTURE / ADVANCED_MATH）：
--      subject_get 通过 findEnabledByCode 做稳定寻址，subject_list 也按 code 展示
--   3) 校验：输出 builtin_rows（应为 0）/ custom_rows / code_rows
--
-- 说明：
--   * 代码侧已删除 SubjectSkillApplicationService 的 builtin 拦截与
--     SubjectSkillResponse.builtin()，前端不再显示「内置只读」标签。
--   * 表结构、uk_subject_skill_code / uk_subject_skill_name 唯一键一律不动。
--   * 应用不会自动执行本脚本（spring.sql.init.mode=never）。脚本可重复执行。
--
-- 执行方式：mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u root \
--           < src/main/resources/db/upgrade-s16.sql
-- =====================================================================

USE student_helper;

UPDATE t_subject_skill
SET scope = 'CUSTOM'
WHERE scope = 'BUILTIN';

SELECT
  (SELECT COUNT(*) FROM t_subject_skill WHERE scope = 'BUILTIN') AS builtin_rows,
  (SELECT COUNT(*) FROM t_subject_skill WHERE scope = 'CUSTOM')  AS custom_rows,
  (SELECT COUNT(*) FROM t_subject_skill WHERE code IS NOT NULL)  AS code_rows;
