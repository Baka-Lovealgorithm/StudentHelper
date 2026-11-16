-- =====================================================================
-- S14 升级脚本（幂等）：TCR-073 / CR-046 内置绘图技能（Mermaid）
-- 技术规范 5.40 / SRS 13.51 / 任务表 v5.29 第 5 章；MySQL 8.0.37 / utf8mb4
--
-- 内容（纯数据，无 DDL）：
--   1) t_skill 插入内置技能 mermaid-diagram（enabled = 1，正文 ≤800 字）
--
-- 说明：
--   * 走既有 CR-026「技能清单 + skill_get 按需读入」链路：清单里只出现名称与说明，
--     模型判断本题需要图示时再读入正文，不占每轮 prompt。
--   * 正文受 SkillApplicationService.MAX_INJECTED_CHARS = 800 限制，故只保留
--     「何时画 / 图类型怎么选 / 7 条语法硬规则 / 一个模板」，不搬外部 skill 的全部章节。
--   * 技能总闸仍是功能开关 skill（S2Feature.SKILL），关闭后清单与 skill_get 均返回空。
--   * 图由前端 MarkdownRenderer 渲染（mermaid 懒加载 → SVG data URL → <img>），
--     本脚本不涉及渲染、不写文件、不新增接口。
--
-- 执行方式：mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u root student_helper \
--           < src/main/resources/db/upgrade-s14.sql
-- 应用不会自动执行本脚本（spring.sql.init.mode=never）。脚本可重复执行。
-- =====================================================================

USE student_helper;

INSERT INTO t_skill (name, description, content, enabled, created_at, updated_at)
SELECT
  'mermaid-diagram',
  '题目涉及树/图/链表形态、算法执行过程、状态迁移或坐标曲线时读入：给出图类型选型、语法硬规则与模板，按 ```mermaid 图块输出',
  '【用途】题目涉及树/图/链表的形态、算法执行过程、状态迁移或坐标曲线时，用 Mermaid 图块把结构画出来再配文字讲解；不需要图示的题目不要画。
【格式】在对应段落内另起一行写 ```mermaid 围栏，前后各留一个空行；同一题最多 2 张图、总节点数 ≤20；图后必须用一句话说明「图说明了什么」，图不能替代推导步骤与最终答案。
【选型】层次 / 父子关系或执行流程 → flowchart TD；前后顺序强 → flowchart LR；状态迁移 → stateDiagram-v2；对象关系 → classDiagram；多角色交互 → sequenceDiagram。不要用 pie、gantt、gitGraph、mindmap。
【硬规则】
1) 一律写 flowchart，不写 graph；
2) 标签含空格、括号、逗号、冒号、斜杠、加减号、等号时必须写成 A["文本"]，拿不准就加引号；
3) 节点 id 用英文短名（root、left1），不要中文、不要单字母；
4) 标签整词为 end 时必须写 A["end"]；
5) 不能写坐标、位置、颜色，不写 style / classDef / click，布局只靠声明顺序与 subgraph 调整；
6) 标签内不用 HTML、不写 $...$ 公式，公式放正文；
7) 单个节点文本 ≤16 字，长内容拆成多个节点。
【模板】
flowchart TD
  A["根 10"] --> B["5"]
  A --> C["15"]
  B --> D["2"]
【禁止】不要输出 ASCII 图、不要生成图片文件、不要为凑格式而画图。',
  1, NOW(3), NOW(3)
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM t_skill WHERE name = 'mermaid-diagram');

-- 自检：应为 1 行、enabled = 1、正文长度 ≤800
SELECT COUNT(*) AS skill_rows,
       MAX(enabled) AS enabled,
       MAX(CHAR_LENGTH(content)) AS content_chars
FROM t_skill
WHERE name = 'mermaid-diagram';
