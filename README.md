# StudentHelper 大学生解题助手

上传题目（文字 / 图片），模型按「解题 / 追问 / 讲解」三种形态作答，并围绕学习过程沉淀知识点、错题与学情数据。

## 功能概览

- **三态回答**：解题走六段结构（题意、思路、步骤、答案、易错、扩展），追问走自由问答，讲解走概念讲解；由模型先判意图，再由统一决策点决定形态与工具。
- **图片题目**：支持多图上传，按「最近 N 条带图消息」的窗口带入上下文，未入选图片改写为文字占位。
- **作图画解**：题目涉及树 / 图 / 状态迁移时，后端把 Mermaid 渲染成 PNG 落到本地，答案中只出现图片链接。
- **学科规范**：按学科注入「解题 / 追问 / 讲解」三段规范，可在界面维护并导入文件夹。
- **技能**：清单常驻（≤12 条），模型按需读取技能正文；会话可固化技能，从下一轮起逐轮注入。
- **长期记忆**：用户画像 + 事实记忆，超阈值自动压缩为摘要；支持整理、归档与清空。
- **知识点 / 错题本**：自动抽取知识点（含命中次数），错题可标记与回顾。
- **学情统计 / 用量看板**：按自然日窗口统计答题量与正确率；用量含 token、耗时与 Prompt 缓存命中率。
- **流式与过程可见**：SSE 流式输出、可中途停止、过程面板展示工具调用与耗时。

## 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Spring Boot 4.0.9 / Java 21 / Spring Data JDBC / AgentScope 2.0.4 |
| 数据库 | MySQL 8.0+（utf8mb4） |
| 前端 | React 19 + Vite 8 + TypeScript |
| 作图 | 本机 Chrome / Edge 无头渲染 Mermaid（本地 `mermaid.min.js`） |

## 环境要求

- JDK 21
- MySQL 8.0+
- Node.js 20+ 与 npm
- Chrome 或 Edge（仅作图功能需要，可用 `STUDENTHELPER_CHROME_PATH` 指定）
- Maven 3.9+（仓库自带 `mvnw`，首次使用需联网下载 wrapper）

## 快速开始

### 1. 建库

```bash
mysql -h 127.0.0.1 -P 3306 -u root -p \
  -e "CREATE DATABASE student_helper DEFAULT CHARACTER SET utf8mb4;"
```

### 2. 初始化表与基础数据

```bash
mysql -h 127.0.0.1 -P 3306 -u root -p --default-character-set=utf8mb4 \
      student_helper < src/main/resources/db/init.sql
```

`init.sql` 建 20 张表，并写入**能力类**种子数据：2 条技能、4 条学科规范、12 项功能开关。

它**不包含**模型配置与 API Key，也**不包含**任何聊天记录、知识点、记忆等个人数据 —— 这些表只建空结构。

> 脚本会 `DROP` 并重建同名表，仅用于空库初始化。已有数据的库不要执行，那种情况应按序执行 `upgrade-s*.sql` 增量脚本。

### 3. 生成主密钥

主密钥用于加密数据库中保存的模型 API Key（AES-256-GCM），**缺失会直接启动失败**，且每套部署各自生成：

```bash
mkdir -p data
openssl rand -base64 32 > data/.master_key
```

也可以改为设置环境变量 `STUDENTHELPER_MASTER_KEY`（内容为 32 字节的 Base64，44 字符），环境变量优先于文件。

`data/.master_key` 已被 `.gitignore` 排除，**不要提交**。

### 4. 启动后端

```bash
DB_PASSWORD=你的数据库密码 mvn -B package -DskipTests
DB_PASSWORD=你的数据库密码 java -jar target/StudentHelper-0.0.1-SNAPSHOT.jar --server.port=8080
```

启动后访问 `http://127.0.0.1:8080/api/features` 应返回 12 项功能开关。

### 5. 启动前端

```bash
cd frontend
npm install
npm run dev
```

打开 `http://localhost:5173`，开发服务器已把 `/api` 代理到 `127.0.0.1:8080`。

### 6. 配置模型（必须）

新库中模型表为空，需要在界面「基础设置 → 模型配置」添加：

1. 新增厂商（OpenAI 兼容网关，填 Base URL）；
2. 新增模型并填写 API Key（加密后入库）；
3. 把要用的模型设为当前。

未配置模型时应用可以启动，但发起对话会失败。

## 目录结构

```
src/main/java/com/ai/studenthelper/
  agent/        模型调用、意图分类、Prompt 模板、工具（技能 / 作图 / 记忆）
  application/  用例层：对话装配、上下文与压缩、学科规范、统计与用量
  controller/   REST 接口与 SSE 流
  infra/        持久化、图片存储、Mermaid 渲染、记忆文件、调用日志
  common/       DTO、章节结构、分词估算等
src/main/resources/
  application.yaml     配置（数据源 / SSE 超时 / 图片与作图目录）
  db/init.sql          全新环境初始化脚本
  db/upgrade-s*.sql    历史增量升级脚本（老库使用）
  diagram/mermaid.min.js  本地作图渲染资源
frontend/src/           React 界面（视图表驱动侧栏、对话页、各管理面板）
```

## 常用命令

| 用途 | 命令 |
|---|---|
| 后端测试 | `DB_PASSWORD=yourpw mvn -B test` |
| 后端打包 | `DB_PASSWORD=yourpw mvn -B package` |
| 前端类型检查 | `cd frontend && npm run typecheck` |
| 前端构建 | `cd frontend && npm run build` |
| 前端断言脚本 | `cd frontend && npm run check` |

## 环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `DB_USERNAME` | `root` | 数据库用户名 |
| `DB_PASSWORD` | 空 | 数据库密码（必填） |
| `STUDENTHELPER_MASTER_KEY` | 空 | 主密钥（优先于 `data/.master_key`） |
| `STUDENTHELPER_CHROME_PATH` | 空 | Chrome / Edge 可执行文件路径，留空则自动探测 |

数据目录默认在 `./data`：`images/` 存题目与作图图片，`diagram/` 存渲染中间文件，`.master_key` 为主密钥，均不入库。

## 说明

- **模型配置与聊天记录不会随仓库分发**：API Key 由主密钥加密后只存在你本机数据库里；`data/` 与三份开发文档（需求 / 技术 / 任务）都在 `.gitignore` 中，仅本地保留。
- 新库与老库的初始化路径不同：空库跑 `init.sql` 一次到位；已有数据的库按
  `schema.sql` → `upgrade-s2` → `upgrade-s2.1` → `upgrade-s3` → `upgrade-s4` → `upgrade-s4.1` → `upgrade-s5` … `upgrade-s17`
  的顺序执行增量脚本（均为幂等脚本，可重复执行）。
- 作图依赖本机浏览器内核，未安装 Chrome / Edge 时作图工具自动不可用，其余功能不受影响。
