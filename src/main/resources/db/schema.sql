-- 大学生解题助手 Agent — S1 建库脚本
-- 对应 docs/02-技术规范.md 5.2；MySQL 8.0.37 / utf8mb4
-- 执行方式（PowerShell，在仓库根目录）：
--   mysql -h 127.0.0.1 -P 3306 -u root -p < src/main/resources/db/schema.sql
-- 应用不会自动执行本脚本（spring.sql.init.mode=never）。

CREATE DATABASE IF NOT EXISTS student_helper
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE student_helper;

CREATE TABLE IF NOT EXISTS t_session (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  title         VARCHAR(200) NOT NULL DEFAULT '新会话',
  guide_mode    TINYINT      NOT NULL DEFAULT 0 COMMENT '0=完整解答 1=仅思路',
  created_at    DATETIME(3)  NOT NULL,
  updated_at    DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_session_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_message (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  session_id        BIGINT       NOT NULL,
  role              VARCHAR(16)  NOT NULL COMMENT 'USER / ASSISTANT',
  content           MEDIUMTEXT   NULL,
  hint              VARCHAR(500) NULL COMMENT '本轮难度与讲解要求原文（FR-109 / AC-109-3）',
  image_paths       TEXT         NULL COMMENT 'JSON 数组，相对路径',
  model_config_id   BIGINT       NULL,
  guide_mode        TINYINT      NOT NULL DEFAULT 0,
  status            VARCHAR(16)  NOT NULL COMMENT 'PENDING / SUCCESS / FAILED',
  error_code        VARCHAR(16)  NULL,
  prompt_tokens     INT          NULL,
  completion_tokens INT          NULL,
  duration_ms       INT          NULL,
  created_at        DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_msg_session (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_model_config (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  name             VARCHAR(100) NOT NULL,
  provider         VARCHAR(50)  NOT NULL COMMENT '模板标识，如 openai / deepseek / ollama / custom',
  base_url         VARCHAR(500) NOT NULL,
  api_key_cipher   TEXT         NULL COMMENT 'AES-GCM，Base64(IV+ciphertext)；本地 Ollama 可为空',
  model_name       VARCHAR(200) NOT NULL,
  vision_supported TINYINT      NOT NULL DEFAULT 0 COMMENT '是否支持图片输入',
  stream_supported TINYINT      NOT NULL DEFAULT 1,
  extra_options    TEXT         NULL COMMENT 'JSON：temperature / nativeStructuredOutput 等',
  is_current       TINYINT      NOT NULL DEFAULT 0,
  created_at       DATETIME(3)  NOT NULL,
  updated_at       DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_cfg_current (is_current)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_call_log (
  id                BIGINT      NOT NULL AUTO_INCREMENT,
  message_id        BIGINT      NULL,
  model_config_id   BIGINT      NULL,
  model_name        VARCHAR(200) NULL,
  prompt_tokens     INT         NULL,
  completion_tokens INT         NULL,
  duration_ms       INT         NULL,
  status            VARCHAR(16) NOT NULL,
  error_summary     VARCHAR(500) NULL,
  created_at        DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_log_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 不建 t_user（AC-112-3）：本机单用户，数据不携带 user_id。
