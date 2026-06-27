# CodeReviewX Architecture Design v1.0

> 本文档承接 `docs/PRD.md`，定义系统架构、模块边界、调用链路、分层设计和编码 Agent 边界规则。

---

## 1. 架构总原则

1. Java 后端只做业务编排和数据持久化。
2. Python ai-service 只做 GitHub 数据获取、静态分析和 LLM 分析。
3. 前端只调用 backend-java，不直接调用 ai-service、GitHub API 或 LLM。
4. MySQL 只作为业务数据存储，不承担分析逻辑。
5. 第一阶段不引入 Redis、消息队列、Kubernetes、向量数据库和复杂权限系统。
6. 所有服务优先保证本地可运行、可调试、可演示。
7. 当前 review agent 锁定 Xiaomi MiMo 双 AI 工作流；缺少 role key 或模型输出非法时 fail fast，不做 mock fallback。

---

## 2. 系统总体架构

```text
+----------------+
|    Frontend    |
| Vue 3 / React  |
+-------+--------+
        |
        | REST API
        v
+----------------------------+
|        backend-java        |
| Spring Boot 3 + Java 17    |
| ReviewTask orchestration   |
+-------+--------------------+
        |
        | HTTP internal API
        v
+----------------------------+
|         ai-service         |
| Python + FastAPI           |
| GitHub diff / Semgrep / LLM|
+---+----------+-------------+
    |          |
    v          v
+-----------+ +-----------+
|GitHub API | | LLM API   |
+-----------+ +-----------+

+----------------------------+
|           MySQL            |
| task / files / issues      |
+----------------------------+
```

---

## 3. 服务职责边界

### 3.1 frontend

**职责：**
- 提供 ReviewTask 创建页面
- 提供任务列表页面
- 提供任务详情页面
- 展示 summary、riskLevel、files 和 issues
- 展示任务状态和失败原因

**禁止：**
- 不得直接调用 ai-service
- 不得直接调用 GitHub API
- 不得保存业务状态
- 不得处理 LLM prompt 或 Semgrep 输出

### 3.2 backend-java

**职责：**
- 对 frontend 提供统一 REST API
- 创建 ReviewTask
- 管理任务状态流转
- 调用 ai-service
- 保存 ReviewFileChange 和 ReviewIssue
- 提供查询接口
- 统一处理业务异常和响应格式

**禁止：**
- 不得执行 Semgrep
- 不得直接编写 LLM prompt
- 不得解析复杂 diff
- 不得绕过 ai-service 调用 LLM

### 3.3 ai-service

**职责：**
- 解析 GitHub repoUrl
- 调用 GitHub API 获取 PR 信息和 diff
- 标准化文件变更
- 执行 Semgrep
- 组织 LLM prompt
- 校验 LLM JSON
- 合并 Semgrep 与 LLM issues
- 返回统一 AnalyzeResponse

**禁止：**
- 不得直接写 MySQL
- 不得管理 ReviewTask 状态
- 不得对 frontend 暴露为公开业务 API
- 不得持有用户会话或权限体系

---

## 4. ReviewTask 状态流

ReviewTask 在生命周期内经过以下状态转换：

```text
PENDING -> RUNNING -> SUCCESS
PENDING -> RUNNING -> FAILED
```

**状态说明：**

| 状态 | 触发时机 |
|---|---|
| `PENDING` | 任务创建成功，尚未执行 |
| `RUNNING` | backend-java 调用 ai-service 前设置 |
| `SUCCESS` | ai-service 成功返回且结果已落库 |
| `FAILED` | 任意关键步骤失败（GitHub 拉取失败、ai-service 超时、数据库写入失败等） |

**规则：**

1. `FAILED` 状态必须同时保存 `error_message` 字段，记录可读错误原因。
2. Semgrep 单独失败不强制导致任务 `FAILED`，可降级为 warning 记录。
3. LLM 失败、缺少 MiMo role key 或 gate 拒绝时将任务置为 `FAILED`，不回退到 mock。
4. 状态只能单向流转，不可回退（如 `SUCCESS` 不可变回 `RUNNING`）。

