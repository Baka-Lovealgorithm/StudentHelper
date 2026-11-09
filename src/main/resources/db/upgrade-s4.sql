-- =====================================================================
-- S4 升级脚本（幂等）：CR-012 通用厂商配置 / 模型条目 / 会话级模型选择
-- 技术规范 5.8、TCR-032；MySQL 8.0.37 / utf8mb4
--
-- 本脚本只负责 DDL。旧 t_model_config 的数据由应用启动时的
-- ModelProviderMigration 幂等搬迁（api_key_cipher 密文原样复制，全程不解密）。
-- 执行方式：mysql -h 127.0.0.1 -P 3306 -u root student_helper < src/main/resources/db/upgrade-s4.sql
-- 应用不会自动执行本脚本（spring.sql.init.mode=never）。脚本可重复执行。
-- =====================================================================

USE student_helper;

CREATE TABLE IF NOT EXISTS t_model_provider (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  name           VARCHAR(100) NOT NULL,
  base_url       VARCHAR(500) NOT NULL,
  api_key_cipher TEXT         NULL COMMENT 'AES-GCM，Base64(IV+ciphertext)；沿用 ./data/.master_key',
  created_at     DATETIME(3)  NOT NULL,
  updated_at     DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_provider_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CR-012 通用厂商配置（名称 / URL / Key）';

CREATE TABLE IF NOT EXISTS t_provider_model (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  provider_id      BIGINT       NOT NULL,
  model_name       VARCHAR(200) NOT NULL,
  vision_supported TINYINT      NOT NULL DEFAULT 0 COMMENT '是否支持图片输入（驱动 E2004）',
  stream_supported TINYINT      NOT NULL DEFAULT 1,
  extra_options    TEXT         NULL COMMENT 'JSON：temperature / nativeStructuredOutput 等',
  is_default       TINYINT      NOT NULL DEFAULT 0 COMMENT '全局默认模型，新会话继承',
  created_at       DATETIME(3)  NOT NULL,
  updated_at       DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_provider_model (provider_id, model_name),
  KEY idx_model_default (is_default)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CR-012 厂商下的模型条目';

-- MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，用 information_schema 判断后动态执行。
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'student_helper'
    AND TABLE_NAME = 't_session'
    AND COLUMN_NAME = 'model_id'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE t_session ADD COLUMN model_id BIGINT NULL COMMENT ''CR-012 会话所选模型条目 id''',
  'SELECT ''t_session.model_id already exists, skipped'' AS upgrade_result');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT 'upgrade-s4 done' AS result,
  (SELECT COUNT(*) FROM information_schema.TABLES
   WHERE TABLE_SCHEMA = 'student_helper'
     AND TABLE_NAME IN ('t_model_provider', 't_provider_model')) AS new_tables,
  (SELECT COUNT(*) FROM t_model_provider) AS provider_rows,
  (SELECT COUNT(*) FROM t_provider_model) AS model_rows;
