-- =====================================================================
-- StudentHelper 初始化脚本（全新环境一次性执行）
-- 兼容 MySQL 8.0+ / utf8mb4
--
-- 内容：
--   1) 20 张表的完整结构（当前版本，无需再执行 upgrade-s*.sql）
--   2) 能力类种子数据：技能 2 条（mermaid-diagram / skill-creator）
--                      学科规范 4 条（数据结构 / 高等数学 / 计算机网络 / 编译原理）
--                      功能开关 12 条（与开发机当前状态一致）
--
-- 刻意不包含（安全与隐私）：
--   * 模型配置与 API Key：t_model_config / t_model_provider / t_provider_model 只建空表，
--     Key 一律在「模型配置」界面填写，由本机主密钥加密后入库
--   * 聊天记录与个人学习数据：t_session / t_message / t_message_process / t_message_feedback /
--     t_call_log / t_practice / t_step_unlock / t_workspace / t_knowledge_point /
--     t_knowledge_hit / t_memory / t_user_profile 均只建空表
--
-- 执行：
--   mysql -h 127.0.0.1 -P 3306 -u root -p --default-character-set=utf8mb4 \
--         student_helper < src/main/resources/db/init.sql
--
-- 注意：脚本会 DROP 并重建同名表，仅用于空库初始化，已有数据的库不要执行。
-- =====================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `t_call_log`;
CREATE TABLE `t_call_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `message_id` bigint DEFAULT NULL,
  `model_config_id` bigint DEFAULT NULL,
  `model_name` varchar(200) DEFAULT NULL,
  `prompt_tokens` int DEFAULT NULL,
  `completion_tokens` int DEFAULT NULL,
  `cache_hit_tokens` int NOT NULL DEFAULT '0' COMMENT '缓存命中 prompt token（CR-065）',
  `cache_miss_tokens` int NOT NULL DEFAULT '0' COMMENT '缓存未命中 prompt token，prompt - hit（CR-065）',
  `duration_ms` int DEFAULT NULL,
  `status` varchar(16) NOT NULL,
  `error_summary` varchar(500) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `is_debug` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'CR-014 调试/桩服务调用标记，用量看板不统计',
  `subject_hit` varchar(500) DEFAULT NULL COMMENT 'CR-018 命中学科 code 与理由',
  PRIMARY KEY (`id`),
  KEY `idx_log_created` (`created_at`),
  KEY `idx_log_debug_created` (`is_debug`,`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=1271 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `t_feature_toggle`;