---

## 5. 核心调用链路

### 4.1 创建并执行任务

```text
1. frontend -> backend-java
   POST /api/review-tasks

2. backend-java
   validate request
   insert review_task status=PENDING
   update status=RUNNING

3. backend-java -> ai-service
   POST /review

4. ai-service
   fetch GitHub PR diff
   normalize changed files
   run Semgrep
   run mock LLM / real LLM
   validate JSON schema
   return AnalyzeResponse

5. backend-java
   save files
   save issues
   update review_task status=SUCCESS

6. frontend -> backend-java
   GET /api/review-tasks/{id}
```

### 4.2 失败链路处理

| 失败场景 | 处理策略 |
|---|---|
| GitHub API 失败 | 任务状态 FAILED，保存 error_message |
| Semgrep 失败 | 降级为 warning，不导致任务失败 |
| LLM 失败 | 任务状态 FAILED，保存安全错误原因 |
| LLM JSON schema 校验失败 | 记录原始输出摘要，不返回未校验结构 |
| backend 数据库保存失败 | 任务状态 FAILED |
| ai-service 超时 | 任务状态 FAILED，保存超时原因 |

---

## 6. backend-java 分层设计

```text
backend-java/src/main/java/com/codereviewx/backend
├── CodeReviewXApplication.java
├── controller
│   └── ReviewTaskController.java
├── service
│   ├── ReviewTaskService.java
│   └── impl
│       └── ReviewTaskServiceImpl.java
├── client
│   └── AiServiceClient.java
├── mapper
│   ├── ReviewTaskMapper.java
│   ├── ReviewFileChangeMapper.java
│   └── ReviewIssueMapper.java
├── entity
│   ├── ReviewTask.java
│   ├── ReviewFileChange.java
│   └── ReviewIssue.java
├── dto
│   ├── request
│   │   └── CreateReviewTaskRequest.java
│   └── response
│       ├── ReviewTaskResponse.java
│       └── ReviewTaskDetailResponse.java
├── enums
│   ├── TaskStatus.java
│   ├── RiskLevel.java
│   ├── IssueType.java
│   └── IssueSeverity.java
├── exception
│   ├── GlobalExceptionHandler.java
│   └── BusinessException.java
└── config
    └── WebClientConfig.java
```

**分层规则：**
- controller 只负责参数接收和响应返回
- service 负责业务流程和事务
- client 负责调用 ai-service
- mapper 只负责数据库访问
- entity 对应数据库表
- dto 对应 API 入参和出参
- enum 集中管理所有状态、类型和严重程度

---

## 7. ai-service 分层设计

```text
ai-service/app
├── main.py
├── api
│   └── review_api.py
├── core
│   └── config.py
├── schemas
│   ├── analyze_request.py
│   └── analyze_response.py
├── services
│   ├── review_analyzer.py
│   ├── github_service.py
│   ├── semgrep_service.py
│   └── llm_service.py
├── prompts
│   └── review_prompt.py
├── validators
│   └── review_json_validator.py
└── utils
    └── repo_parser.py
```

**分层规则：**
- api 层只定义 HTTP endpoint
- services 层实现业务分析流程
- github_service 只负责 GitHub 数据
- semgrep_service 只负责 Semgrep 调用与输出转换
- llm_service 只负责 mock / real LLM
- validators 负责 JSON schema 校验
- schemas 使用 Pydantic 定义请求和响应模型

---

## 8. 数据流设计

### 输入

```json
{
  "repoUrl": "https://github.com/owner/repo",
  "prNumber": 12
}
```

### ai-service 标准输出（AnalyzeResponse）

