# CodeReviewX

面向 Java / Python 等项目的 **AI 辅助代码审查 Agent**。在本地创建审查任务，粘贴 PR 信息与可选 diff，获取结构化的风险等级、问题摘要与修复建议。

> 当前版本为可本地运行的 MVP：支持手动 diff 上下文 + Mock / 小米 MiMo 双 Provider，不包含 GitHub 自动拉取 PR。

---

## 功能特性

- **审查任务管理** — 创建、列表、详情查询，任务与问题持久化到本地 H2 数据库
- **Diff 上下文** — 可选粘贴 unified diff（最大 20,000 字符），为 AI 审查提供代码变更依据
- **双 Provider 模式**
  - **小米 MiMo（默认）** — 通过环境变量 `MIMO_API_KEY` 调用真实模型；Key 缺失时安全降级 Mock
  - **Mock** — 无需 API Key，返回稳定的演示数据
- **Provider 命中反馈** — 每次审查返回 `requestedProvider`、`providerUsed`、`providerHit`（是否命中请求的 Provider）
- **安全降级** — Key 缺失、API 失败或解析失败时自动回退 Mock，不暴露内部错误细节
- **结构化输出** — 每条 issue 含 severity、category、文件路径、行号、标题、描述与建议
- **Web 界面** — React 前端展示审查摘要、风险等级、Provider 来源与 issue 卡片

---

## 技术栈

| 模块 | 技术 |
|---|---|
| 后端 | Spring Boot 3、Java 17、Maven、Spring Data JPA |
| 前端 | React 18、TypeScript、Vite |
| 数据库 | H2（本地文件模式，重启后数据保留） |
| AI Provider | Mock Provider / 小米 MiMo OpenAI 兼容 API |

---

## 快速开始

### 环境要求

- Java 17（macOS 示例：`/opt/homebrew/opt/openjdk@17`）
- Node.js 18+
- Maven 3.8+

### 1. 启动后端

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run
```

默认 Provider 为 **MiMo**。未配置 `MIMO_API_KEY` 时会自动降级为 Mock，并在响应中返回 `providerHit: false`。

服务地址：`http://localhost:8080`

健康检查：

```bash
curl http://localhost:8080/api/health
```

### 2. 启动前端

```bash
cd frontend
npm install
npm run dev -- --host 127.0.0.1
```

浏览器打开 [http://localhost:5173](http://localhost:5173)。

### 3. 启用小米 MiMo（推荐）

```bash
export MIMO_API_KEY="<your-local-key>"   # 仅来自环境变量，切勿提交到仓库

cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run
```

可复制根目录 `.env.example` 为本地 `.env` 参考变量名；`.env` 已被 `.gitignore` 排除，**请勿将真实 Key 写入仓库**。

| 环境变量 | 说明 | 默认值 |
|---|---|---|
| `CODEREVIEWX_REVIEW_PROVIDER` | `mock` 或 `mimo` | `mimo` |
| `MIMO_API_KEY` | 小米 MiMo API Key | — |
| `MIMO_BASE_URL` | API 地址 | `https://api.xiaomimimo.com/v1` |
| `MIMO_MODEL` | 模型名称 | `mimo-v2.5-pro` |
| `MIMO_TIMEOUT_SECONDS` | 请求超时（秒） | `60` |
| `BACKEND_PORT` | 后端端口 | `8080` |

---

## API 概览

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/health` | 健康检查 |
| `POST` | `/api/review-tasks` | 创建审查任务 |
| `GET` | `/api/review-tasks` | 任务列表 |
| `GET` | `/api/review-tasks/{id}` | 任务详情 |

**创建任务请求示例：**

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{
    "repoUrl": "https://github.com/example/repo",
    "prNumber": 42,
    "diffText": "diff --git a/src/App.tsx b/src/App.tsx\n+const x = unsafe();\n"
  }'
```

**响应要点：**

- 包含 `issueSummary`（总数、各级别计数、`riskLevel`）
- 含 `requestedProvider`、`providerUsed`、`providerHit`（Provider 是否命中）
- 每条 `issues[]` 含 `source`（`MOCK` / `MIMO`）、`severity`、`category`、`title` 等
- **不返回** 原始 `diffText`、prompt 或模型原始输出

更多接口细节见 [backend-java/README.md](backend-java/README.md)。

---

## 审查流程

```text
用户提交 repoUrl + prNumber [+ diffText]
        ↓
ReviewPipelineService
        ↓
ConfigurableReviewProvider
   ├─ XiaomiMiMoReviewProvider（默认，需 MIMO_API_KEY）
   └─ MockReviewProvider（显式 mock 或降级回退）
        ↓
结构化 findings → 持久化 → 返回 ReviewTaskResponse
```

---

## 项目结构

```text
CodeReviewX/
├── backend-java/          # Spring Boot 后端
├── frontend/              # React 前端
├── docs/                  # 产品设计、架构与 API 文档
├── .env.example           # 环境变量模板
├── docker-compose.yml
└── .github/workflows/     # CI
```

---

## 运行测试

```bash
# 后端（86 tests）
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test

# 前端（45 tests）
cd frontend
npm run typecheck
npm run build
npm test -- --run
```

---

## 当前限制

以下能力**尚未实现**，请勿在产品中误称已支持：

- GitHub PR 自动拉取 / OAuth / GitHub App
- 仓库 clone 与全量代码分析
- Semgrep 等静态扫描引擎
- PR 评论回写、RAG、MCP、Function Calling
- 生产级认证与团队协作
- MySQL / PostgreSQL 生产数据库

MiMo 在无 diff 时仅依赖 `repoUrl + prNumber` 元数据，审查深度有限；粘贴 diff 可获得更可靠的结果。

---

## 文档

| 文档 | 说明 |
|---|---|
| [backend-java/README.md](backend-java/README.md) | 后端 API、Provider 配置与持久化 |
| [frontend/README.md](frontend/README.md) | 前端开发与测试 |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 系统架构 |
| [docs/PRD.md](docs/PRD.md) | 产品需求 |
| [docs/API.md](docs/API.md) | API 设计（部分为规划文档） |

---

## 许可证

尚未指定开源许可证。如需对外分发，请先补充 LICENSE 文件。
