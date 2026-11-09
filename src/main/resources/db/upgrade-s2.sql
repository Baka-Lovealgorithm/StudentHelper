-- 大学生解题助手 Agent — S2 幂等升级脚本（交付脚本，S1 阶段不执行）
-- 对应 docs/02-技术规范.md 5.5；MySQL 8.0.37 / utf8mb4 / InnoDB
-- 执行方式（S2 开工时，在仓库根目录；口令只经 MYSQL_PWD 临时环境变量传递）：
--   mysql -h 127.0.0.1 -P 3306 -u root < src/main/resources/db/upgrade-s2.sql
-- 应用不会自动执行本脚本（spring.sql.init.mode=never）。
--
-- 幂等性：全部为新建表，使用 CREATE TABLE IF NOT EXISTS，可重复执行；
--         与 upgrade-v2.0.sql 的「加列」场景不同，无需 information_schema + 动态 ALTER。
--         后续结构演进另起 upgrade-s2.1.sql，不要直接改本文件。
-- 设计约束：本机单用户，所有表不携带 user_id；不修改 S1 已冻结的 4 张表；
--         跨表关联只记 id，不建外键约束，级联清理由 Application 层编排（沿用 S1 做法）。
-- 当前状态：2026-11-09 按用户决定「只出脚本不执行」，S1 库仍为 4 张表。

USE student_helper;

-- T-201 跨会话长期记忆与用户画像：单行画像 + 可清空的记忆条目
CREATE TABLE IF NOT EXISTS t_user_profile (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  profile_json  MEDIUMTEXT   NULL COMMENT '画像与偏好 JSON：学科 / 讲解深度 / FR-204 可选段落开关',
  updated_at    DATETIME(3)  NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单用户，应用层只维护一行';

CREATE TABLE IF NOT EXISTS t_memory (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  kind              VARCHAR(32)  NOT NULL COMMENT 'PREFERENCE / FACT / SUMMARY',
  content           TEXT         NOT NULL,
  source_session_id BIGINT       NULL,
  source_message_id BIGINT       NULL,
  enabled           TINYINT      NOT NULL DEFAULT 1 COMMENT '0=已停用，便于单条清空',
  created_at        DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_memory_kind (kind, enabled, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- T-202 错题本 + T-207 反馈闭环：一张标记表承载两种用途
-- 错题本 = kind='WRONG' 的检索视图，不另建冗余表，避免双写不一致
CREATE TABLE IF NOT EXISTS t_message_feedback (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  message_id  BIGINT       NOT NULL,
  session_id  BIGINT       NOT NULL,
  kind        VARCHAR(24)  NOT NULL COMMENT 'WRONG / INCORRECT / REGENERATE',
  subject     VARCHAR(64)  NULL COMMENT '错题本按学科检索，允许为空',
  note        VARCHAR(500) NULL,
  created_at  DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_feedback (message_id, kind),
  KEY idx_fb_wrong (kind, subject, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- T-203 知识点回顾与掌握度：聚合表 + 命中明细
CREATE TABLE IF NOT EXISTS t_knowledge_point (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  subject          VARCHAR(64)  NOT NULL,
  name             VARCHAR(128) NOT NULL,
  hit_count        INT          NOT NULL DEFAULT 0,
  mastery          DECIMAL(4,3) NULL COMMENT '0.000~1.000，由 T-205 判分结果聚合',
  last_message_id  BIGINT       NULL,
  created_at       DATETIME(3)  NOT NULL,
  updated_at       DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_kp (subject, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_knowledge_hit (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  kp_id       BIGINT      NOT NULL,
  message_id  BIGINT      NOT NULL,
  session_id  BIGINT      NOT NULL,
  created_at  DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_kh_kp (kp_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- T-205 举一反三：生成 → 作答 → 判分 → 落库四步状态
CREATE TABLE IF NOT EXISTS t_practice (
  id                 BIGINT       NOT NULL AUTO_INCREMENT,
  source_message_id  BIGINT       NOT NULL COMMENT '原始题目消息 id',
  session_id         BIGINT       NOT NULL,
  variant_text       MEDIUMTEXT   NOT NULL COMMENT '相似题，明确不含答案',
  user_answer        MEDIUMTEXT   NULL,
  score              DECIMAL(5,2) NULL,
  judge_result       VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / PASS / FAIL',
  feedback           TEXT         NULL,
  created_at         DATETIME(3)  NOT NULL,
  judged_at          DATETIME(3)  NULL,
  PRIMARY KEY (id),
  KEY idx_practice_session (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- T-208 用户文件系统工作区：登记目录与边界校验结果
CREATE TABLE IF NOT EXISTS t_workspace (
  id           BIGINT        NOT NULL AUTO_INCREMENT,
  root_path    VARCHAR(1000) NOT NULL,
  enabled      TINYINT       NOT NULL DEFAULT 0,
  validated_at DATETIME(3)   NULL COMMENT '目录存在 / 可写 / 边界校验通过时间',
  created_at   DATETIME(3)   NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- T-209 引导模式分步解锁：逐条消息记录已解锁层级
CREATE TABLE IF NOT EXISTS t_step_unlock (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  session_id   BIGINT      NOT NULL,
  message_id   BIGINT      NOT NULL,
  step         TINYINT     NOT NULL COMMENT '1=思路 2=关键步骤 3=答案',
  unlocked_at  DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_unlock (message_id, step),
  KEY idx_unlock_session (session_id, message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 只读校验：重复执行后仍应返回 8
SELECT COUNT(*) AS s2_table_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'student_helper'
  AND TABLE_NAME IN (
    't_user_profile', 't_memory', 't_message_feedback', 't_knowledge_point',
    't_knowledge_hit', 't_practice', 't_workspace', 't_step_unlock'
  );