```json
{
  "summary": "This PR introduces potential risks.",
  "riskLevel": "MEDIUM",
  "files": [
    {
      "filePath": "src/main/java/example/UserService.java",
      "changeType": "modified",
      "additions": 20,
      "deletions": 5,
      "patch": "@@ -1,5 +1,10 @@"
    }
  ],
  "issues": [
    {
      "type": "BUG",
      "severity": "MEDIUM",
      "filePath": "src/main/java/example/UserService.java",
      "line": 42,
      "title": "Potential null pointer exception",
      "description": "The variable may be null before use.",
      "suggestion": "Add a null check before accessing the field.",
      "source": "LLM"
    }
  ]
}
```

---

## 9. 错误响应格式

### backend-java 统一错误响应

```json
{
  "code": "TASK_NOT_FOUND",
  "message": "Review task not found",
  "details": null
}
```

| 错误码 | 场景 |
|---|---|
| INVALID_REQUEST | 请求参数错误 |
| TASK_NOT_FOUND | 任务不存在 |
| AI_SERVICE_ERROR | ai-service 调用失败 |
| GITHUB_FETCH_FAILED | GitHub 数据获取失败 |
| DATABASE_ERROR | 数据库操作失败 |
| INTERNAL_ERROR | 未知系统错误 |

### ai-service 错误响应

```json
{
  "errorCode": "GITHUB_FETCH_FAILED",
  "message": "Failed to fetch pull request diff",
  "recoverable": false
}
```

---

## 10. 配置与环境变量

### backend-java

```env
SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/codereviewx
SPRING_DATASOURCE_USERNAME=codereviewx
SPRING_DATASOURCE_PASSWORD=codereviewx
AI_SERVICE_BASE_URL=http://ai-service:8000
```

### ai-service

```env
GITHUB_TOKEN=
LLM_PROVIDER=mock
LLM_API_KEY=
SEMGREP_TIMEOUT_SECONDS=30
```

### frontend

```env
VITE_API_BASE_URL=http://localhost:8080
```

---

## 11. Docker Compose 部署结构

| 服务 | 端口 |
|---|---|
| frontend | 3000 |
| backend-java | 8080 |
| ai-service | 8000 |
| mysql | 3306 |

---

## 12. Agent 编码边界

### Cursor 适用任务
- 单个 controller / service / DTO 生成
- 单个前端页面
- 小范围 bug 修复
- 单个测试文件补充

### Codex 适用任务
- 初始化仓库结构
- 批量修改包结构
- 跨模块联调
- 运行测试并修复失败
- 引入 Docker Compose 或 CI

### Qoder 适用任务
- 审查架构是否违背 PRD
- 审查模块职责是否越界
- 审查代码质量和依赖风险
- 对比两种实现方案

---

## 13. MVP 阶段不引入复杂架构的理由

| 不引入项 | 原因 |
|---|---|
| Redis | 当前任务量小，数据库状态足够表达任务进度 |
| 消息队列 | 异步复杂度高，MVP 可先同步调用或简单后台线程 |
| Kubernetes | 部署复杂，作品集阶段 Docker Compose 更可演示 |
| 向量数据库 | 当前场景是 PR diff 分析，不需要 RAG |
| 多模型路由 | 增加配置和调试成本，mock + 单模型即可 |
| 用户系统 | 第一阶段聚焦主链路，不需要账号体系 |

---

## 14. Stage 2 架构扩展（Round 16-18）

Stage 2 在 Stage 1.5 基线上增加 run/trace 持久化层，并为 `GITHUB_PR` 增加 bounded agent ingestion：GitHub PR metadata loader 与 GitHub PR diff loader。

```text
review_task (shell)
  └── review_run (execution attempt)
        ├── review_input_snapshot
        ├── review_tool_trace
        ├── review_provider_trace
        ├── review_issue (optional review_run_id)
        └── review_comment_preview (NOT_PUBLISHED / PUBLISHING / PUBLISHED / FAILED)
```

### 14.1 数据库 Migration

