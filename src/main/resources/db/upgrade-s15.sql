-- =====================================================================
-- S15 升级脚本（幂等）：TCR-074 / CR-047 作图改由后端渲染（工具化）
-- 技术规范 5.41 / SRS 13.52 / 任务表 v5.30 第 5 章；MySQL 8.0.37 / utf8mb4
--
-- 内容（纯数据，无 DDL）：
--   1) 内置技能 mermaid-diagram 的正文改为「调用 diagram_render 工具」口径
--      （CR-046 版本教模型输出 ```mermaid 图块，改造后图由后端渲染、答案里只留图片链接）
--
-- 说明：
--   * t_skill.content 上限 5000 字，本正文约 620 字；skill_get 注入上限 800 字，未超。
--   * 复跑两次结果一致：先按 name 插入（不存在时），再无条件覆盖 description / content / enabled。
--   * 技能总闸仍是功能开关 skill；关闭后清单与 skill_get 均返回空。
--
-- 执行方式：mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u root student_helper \
--           < src/main/resources/db/upgrade-s15.sql
-- 应用不会自动执行本脚本（spring.sql.init.mode=never）。脚本可重复执行。
-- =====================================================================

USE student_helper;

INSERT INTO t_skill (name, description, content, enabled, created_at, updated_at)
SELECT
  'mermaid-diagram',
  '题目涉及树/图/链表形态、算法执行过程、状态迁移或坐标曲线时读入：给出 diagram_render 工具的调用口径、图类型选型与语法硬规则',
  '占位',
  1, NOW(3), NOW(3)
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM t_skill WHERE name = 'mermaid-diagram');

UPDATE t_skill
SET description = '题目涉及树/图/链表形态、算法执行过程、状态迁移或坐标曲线时读入：给出 diagram_render 工具的调用口径、图类型选型与语法硬规则',
    content = '【用途】题目涉及树 / 图 / 链表的形态、算法执行过程、状态迁移或坐标曲线时，先作图再讲解；不需要图示的题目不要画。
【调用】用 diagram_render(code, alt) 工具：code 传 Mermaid 源码，alt 传一句简短说明。工具返回 ![说明](/api/images/…) 链接，必须原样放进答案对应段落（单独成行），图后用一句话说明「图说明了什么」。答案里不要出现 Mermaid 源码、代码块或「已生成图片」之类的过程描述。
【数量】同一题最多 2 张图、总节点数 ≤20；工具报错先修语法重试一次，仍失败改用文字描述结构，不要把源码贴进答案。
【选型】层次 / 父子或执行流程 → flowchart TD；顺序强 → flowchart LR；状态迁移 → stateDiagram-v2；对象关系 → classDiagram；多角色交互 → sequenceDiagram。
【语法硬规则】
1) 一律写 flowchart，不写 graph；
2) 标签含空格、括号、逗号、冒号、斜杠、加减号、等号时必须写成 A["文本"]，拿不准就加引号；
3) 节点 id 用英文短名（root、left1），不要中文、不要单字母；
4) 标签整词为 end 时必须写 A["end"]；
5) 不能写坐标、位置、颜色，不写 style / classDef / click；
6) 标签内不用 HTML、不写 $...$ 公式，公式放正文；
7) 单个节点文本 ≤16 字，长内容拆成多个节点。
【禁止】不要输出 ASCII 图、不要生成图片文件、不要为凑格式而画图。',
    enabled = 1,
    updated_at = NOW(3)
WHERE name = 'mermaid-diagram';

-- 自检：应为 1 行、enabled = 1、正文 ≤800（skill_get 注入上限）
SELECT COUNT(*) AS skill_rows,
       MAX(enabled) AS enabled,
       MAX(CHAR_LENGTH(content)) AS content_chars
FROM t_skill
WHERE name = 'mermaid-diagram';