CREATE TABLE `t_feature_toggle` (
  `feature_key` varchar(64) NOT NULL,
  `enabled` tinyint NOT NULL DEFAULT '0' COMMENT '0=关闭（默认） / 1=开启',
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`feature_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='S2 逐功能独立开关，默认全部关闭（DD-01）';
DROP TABLE IF EXISTS `t_knowledge_hit`;
CREATE TABLE `t_knowledge_hit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `kp_id` bigint NOT NULL,
  `message_id` bigint NOT NULL,
  `session_id` bigint NOT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_kh_kp` (`kp_id`,`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=861 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `t_knowledge_point`;
CREATE TABLE `t_knowledge_point` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `subject` varchar(64) NOT NULL,
  `parent_name` varchar(128) NOT NULL DEFAULT '' COMMENT '大知识点，单级时为空串',
  `name` varchar(128) NOT NULL,
  `hit_count` int NOT NULL DEFAULT '0',
  `mastery` decimal(4,3) DEFAULT NULL COMMENT '0.000~1.000，由 T-205 判分结果聚合',
  `last_message_id` bigint DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kp2` (`subject`,`parent_name`,`name`)
) ENGINE=InnoDB AUTO_INCREMENT=1178 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `t_mcp_server`;
CREATE TABLE `t_mcp_server` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `transport` varchar(24) NOT NULL DEFAULT 'STREAMABLE_HTTP',
  `url` varchar(512) NOT NULL,
  `enabled` tinyint NOT NULL DEFAULT '0',
  `last_status` varchar(24) DEFAULT NULL,
  `last_error` varchar(500) DEFAULT NULL,
  `tool_count` int NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mcp_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='T-301 远程 MCP Server（仅远程，不启用 stdio）';
DROP TABLE IF EXISTS `t_memory`;
CREATE TABLE `t_memory` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `kind` varchar(32) NOT NULL COMMENT 'PREFERENCE / FACT / SUMMARY',
  `content` text NOT NULL,
  `source_session_id` bigint DEFAULT NULL,
  `source_message_id` bigint DEFAULT NULL,
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '0=已停用，便于单条清空',
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_memory_kind` (`kind`,`enabled`,`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `t_message`;
CREATE TABLE `t_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` bigint NOT NULL,
  `role` varchar(16) NOT NULL COMMENT 'USER / ASSISTANT',
  `content` mediumtext,
  `hint` varchar(500) DEFAULT NULL COMMENT '本轮难度与求解要求原文（FR-109 / AC-109-3）',
  `image_paths` text COMMENT 'JSON 数组，相对路径',
  `model_config_id` bigint DEFAULT NULL,
  `guide_mode` tinyint NOT NULL DEFAULT '0',
  `status` varchar(16) NOT NULL COMMENT 'PENDING / SUCCESS / FAILED',
  `error_code` varchar(16) DEFAULT NULL,
  `prompt_tokens` int DEFAULT NULL,
  `completion_tokens` int DEFAULT NULL,
  `duration_ms` int DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `section_schema` text COMMENT 'CR-018 该回答所用输出结构；NULL 按默认六段；FREE_TEXT 为追问',
  `subject_label` varchar(128) DEFAULT NULL COMMENT 'CR-018 该回答采用的学科与结构标签，供前端展示',
  `intent` varchar(16) DEFAULT NULL COMMENT 'CR-039 回答形态 SOLVE/FOLLOW_UP/EXPLAIN；NULL 为历史消息',
  PRIMARY KEY (`id`),
  KEY `idx_msg_session` (`session_id`,`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=2320 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `t_message_feedback`;
CREATE TABLE `t_message_feedback` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `message_id` bigint NOT NULL,
  `session_id` bigint NOT NULL,
  `kind` varchar(24) NOT NULL COMMENT 'WRONG / INCORRECT / REGENERATE',
  `subject` varchar(64) DEFAULT NULL COMMENT '错题本按学科检索，允许为空',
  `note` varchar(500) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_feedback` (`message_id`,`kind`),
  KEY `idx_fb_wrong` (`kind`,`subject`,`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=141 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `t_message_process`;
CREATE TABLE `t_message_process` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `message_id` bigint NOT NULL,
  `seq` int NOT NULL,
  `kind` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `detail` text COLLATE utf8mb4_unicode_ci,
  `elapsed_ms` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_message_seq` (`message_id`,`seq`),
  KEY `idx_process_message` (`message_id`)
) ENGINE=InnoDB AUTO_INCREMENT=553 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
DROP TABLE IF EXISTS `t_model_config`;
CREATE TABLE `t_model_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `provider` varchar(50) NOT NULL COMMENT '模板标识，如 openai / deepseek / ollama / custom',
  `base_url` varchar(500) NOT NULL,
  `api_key_cipher` text COMMENT 'AES-GCM，Base64(IV+ciphertext)；本地 Ollama 可为空',
  `model_name` varchar(200) NOT NULL,
  `vision_supported` tinyint NOT NULL DEFAULT '0' COMMENT '是否支持图片输入',
  `stream_supported` tinyint NOT NULL DEFAULT '1',
  `extra_options` text COMMENT 'JSON：temperature / nativeStructuredOutput 等',
  `is_current` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_cfg_current` (`is_current`)
) ENGINE=InnoDB AUTO_INCREMENT=554 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `t_model_provider`;
CREATE TABLE `t_model_provider` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `base_url` varchar(500) NOT NULL,
  `api_key_cipher` text COMMENT 'AES-GCM，Base64(IV+ciphertext)；沿用 ./data/.master_key',
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_provider_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=502 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CR-012 通用厂商配置（名称 / URL / Key）';
DROP TABLE IF EXISTS `t_practice`;
CREATE TABLE `t_practice` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `source_message_id` bigint NOT NULL COMMENT '原始题目消息 id',
  `session_id` bigint NOT NULL,
  `kp_id` bigint DEFAULT NULL COMMENT '本次练习对应知识点，供 mastery 聚合',
  `variant_text` mediumtext NOT NULL COMMENT '相似题，明确不含答案',
  `user_answer` mediumtext,
  `score` decimal(5,2) DEFAULT NULL,
  `judge_result` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / PASS / FAIL',
  `feedback` text,
  `created_at` datetime(3) NOT NULL,
  `judged_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_practice_session` (`session_id`,`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `t_provider_model`;
CREATE TABLE `t_provider_model` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `provider_id` bigint NOT NULL,
  `model_name` varchar(200) NOT NULL,
  `vision_supported` tinyint NOT NULL DEFAULT '0' COMMENT '是否支持图片输入（驱动 E2004）',
  `stream_supported` tinyint NOT NULL DEFAULT '1',
  `extra_options` text COMMENT 'JSON：temperature / nativeStructuredOutput 等',
  `is_default` tinyint NOT NULL DEFAULT '0' COMMENT '全局默认模型，新会话继承',
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `is_debug` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'CR-014 调试模型条目，其调用不计入用量看板',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_provider_model` (`provider_id`,`model_name`),
  KEY `idx_model_default` (`is_default`)
) ENGINE=InnoDB AUTO_INCREMENT=507 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CR-012 厂商下的模型条目';
DROP TABLE IF EXISTS `t_session`;
CREATE TABLE `t_session` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(200) NOT NULL DEFAULT '新会话',
  `guide_mode` tinyint NOT NULL DEFAULT '0' COMMENT '0=完整解答 1=仅思路',
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `model_id` bigint DEFAULT NULL COMMENT 'CR-012 会话所选模型条目 id',
  `subject_mode` varchar(16) NOT NULL DEFAULT 'AUTO' COMMENT 'CR-018 AUTO/MANUAL/OFF',
  `subject_skill_id` bigint DEFAULT NULL COMMENT 'CR-018 首轮判定后固化的学科规范 id',
  `skill_ids` varchar(128) DEFAULT NULL COMMENT 'CR-038 会话固化的通用技能 id，逗号分隔，最多 2 条',
  PRIMARY KEY (`id`),
  KEY `idx_session_updated` (`updated_at`)
) ENGINE=InnoDB AUTO_INCREMENT=825 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `t_skill`;
CREATE TABLE `t_skill` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `content` mediumtext NOT NULL,
  `enabled` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_skill` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='T-302 解题技能（Markdown 指令片段）';
DROP TABLE IF EXISTS `t_step_unlock`;
CREATE TABLE `t_step_unlock` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` bigint NOT NULL,
  `message_id` bigint NOT NULL,
  `step` tinyint NOT NULL COMMENT '1=思路 2=关键步骤 3=答案',
  `unlocked_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_unlock` (`message_id`,`step`),
  KEY `idx_unlock_session` (`session_id`,`message_id`)
) ENGINE=InnoDB AUTO_INCREMENT=39 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `t_subject`;
CREATE TABLE `t_subject` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `prompt_hint` text,
  `is_current` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_subject` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='T-307 学科与提示词模板';
DROP TABLE IF EXISTS `t_subject_skill`;
CREATE TABLE `t_subject_skill` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '内置稳定标识；自定义学科为 NULL',
  `name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `scope` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CUSTOM' COMMENT 'BUILTIN / CUSTOM',
  `enabled` tinyint(1) NOT NULL DEFAULT '0',
  `keywords` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `spec_md` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '回答规范（只写怎么答，不写教材知识）',
  `sections_json` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '{"full":[...],"idea":[...]}，标题须以 ## 开头',
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `spec_json` text COLLATE utf8mb4_unicode_ci COMMENT 'CR-039 三段规范 {"solve","followUp","explain"}；空段回落系统默认',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_subject_skill_name` (`name`),
  UNIQUE KEY `uk_subject_skill_code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CR-018 学科规范（与通用技能 t_skill 两条独立链路）';
DROP TABLE IF EXISTS `t_user_profile`;
CREATE TABLE `t_user_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `profile_json` mediumtext COMMENT '画像与偏好 JSON：学科 / 讲解深度 / FR-204 可选段落开关',
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='单用户，应用层只维护一行';
DROP TABLE IF EXISTS `t_workspace`;
CREATE TABLE `t_workspace` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `root_path` varchar(1000) NOT NULL,
  `enabled` tinyint NOT NULL DEFAULT '0',
  `validated_at` datetime(3) DEFAULT NULL COMMENT '目录存在 / 可写 / 边界校验通过时间',
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- 种子数据（能力与开关，无个人数据）
-- ---------------------------------------------------------------------

INSERT INTO `t_skill` (`id`, `name`, `description`, `content`, `enabled`, `created_at`, `updated_at`) VALUES (9,'skill-creator','Create new skills, modify and improve existing skills, and measure skill performance. Use when users want to create a skill from scratch, edit, or optimize an existing skill, run evals to test a skill','# Skill Creator\n\nA skill for creating new skills and iteratively improving them.\n\nAt a high level, the process of creating a skill goes like this:\n\n- Decide what you want the skill to do and roughly how it should do it\n- Write a draft of the skill\n- Create a few test prompts and run claude-with-access-to-the-skill on them\n- Help the user evaluate the results both qualitatively and quantitatively\n  - While the runs happen in the background, draft some quantitative evals if there aren\'t any (if there are some, you can either use as is or modify if you feel something needs to change about them). Then explain them to the user (or if they already existed, explain the ones that already exist)\n  - Use the `eval-viewer/generate_review.py` script to show the user the results for them to look at, and also let them look at the quantitative metrics\n- Rewrite the skill based on feedback from the user\'s evaluation of the results (and also if there are any glaring flaws that become apparent from the quantitative benchmarks)\n- Repeat until you\'re satisfied\n- Expand the test set and try again at larger scale\n\nYour job when using this skill is to figure out where the user is in this process and then jump in and help them progress through these stages. So for instance, maybe they\'re like \"I want to make a skill for X\". You can help narrow down what they mean, write a draft, write the test cases, figure out how they want to evaluate, run all the prompts, and repeat.\n\nOn the other hand, maybe they already have a draft of the skill. In this case you can go straight to the eval/iterate part of the loop.\n\nOf course, you should always be flexible and if the user is like \"I don\'t need to run a bunch of evaluations, just vibe with me\", you can do that instead.\n\nThen after the skill is done (but again, the order is flexible), you can also run the skill description improver, which we have a whole separate script for, to optimize the triggering of the skill.\n\nCool? Cool.\n\n## Communicating with the user\n\nThe skill creator is liable to be used by people across a wide range of familiarity with coding jargon. If you haven\'t heard (and how could you, it\'s only very recently that it started), there\'s a trend now where the power of Claude is inspiring plumbers to open up their terminals, parents and grandparents to google \"how to install npm\". On the other hand, the bulk of users are probably fairly computer-literate.\n\nSo please pay attention to context cues to understand how to phrase your communication! In the default case, just to give you some idea:\n\n- \"evaluation\" and \"benchmark\" are borderline, but OK\n- for \"JSON\" and \"assertion\" you want to see serious cues from the user that they know what those things are before using them without explaining them\n\nIt\'s OK to briefly explain terms if you\'re in doubt, and feel free to clarify terms with a short definition if you\'re unsure if the user will get it.\n\n---\n\n## Creating a skill\n\n### Capture Intent\n\nStart by understanding the user\'s intent. The current conversation might already contain a workflow the user wants to capture (e.g., they say \"turn this into a skill\"). If so, extract answers from the conversation history first — the tools used, the sequence of steps, corrections the user made, input/output formats observed. The user may need to fill the gaps, and should confirm before proceeding to the next step.\n\n1. What should this skill enable Claude to do?\n2. When should this skill trigger? (what user phrases/contexts)\n3. What\'s the expected output format?\n4. Should we set up test cases to verify the skill works? Skills with objectively verifiable outputs (file transforms, data extraction, code generation, fixed workflow steps) benefit from test cases. Skills with subjective outputs (writing style, art) often don\'t need them. Suggest the appropriate default based on the skill type, but let the user decide.\n\n### Interview and Research\n\nProactively ask questions about edge cases, input/output formats, example files, success criteria, and dependencies. Wait to write test prompts until you\'ve got this part ironed out.\n\nCheck available MCPs - if useful for research (searching docs, finding similar skills, looking up best practices), research in parallel via subagents if available, otherwise inline. Come prepared with context to reduce burden on the user.\n\n### Write the SKILL.md\n\nBased on the user interview, fill in these components:\n\n- **name**: Skill identifier\n- **description**: When to trigger, what it does. This is the primary triggering mechanism - include both what the skill does AND specific contexts for when to use it. All \"when to use\" info goes here, not in the body. Note: currently Claude has a tendency to \"undertrigger\" skills -- to not use them when they\'d be useful. To combat this, please make the skill descriptions a little bit \"pushy\". So for instance, instead of \"How to build a simple fast dashboard to display internal Anthropic data.\", you might write \"How to build a simple fast d',0,'2026-11-10 15:39:59.984','2026-11-15 14:29:09.123');
INSERT INTO `t_skill` (`id`, `name`, `description`, `content`, `enabled`, `created_at`, `updated_at`) VALUES (11,'mermaid-diagram','题目涉及树/图/链表形态、算法执行过程、状态迁移或坐标曲线时读入：给出 diagram_render 工具的调用口径、图类型选型与语法硬规则','【用途】题目涉及树 / 图 / 链表的形态、算法执行过程、状态迁移或坐标曲线时，先作图再讲解；不需要图示的题目不要画。\n【调用】用 diagram_render(code, alt) 工具：code 传 Mermaid 源码，alt 传一句简短说明。工具返回 ![说明](/api/images/…) 链接，必须原样放进答案对应段落（单独成行），图后用一句话说明「图说明了什么」。答案里不要出现 Mermaid 源码、代码块或「已生成图片」之类的过程描述。\n【数量】同一题最多 2 张图、总节点数 ≤20；工具报错先修语法重试一次，仍失败改用文字描述结构，不要把源码贴进答案。\n【选型】层次 / 父子或执行流程 → flowchart TD；顺序强 → flowchart LR；状态迁移 → stateDiagram-v2；对象关系 → classDiagram；多角色交互 → sequenceDiagram。\n【语法硬规则】\n1) 一律写 flowchart，不写 graph；\n2) 标签含空格、括号、逗号、冒号、斜杠、加减号、等号时必须写成 A[\"文本\"]，拿不准就加引号；\n3) 节点 id 用英文短名（root、left1），不要中文、不要单字母；\n4) 标签整词为 end 时必须写 A[\"end\"]；\n5) 不能写坐标、位置、颜色，不写 style / classDef / click；\n6) 标签内不用 HTML、不写 $...$ 公式，公式放正文；\n7) 单个节点文本 ≤16 字，长内容拆成多个节点。\n【禁止】不要输出 ASCII 图、不要生成图片文件、不要为凑格式而画图。',1,'2026-11-14 17:01:03.087','2026-11-15 11:19:42.735');
INSERT INTO `t_subject_skill` (`id`, `code`, `name`, `description`, `scope`, `enabled`, `keywords`, `spec_md`, `sections_json`, `created_at`, `updated_at`, `spec_json`) VALUES (1,'DATA_STRUCTURE','数据结构','数据结构与算法课程题目的术语、复杂度与推导口径，沿用默认六段结构','CUSTOM',1,'栈、队列、链表、树、二叉树、图、堆、哈希表、排序、查找、递归、遍历、时间复杂度、空间复杂度、大O','【适用范围】数据结构与算法题目（线性结构、树、图、排序查找、复杂度分析）。\n【记号与术语】Θ / O / Ω 严格区分上下界；未说明时「复杂度」默认指最坏情况；log 默认以 2 为底并写明；n 为问题规模，须声明含义；树的高度按边数计并注明口径。\n【推理要求】② 必须点出所用结构及其性质；③ 先给思路再给步骤；④ 每步说明对数据结构状态的改变；⑤ 结论必须可复算；⑥ 至少列 1 个易错点或验算方式。\n【依据】凡涉及复杂度或算法性质，必须给出推导或反例，不得凭印象断言；题目条件不足时明确指出缺什么条件，不得编造性质。\n【禁止】不使用未定义的自定义记号；不输出 ⑦ 及相似练习题；不用 JSON 包裹答案。','{\"full\":[\"## ① 题目理解\",\"## ② 涉及知识点\",\"## ③ 解题思路\",\"## ④ 详细步骤\",\"## ⑤ 最终答案\",\"## ⑥ 易错点 / 验算\"],\"idea\":[\"## ① 题目理解\",\"## ② 涉及知识点\",\"## ③ 解题思路\"],\"explain\":[\"## ① 概念定义\",\"## ② 关键性质与适用条件\",\"## ③ 典型例子\",\"## ④ 与相邻概念的区别\",\"## ⑤ 常见误区\"]}','2026-11-11 15:02:56.318','2026-11-15 19:50:54.751','{\"solve\":\"【适用范围】数据结构与算法题目（线性结构、树、图、排序查找、复杂度分析）。\\n【记号与术语】Θ / O / Ω 严格区分上下界；未说明时「复杂度」默认指最坏情况；log 默认以 2 为底并写明；n 为问题规模，须声明含义；树的高度按边数计并注明口径。\\n【推理要求】② 必须点出所用结构及其性质；③ 先给思路再给步骤；④ 每步说明对数据结构状态的改变；⑤ 结论必须可复算；⑥ 至少列 1 个易错点或验算方式。\\n【依据】凡涉及复杂度或算法性质，必须给出推导或反例，不得凭印象断言；题目条件不足时明确指出缺什么条件，不得编造性质。\\n【禁止】不使用未定义的自定义记号；不输出 ⑦ 及相似练习题；不用 JSON 包裹答案。\",\"followUp\":\"\",\"explain\":\"\"}');
INSERT INTO `t_subject_skill` (`id`, `code`, `name`, `description`, `scope`, `enabled`, `keywords`, `spec_md`, `sections_json`, `created_at`, `updated_at`, `spec_json`) VALUES (2,'ADVANCED_MATH','高等数学','极限、导数、积分与级数等推导型题目的规范，采用五段推导结构','CUSTOM',1,'极限、连续、导数、微分、中值定理、积分、不定积分、定积分、级数、收敛、泰勒、洛必达、偏导、重积分、微分方程','【适用范围】极限、连续、导数与微分、中值定理、积分、级数、多元函数与微分方程等推导型题目。\n【记号与术语】变量与参数含义须先声明；lim / ∫ / ∑ 必须写清上下限与趋近方向；使用 o、O、~ 时给出定义；分段函数须写明分界点处的处理方式。\n【推理要求】② 必须写出所用定理名称及其适用条件（如洛必达需 0/0 或 ∞/∞ 且导数比极限存在）；③ 逐步推导、不跳步，每步注明依据；④ 结论须回代检验（定义域、收敛性、常数项、量纲）；⑤ 至少列 1 个常见误用或边界情况。\n【依据】不得编造公式或定理；条件不足时明确指出并说明还需什么条件，不得强行给数值答案；数值近似须标明方法与误差，不得当作精确结论。\n【仅思路】按 idea 数组的 3 段输出，③ 只写思路要点，不写完整推导、不给最终数值。\n【禁止】不用 JSON 包裹答案；不输出 ⑦ 及相似练习题。','{\"full\":[\"## ① 题意与已知条件\",\"## ② 定理与公式\",\"## ③ 推导过程\",\"## ④ 结论与验证\",\"## ⑤ 易错点与适用边界\"],\"idea\":[\"## ① 题意与已知条件\",\"## ② 定理与公式\",\"## ③ 思路要点\"]}','2026-11-11 15:02:56.321','2026-11-11 15:02:56.321','{\"solve\": \"【适用范围】极限、连续、导数与微分、中值定理、积分、级数、多元函数与微分方程等推导型题目。\\n【记号与术语】变量与参数含义须先声明；lim / ∫ / ∑ 必须写清上下限与趋近方向；使用 o、O、~ 时给出定义；分段函数须写明分界点处的处理方式。\\n【推理要求】② 必须写出所用定理名称及其适用条件（如洛必达需 0/0 或 ∞/∞ 且导数比极限存在）；③ 逐步推导、不跳步，每步注明依据；④ 结论须回代检验（定义域、收敛性、常数项、量纲）；⑤ 至少列 1 个常见误用或边界情况。\\n【依据】不得编造公式或定理；条件不足时明确指出并说明还需什么条件，不得强行给数值答案；数值近似须标明方法与误差，不得当作精确结论。\\n【仅思路】按 idea 数组的 3 段输出，③ 只写思路要点，不写完整推导、不给最终数值。\\n【禁止】不用 JSON 包裹答案；不输出 ⑦ 及相似练习题。\", \"explain\": \"\", \"followUp\": \"\"}');
INSERT INTO `t_subject_skill` (`id`, `code`, `name`, `description`, `scope`, `enabled`, `keywords`, `spec_md`, `sections_json`, `created_at`, `updated_at`, `spec_json`) VALUES (5,NULL,'计算机网络','计算机网络课程的计算与协议分析题：子网划分/CIDR、时延与吞吐量、TCP 序号与窗口、拥塞控制、封装转发、协议交互时序；采用六段分层推导结构','CUSTOM',1,'TCP、UDP、IP、子网划分、CIDR、路由聚合、ARP、DHCP、DNS、HTTP、以太网、CSMA/CD、交换机、冲突域、广播域、VLAN、NAT、MTU、MSS、RTT、时延、带宽时延积、吞吐量、滑动窗口、停止-等待、GBN、SR、三次握手、四次挥手、流量控制、拥塞控制、cwnd、序号、确认号、CRC、封装、OSI、TCP/IP、分层、PDU','【适用范围】子网划分与 CIDR 聚合、时延与吞吐量、滑动窗口与可靠传输、TCP 序号与拥塞控制、封装转发与协议交互时序、冲突域与广播域等计算机网络题目。\n【记号与术语】速率写 bit/s、数据量写 bit 或 Byte 并标明 1B=8bit；K/M 取 10^3 还是 2^10 须声明；时延分发送/传播/排队/处理并注明所用带宽与距离；序号、窗口、MSS、MTU 区分字节与报文段；CIDR 写清前缀长度与网络地址。\n【推理要求】① 列已知条件与隐含假设；② 指出层次、协议与 PDU；③ 先定分析口径再选公式或状态机；④ 计算题按「公式→代入→结果（带单位）」逐项写，协议题按时间序逐跳列封装/解封装与序号、确认号、窗口、cwnd 的变化；⑤ 结论可复算；⑥ 至少 1 个易错点（单位、RTT、流量控制与拥塞控制混用）。\n【依据】不得编造协议字段、定时器默认值或 RFC 数值；RTT、MSS、初始窗口、传播时延等未给条件须显式假设或指出缺失，不得悄悄代入。\n【仅思路】按 idea 数组 3 段输出，③ 只写口径与依据，不给完整推导与最终数值。\n【禁止】不用 JSON 包裹答案；不输出 ⑦ 及相似练习题；不混用 bit/Byte；不把拥塞控制当流量控制。','{\"full\":[\"## ① 题意与网络条件\",\"## ② 涉及协议与分层\",\"## ③ 分析思路与口径\",\"## ④ 推导 / 交互过程\",\"## ⑤ 结论与验算\",\"## ⑥ 易错点与边界\"],\"idea\":[\"## ① 题意与网络条件\",\"## ② 涉及协议与分层\",\"## ③ 分析思路与口径\"],\"explain\":[\"## ① 概念定义\",\"## ② 关键性质与适用条件\",\"## ③ 典型例子\",\"## ④ 与相邻概念的区别\",\"## ⑤ 常见误区\"]}','2026-11-12 15:00:35.835','2026-11-16 10:12:55.265','{\"solve\":\"【适用范围】子网划分与 CIDR 聚合、时延与吞吐量、滑动窗口与可靠传输、TCP 序号与拥塞控制、封装转发与协议交互时序、冲突域与广播域等计算机网络题目。\\n【记号与术语】速率写 bit/s、数据量写 bit 或 Byte 并标明 1B=8bit；K/M 取 10^3 还是 2^10 须声明；时延分发送/传播/排队/处理并注明所用带宽与距离；序号、窗口、MSS、MTU 区分字节与报文段；CIDR 写清前缀长度与网络地址。\\n【推理要求】① 列已知条件与隐含假设；② 指出层次、协议与 PDU；③ 先定分析口径再选公式或状态机；④ 计算题按「公式→代入→结果（带单位）」逐项写，协议题按时间序逐跳列封装/解封装与序号、确认号、窗口、cwnd 的变化；⑤ 结论可复算；⑥ 至少 1 个易错点（单位、RTT、流量控制与拥塞控制混用）。\\n【依据】不得编造协议字段、定时器默认值或 RFC 数值；RTT、MSS、初始窗口、传播时延等未给条件须显式假设或指出缺失，不得悄悄代入。\\n【仅思路】按 idea 数组 3 段输出，③ 只写口径与依据，不给完整推导与最终数值。\\n【禁止】不用 JSON 包裹答案；不输出 ⑦ 及相似练习题；不混用 bit/Byte；不把拥塞控制当流量控制。\",\"followUp\":\"\",\"explain\":\"\"}');
INSERT INTO `t_subject_skill` (`id`, `code`, `name`, `description`, `scope`, `enabled`, `keywords`, `spec_md`, `sections_json`, `created_at`, `updated_at`, `spec_json`) VALUES (6,NULL,'编译原理','编译原理课程题目与概念：正规式与 NFA/DFA、CFG 与推导、LL(1)/LR 族分析表、语法制导翻译与中间代码、基本块与优化；采用六段形式化推导结构','CUSTOM',1,'词法分析、语法分析、正规式、正则表达式、有限自动机、NFA、DFA、确定化、子集构造、最小化、ε、闭包、状态转换图、token、上下文无关文法、CFG、BNF、EBNF、产生式、推导、最左推导、最右推导、语法树、二义性、左递归、左公因子、消除左递归、FIRST、FOLLOW、SELECT、LL(1)、预测分析表、递归下降、自上而下、自下而上、移进-归约、短语、直接短语、句柄、LR(0)、SLR(1)、LR(1)、LALR(1)、项目、项目集规范族、ACTION、GOTO、移进归约冲突、归约归约冲突、语法制导翻译、SDT、属性文法、综合属性、继承属性、符号表、类型检查、三地址码、四元式、三元式、逆波兰、回填、布尔表达式、短路求值、基本块、控制流图、DAG、公共子表达式、循环优化、数据流分析、寄存器分配、目标代码、活动记录、运行时存储、展望符、$、接受、归约','【适用范围】正规式与 NFA/DFA 确定化和最小化、CFG 与推导、LL(1)/LR 族分析表、移进-归约、语法制导翻译与中间代码、优化。\n【记号与术语】产生式写 A→α，大写非终结符、小写终结符，ε 空串、$ 结束符；LR 项目写 A→α·β 且注明圆点与展望符；FIRST/FOLLOW/SELECT 附推导依据，状态 I0/I1 列项目集，四元式按（op,arg1,arg2,result）书写；文法未给全先补全并标注假设。\n【推理要求】① 抄录文法 / 正规式与隐含假设；② 指明阶段与概念；③ 先定口径（分析方向、ε 与 $ 处理、冲突判定）；④ 按「规则→逐项计算→集合 / 状态 / 分析表 / 语法树」逐步写，每步标产生式编号；⑤ 结论可复算（完整集合、分析表、DFA 状态表或代码序列）；⑥ 至少 1 个易错点或验算（用输入串走一遍分析表）。\n【依据】判断 LL(1)/SLR(1)/LR(1)/LALR(1) 或二义性，须给集合、冲突项或两棵语法树为证；优先级、结合性、开始符号未给时显式声明或指出缺失，不得凭印象断言。\n【仅思路】按 idea 数组 3 段输出，③ 只写口径与依据，不给完整集合与分析表。\n【禁止】不用 JSON 包裹答案；不输出 ⑦ 及练习题；不混用 FIRST/FOLLOW、短语/句柄、移进/归约、LL/LR；不省略 ε 与 $。','{\"full\":[\"## ① 题意与形式化条件\",\"## ② 涉及阶段与概念\",\"## ③ 分析口径与判定依据\",\"## ④ 推导 / 构造过程\",\"## ⑤ 结论与验算\",\"## ⑥ 易错点与边界\"],\"idea\":[\"## ① 题意与形式化条件\",\"## ② 涉及阶段与概念\",\"## ③ 分析口径与判定依据\"],\"explain\":[\"## ① 概念与形式化定义\",\"## ② 关键性质与成立条件\",\"## ③ 典型例子与手工推演\",\"## ④ 与相邻概念的对比\",\"## ⑤ 常见误区与边界\"]}','2026-11-15 10:02:08.472','2026-11-15 10:02:08.472','{\"solve\":\"【适用范围】正规式与 NFA/DFA 确定化和最小化、CFG 与推导、LL(1)/LR 族分析表、移进-归约、语法制导翻译与中间代码、优化。\\n【记号与术语】产生式写 A→α，大写非终结符、小写终结符，ε 空串、$ 结束符；LR 项目写 A→α·β 且注明圆点与展望符；FIRST/FOLLOW/SELECT 附推导依据，状态 I0/I1 列项目集，四元式按（op,arg1,arg2,result）书写；文法未给全先补全并标注假设。\\n【推理要求】① 抄录文法 / 正规式与隐含假设；② 指明阶段与概念；③ 先定口径（分析方向、ε 与 $ 处理、冲突判定）；④ 按「规则→逐项计算→集合 / 状态 / 分析表 / 语法树」逐步写，每步标产生式编号；⑤ 结论可复算（完整集合、分析表、DFA 状态表或代码序列）；⑥ 至少 1 个易错点或验算（用输入串走一遍分析表）。\\n【依据】判断 LL(1)/SLR(1)/LR(1)/LALR(1) 或二义性，须给集合、冲突项或两棵语法树为证；优先级、结合性、开始符号未给时显式声明或指出缺失，不得凭印象断言。\\n【仅思路】按 idea 数组 3 段输出，③ 只写口径与依据，不给完整集合与分析表。\\n【禁止】不用 JSON 包裹答案；不输出 ⑦ 及练习题；不混用 FIRST/FOLLOW、短语/句柄、移进/归约、LL/LR；不省略 ε 与 $。\",\"followUp\":\"【适用范围】已有解题或讲解之后的追问（问为什么、换文法或输入串、某一步怎么来、两个概念有何区别），按自由文本作答，不套任何分段结构。\\n【一致性】沿用上轮已声明的产生式编号、记号（A→α、ε、$、FIRST/FOLLOW、项目集编号）与假设；若追问改变了文法、输入串或优先级，先用一句话说明「相对上轮改变了什么」再作答，不得悄悄换条件。\\n【推理要求】先直接给出所问的结论或判定，再补最小必要依据（一条产生式、一次集合计算、一步状态转移或一个短例子）；只答所问，不重述整题推导、不重复输出六段结构。\\n【依据】涉及冲突、二义性或接受性判断时给出可复核的局部证据；若追问推翻了上轮结论，明确指出修正点与原因，不含糊带过。\\n【边界】条件不足或追问超出给定文法时，指出缺什么条件并给出在该条件下才成立的答案，不得编造产生式或分析表；不输出练习题、不用 JSON 包裹答案、不用 ⑦ 编号。\",\"explain\":\"【适用范围】编译原理概念讲解（词法 / 语法 / 语义各阶段、自动机、文法与推导、LL 与 LR 族、语法制导翻译、中间代码、运行时存储与优化），按 sections_json 的 explain 五段逐字输出。\\n【推理要求】① 给形式化定义并说明每个符号含义（产生式、ε、$、展望符等）；② 写清关键性质与成立条件（如 LL(1) 的 SELECT 集互不相交、LR(0) 项目集无冲突），不用口诀代替条件；③ 至少 1 个可手算的小例子（短文法或短输入串），逐步演算到可验证的结果；④ 与相邻概念对比时指出「判定对象 / 分析方向 / 所需信息」的差异，如 LL(1) 与 LR(1)、NFA 与 DFA、短语与句柄；⑤ 列 2 个常见误区并给出反例或正确做法。\\n【依据】性质与复杂度须给出理由或反例；教材口径有差异时（FOLLOW 是否含 $、ε 产生式的 FIRST、归约时机）先声明采用哪种口径，不得混用两套定义。\\n【禁止】不输出 ⑦ 及相似练习题、不用 JSON 包裹答案、不把构造过程写成不可复算的结论、不在讲解里偷换成解题的六段结构。\"}');
INSERT INTO `t_feature_toggle` (`feature_key`, `enabled`, `updated_at`) VALUES ('conversationCompaction',1,'2026-11-11 11:47:51.261');
INSERT INTO `t_feature_toggle` (`feature_key`, `enabled`, `updated_at`) VALUES ('historyImage',1,'2026-11-13 12:16:37.234');
INSERT INTO `t_feature_toggle` (`feature_key`, `enabled`, `updated_at`) VALUES ('knowledgeExtract',1,'2026-11-11 21:01:26.331');
INSERT INTO `t_feature_toggle` (`feature_key`, `enabled`, `updated_at`) VALUES ('learningStats',1,'2026-11-11 20:02:28.341');
INSERT INTO `t_feature_toggle` (`feature_key`, `enabled`, `updated_at`) VALUES ('mcp',1,'2026-11-09 19:42:16.924');
INSERT INTO `t_feature_toggle` (`feature_key`, `enabled`, `updated_at`) VALUES ('memory',1,'2026-11-11 09:52:30.229');
INSERT INTO `t_feature_toggle` (`feature_key`, `enabled`, `updated_at`) VALUES ('multiSolution',0,'2026-11-13 20:43:43.418');
INSERT INTO `t_feature_toggle` (`feature_key`, `enabled`, `updated_at`) VALUES ('practice',1,'2026-11-11 20:41:29.729');
INSERT INTO `t_feature_toggle` (`feature_key`, `enabled`, `updated_at`) VALUES ('skill',1,'2026-11-13 14:41:03.782');
INSERT INTO `t_feature_toggle` (`feature_key`, `enabled`, `updated_at`) VALUES ('stepUnlock',0,'2026-11-14 20:54:01.718');
INSERT INTO `t_feature_toggle` (`feature_key`, `enabled`, `updated_at`) VALUES ('usagePanel',1,'2026-11-11 20:02:28.405');
INSERT INTO `t_feature_toggle` (`feature_key`, `enabled`, `updated_at`) VALUES ('wrongBook',1,'2026-11-11 19:35:09.330');

SET FOREIGN_KEY_CHECKS = 1;
