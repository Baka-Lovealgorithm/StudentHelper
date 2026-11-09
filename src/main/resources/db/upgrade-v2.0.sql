-- 大学生解题助手 Agent — S1 增量升级脚本（v2.0）
-- 目的：为 FR-109 / AC-109-3 增加 t_message.hint，保存用户本轮「难度与讲解要求」原文。
-- 对应 docs/02-技术规范.md 5.2；MySQL 8.0.37 / utf8mb4。
-- 执行方式（PowerShell，在仓库根目录；口令走 MYSQL_PWD 临时环境变量，不写入文件）：
--   $env:MYSQL_PWD = "<你的 MySQL 口令>"
--   mysql -h 127.0.0.1 -P 3306 -u root < src/main/resources/db/upgrade-v2.0.sql
-- 应用不会自动执行本脚本（spring.sql.init.mode=never）。脚本可重复执行。

USE student_helper;

-- MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，用 information_schema 判断后动态执行，保证幂等。
SET @col_exists = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'student_helper'
    AND TABLE_NAME = 't_message'
    AND COLUMN_NAME = 'hint'
);

SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE t_message ADD COLUMN hint VARCHAR(500) NULL COMMENT ''本轮难度与讲解要求原文（FR-109 / AC-109-3）'' AFTER content',
  'SELECT ''t_message.hint already exists, skipped'' AS upgrade_result');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
