-- =====================================================================
-- S8 升级脚本（幂等）：TCR-052 / CR-027 模型行为过程落库（T-502）
-- 技术规范 5.21 / SRS 13.32 / 任务表 v5.9 第 14 章；MySQL 8.0.37 / utf8mb4
--
-- 内容（仅新增一张展示用表，不改既有表）：
--   t_message_process：按助手消息 id 记录本轮的工具 / 技能 / 思考过程
--
-- 说明：过程只作展示，不参与解题判定；删除会话时由 MessageRepository 的既有清理
-- 逻辑连带删除（按 message_id 关联）。应用不会自动执行本脚本
-- （spring.sql.init.mode=never）。脚本可重复执行。
--
-- 执行方式：mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u root student_helper \
--           < src/main/resources/db/upgrade-s8.sql
-- =====================================================================

USE student_helper;

CREATE TABLE IF NOT EXISTS t_message_process (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    message_id  BIGINT       NOT NULL,
    seq         INT          NOT NULL,
    kind        VARCHAR(24)  NOT NULL,
    title       VARCHAR(255) NOT NULL,
    detail      TEXT         NULL,
    elapsed_ms  BIGINT       NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_process_message_seq (message_id, seq),
    KEY idx_process_message (message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