- Flyway 管理 schema（`backend-java/src/main/resources/db/migration/`）
- JPA `ddl-auto=validate`，Hibernate 不再自动改表
- 本地 file-backed H2 通过 Flyway baseline 兼容已有数据

### 14.2 新增 Read API

```text
GET /api/review-runs/{runId}
GET /api/review-runs/{runId}/trace
GET /api/review-runs/{runId}/comment-previews
```

Review task 响应增加 `latestRunId`、`reviewMode`、`ingestionSummary`、`traceSummary`、`commentPreviewCount`。
Trace API 返回 sanitized timeline：GitHub ingestion steps、MiMo planner/executor/gatekeeper steps、issue generation、comment preview build。响应只包含步骤名、状态、时间、耗时、安全输出摘要和错误码。

### 14.3 安全与 Redaction

- GitHub token 不入库、不出 API、不写 trace
- API 不返回 raw prompt、raw model output、未截断 diff、`snapshotJson`
- Comment preview 发布仅在人工选择并确认后执行；API 不返回 token 或 Authorization header

### 14.4 GitHub Token 策略

单用户本地 token（`GITHUB_TOKEN`），最低权限：Contents read + Pull requests read/write。无 OAuth / GitHub App / 多租户。

本地配置：

```text
codereviewx.github.api-base-url=${GITHUB_API_BASE_URL:https://api.github.com}
codereviewx.github.token=${GITHUB_TOKEN:}
codereviewx.github.timeout-seconds=${GITHUB_TIMEOUT_SECONDS:20}
codereviewx.github.max-changed-files=${GITHUB_MAX_CHANGED_FILES:50}
codereviewx.github.max-diff-bytes=${GITHUB_MAX_DIFF_BYTES:512000}
codereviewx.github.per-file-patch-max-bytes=${GITHUB_PER_FILE_PATCH_MAX_BYTES:20000}
```

应用启动不要求 token。缺 token 是一次可恢复的 failed run（`GITHUB_AUTH_MISSING`），并会写入 sanitized `github.pr.metadata.load` tool trace。

### 14.5 GITHUB_PR Metadata + Diff Path

```text
POST /api/review-tasks (reviewMode=GITHUB_PR)
  -> create review_task + review_run
  -> run.status=INGESTING
  -> github.pr.metadata.load
  -> persist review_tool_trace
  -> github.pr.diff.load
  -> persist review_tool_trace
  -> on metadata + diff success: persist sanitized review_input_snapshot
  -> run.status=REVIEWING
  -> execute MiMo dual-AI agent pipeline with bounded PR diff context
     -> mimo.ai1.plan
     -> mimo.ai2.execute
     -> mimo.ai1.gate
     -> issue.generate
  -> persist review_issue, review_provider_trace, provider step traces
  -> run.status=BUILDING_PREVIEW
  -> comment.preview.build
  -> persist local review_comment_preview rows
  -> run.status=SUCCESS
```

当前 `GITHUB_PR` MVP 拉取 PR metadata 与 files patch，不读取 repository context，不 clone 仓库；snapshot 只保存 sanitized metadata/diff summary，完整 diff 只作为本次 MiMo pipeline 输入。

### 14.6 Comment Preview Publish Path

```text
GET /api/review-runs/{runId}/comment-previews
  -> frontend renders local draft comments
PATCH /api/review-runs/{runId}/comment-previews/selection
  -> persist selected_for_publish
POST /api/review-runs/{runId}/comment-previews/publish-selected
  -> require confirmed=true
  -> require selected_for_publish=true
  -> publish GitHub PR review comments
  -> persist publish_status, github_comment_id, publish_error_message
```

发布失败不会回滚已保存的 review result；失败 preview 会保存 `FAILED` 和 sanitized 错误信息，供用户修正权限或目标行后重试。

### 14.7 默认边界

- 最多 50 个变更文件
- 总 diff 500KB
- 单文件 patch 20KB 截断
- raw prompt / model output 默认不持久化

仍未实现：repository context loader、RAG、MCP、Memory、OAuth。
