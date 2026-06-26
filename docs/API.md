# CodeReviewX API Design v1.0

> 本文档定义 CodeReviewX MVP 阶段的所有 REST API 接口。
> 包含 frontend -> backend-java 和 backend-java -> ai-service 两套接口。
> 当前状态：Round 01 仅为计划设计，以下 API 均未实现。

---

## 1. 通用规范

### Base URL

| 环境 | backend-java | ai-service |
|---|---|---|
| 本地开发 | `http://localhost:8080` | `http://localhost:8000` |
| Docker Compose | `http://backend-java:8080` | `http://ai-service:8000` |

### 请求格式

- Content-Type: `application/json`
- 字符集: UTF-8

### 统一响应格式（成功）

```json
{
  "data": { ... }
}
```

### 统一错误响应格式

```json
{
  "code": "ERROR_CODE",
  "message": "Human readable error message",
  "details": null
}
```

### 错误码定义

| 错误码 | HTTP 状态 | 场景 |
|---|---|---|
| `INVALID_REQUEST` | 400 | 请求参数错误或校验失败 |
| `TASK_NOT_FOUND` | 404 | 任务不存在 |
| `AI_SERVICE_ERROR` | 502 | ai-service 调用失败 |
| `GITHUB_FETCH_FAILED` | 502 | GitHub 数据获取失败 |
| `DATABASE_ERROR` | 500 | 数据库操作失败 |
| `INTERNAL_ERROR` | 500 | 未知系统错误 |

---

## 2. backend-java API（供 frontend 调用）

### 2.1 创建 Review 任务

```http
POST /api/review-tasks
```

**当前状态：Planned only. Not implemented in Round 01.**

**请求体：**

```json
{
  "repoUrl": "https://github.com/owner/repo",
  "prNumber": 12
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `repoUrl` | string | 是 | GitHub 仓库地址，格式：`https://github.com/{owner}/{repo}` |
| `prNumber` | integer | 是 | Pull Request 编号，必须为正整数 |

**响应（201 Created）：**

```json
{
  "taskId": 1,
  "status": "PENDING"
}
```

**错误响应示例：**

```json
{
  "code": "INVALID_REQUEST",
  "message": "repoUrl must be a valid GitHub URL",
  "details": null
}
```

---

### 2.2 查询任务列表

```http
GET /api/review-tasks
```

**当前状态：Planned only. Not implemented in Round 01.**

**查询参数（可选）：**

| 参数 | 类型 | 说明 |
|---|---|---|
| `page` | integer | 页码，从 0 开始，默认 0 |
| `size` | integer | 每页数量，默认 20 |

**响应（200 OK）：**

```json
{
  "items": [
    {
      "taskId": 1,
      "repoUrl": "https://github.com/owner/repo",
      "prNumber": 12,
      "status": "SUCCESS",
      "riskLevel": "MEDIUM",
      "createdAt": "2026-06-19T10:00:00"
    }
  ],
  "total": 1
}
```

**items 字段说明：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `taskId` | long | 任务 ID |
| `repoUrl` | string | GitHub 仓库地址 |
| `prNumber` | integer | PR 编号 |
| `status` | string | `PENDING` / `RUNNING` / `SUCCESS` / `FAILED` |
| `riskLevel` | string | `LOW` / `MEDIUM` / `HIGH` / null（未完成时） |
| `createdAt` | string | ISO 8601 格式时间 |

---

### 2.3 查询任务详情

```http
GET /api/review-tasks/{id}
```

**当前状态：Planned only. Not implemented in Round 01.**

**路径参数：**

| 参数 | 类型 | 说明 |
|---|---|---|
| `id` | long | 任务 ID |

**响应（200 OK）：**

```json
{
  "taskId": 1,
  "repoUrl": "https://github.com/owner/repo",
  "prNumber": 12,
  "status": "SUCCESS",
  "summary": "This PR has several medium risk issues.",
  "riskLevel": "MEDIUM",
  "errorMessage": null,
  "createdAt": "2026-06-19T10:00:00",
  "updatedAt": "2026-06-19T10:01:30",
  "files": [
    {
      "filePath": "src/main/java/example/UserService.java",
      "changeType": "modified",
      "additions": 20,
      "deletions": 5
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

**响应字段说明：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `taskId` | long | 任务 ID |
| `repoUrl` | string | GitHub 仓库地址 |
| `prNumber` | integer | PR 编号 |
| `status` | string | 任务状态 |
| `summary` | string | Review 总结（任务成功后填充） |
| `riskLevel` | string | 风险等级（任务成功后填充） |
| `errorMessage` | string | 失败原因（FAILED 状态时填充） |
| `files` | array | 变更文件列表 |
| `issues` | array | Review 问题列表 |

**files 项字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `filePath` | string | 文件路径 |
| `changeType` | string | `added` / `modified` / `deleted` |
| `additions` | integer | 新增行数 |
| `deletions` | integer | 删除行数 |

**issues 项字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `type` | string | `BUG` / `SECURITY` / `PERFORMANCE` / `TEST` / `STYLE` |
| `severity` | string | `LOW` / `MEDIUM` / `HIGH` |
| `filePath` | string | 问题所在文件路径 |
| `line` | integer | 问题行号 |
| `title` | string | 问题标题 |
| `description` | string | 问题描述 |
| `suggestion` | string | 修复建议 |
| `source` | string | `LLM` / `SEMGREP` |

**错误响应（任务不存在）：**

```json
{
  "code": "TASK_NOT_FOUND",
  "message": "Review task with id 999 not found",
  "details": null
}
```

| `SEMGREP` | 来自 Semgrep 静态分析 |

---

## 5. Stage 2 Read API（Round 16）

### 5.1 Review Task 响应扩展字段

`GET /api/review-tasks` 与 `GET /api/review-tasks/{id}` 在原有字段基础上增加（均为向后兼容 additive）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `latestRunId` | long / null | 最近一次 run ID，无 run 时为 null |
| `reviewMode` | string | `MANUAL_DIFF` 或 `GITHUB_PR` |
| `errorCode` | string / null | 失败时来自 latest run 的错误码（如 `GITHUB_AUTH_MISSING`） |
| `ingestionSummary` | object / null | 来自 latest run 输入快照的摘要 |
| `traceSummary` | object / null | tool trace 计数摘要 |
| `commentPreviewCount` | integer | 评论预览数量，无 run 时为 0 |

`POST /api/review-tasks` 可选请求字段 `reviewMode`（`MANUAL_DIFF` | `GITHUB_PR`）。解析顺序：显式 `reviewMode` > 有 `diffText` → `MANUAL_DIFF` > 否则 `GITHUB_PR`。显式 `MANUAL_DIFF` 且无有效 `diffText` 时返回 400。

每次 create 都会创建 `review_run`（run number 1），`latestRunId` 指向该 run。

- **MANUAL_DIFF**：执行 MiMo 双 AI agent pipeline，issues 关联 `review_run_id`，任务与 run 均为 `SUCCESS`（需有 `diffText` 或显式模式且带 diff）。
- **GITHUB_PR**：先解析本地 GitHub token 配置并执行 bounded metadata loader（`github.pr.metadata.load`）。缺少 `GITHUB_TOKEN` 时任务与 run 均为 `FAILED`，`errorCode=GITHUB_AUTH_MISSING`，无 input snapshot、无 issues。metadata 成功时写入 sanitized `review_input_snapshot`，随后执行 MiMo 双 AI agent pipeline，保存 issues、provider trace 和本地 comment previews。当前仍不拉取 PR diff，也不写回 GitHub。

无 `diffText` 的 create（默认 `GITHUB_PR`）仅在 GitHub metadata loader 成功后返回 `SUCCESS` 与 provider findings；缺少 token 或 metadata loader 失败时返回 failed run。

API 永不返回：`diffText`、GitHub token、Authorization header、raw prompt、raw model output、未截断的 Stage 2 snapshot diff。

### 5.2 Get Review Run

```http
GET /api/review-runs/{runId}
```

返回 run 状态、bounded input snapshot summary、provider summary。不返回 `snapshotJson` 或 raw prompt/output。

### 5.3 Get Tool Trace

```http
GET /api/review-runs/{runId}/trace
```

返回 tool 时间线摘要（`toolName`、`status`、`outputSummary` 等）。不返回 `inputSummary`（可能含敏感信息）。

### 5.4 Get Comment Previews

```http
GET /api/review-runs/{runId}/comment-previews
```

返回草稿评论列表。`publishStatus` 当前仅 `NOT_PUBLISHED`。无 GitHub 写回 endpoint。

### 5.5 GitHub Token 与 Metadata Loader（Round 18）

本地配置：

```yaml
codereviewx:
  github:
    api-base-url: ${GITHUB_API_BASE_URL:https://api.github.com}
    token: ${GITHUB_TOKEN:}
    timeout-seconds: ${GITHUB_TIMEOUT_SECONDS:20}
```

Token 是可选本地配置；应用启动不要求 `GITHUB_TOKEN`。token 值和 Authorization header 不入库、不出 API、不写 tool trace。

`GITHUB_PR` 当前只加载 PR metadata，并用该 bounded context 启动 MiMo 双 AI agent review：

```text
owner, repo, prNumber, title, author login, base/head refs, base/head shas,
state, createdAt, updatedAt, changedFiles, additions, deletions
```

错误码：

| 错误码 | 语义 |
|---|---|
| `GITHUB_AUTH_MISSING` | 本地未配置 `GITHUB_TOKEN` |
| `GITHUB_AUTH_FAILED` | GitHub 返回 401/403 且非 rate limit |
| `GITHUB_PR_NOT_FOUND` | PR 不存在或不可访问 |
| `GITHUB_RATE_LIMITED` | GitHub API rate limit |
| `GITHUB_METADATA_LOAD_FAILED` | 其他 metadata loader 安全失败 |

### 5.6 GitHub Token 最低权限（Stage 2 规划）

私有仓库 review 所需最低 GitHub token 权限：

```text
repo read（或 public_repo）
pull request read
```

Token 仅存于本地环境变量（如 `GITHUB_TOKEN`），不入库、不出 API。

### 5.7 默认 diff 边界

Stage 2 规划默认：最多 50 个变更文件，总 diff 500KB。raw prompt / model output 默认不持久化。

---

## 6. ai-service API（供 backend-java 调用）

> ai-service 的接口仅供 backend-java 内部调用，不对 frontend 暴露。

### 3.1 执行 PR 分析

```http
POST /review
```

**当前状态：Planned only. Not implemented in Round 01.**

**请求体：**

```json
{
  "repoUrl": "https://github.com/owner/repo",
  "prNumber": 12
}
```

**响应（200 OK）：**

```json
{
  "summary": "This PR introduces potential risks in user authentication logic.",
  "riskLevel": "MEDIUM",
  "files": [
    {
      "filePath": "src/main/java/example/UserService.java",
      "changeType": "modified",
      "additions": 20,
      "deletions": 5,
      "patch": "@@ -1,5 +1,10 @@\n-old line\n+new line"
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
    },
    {
      "type": "SECURITY",
      "severity": "HIGH",
      "filePath": "src/main/java/example/AuthController.java",
      "line": 15,
      "title": "Hardcoded secret detected",
      "description": "A hardcoded token was found in the source code.",
      "suggestion": "Move this value to environment variables.",
      "source": "SEMGREP"
    }
  ]
}
```

**响应字段说明：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `summary` | string | Review 总结 |
| `riskLevel` | string | `LOW` / `MEDIUM` / `HIGH` |
| `files` | array | PR 变更文件列表（含 patch） |
| `issues` | array | 所有 Review 问题（合并 LLM + Semgrep） |

**错误响应（GitHub 拉取失败）：**

```json
{
  "errorCode": "GITHUB_FETCH_FAILED",
  "message": "Failed to fetch pull request: Repository not found or no access",
  "recoverable": false
}
```

**ai-service 错误码：**

| 错误码 | 场景 |
|---|---|
| `GITHUB_FETCH_FAILED` | GitHub API 请求失败 |
| `PR_NOT_FOUND` | PR 不存在 |
| `SEMGREP_FAILED` | Semgrep 执行失败（通常降级处理） |
| `LLM_FAILED` | LLM 调用失败（通常降级为 mock） |
| `INVALID_REQUEST` | 请求参数错误 |

---

## 4. 枚举值定义

### TaskStatus

| 值 | 含义 |
|---|---|
| `PENDING` | 任务已创建，尚未执行 |
| `RUNNING` | 任务执行中 |
| `SUCCESS` | 任务执行成功 |
| `FAILED` | 任务执行失败 |

### RiskLevel

| 值 | 含义 |
|---|---|
| `LOW` | 低风险 |
| `MEDIUM` | 中风险 |
| `HIGH` | 高风险 |

### IssueType

| 值 | 含义 |
|---|---|
| `BUG` | 潜在 Bug |
| `SECURITY` | 安全风险 |
| `PERFORMANCE` | 性能问题 |
| `TEST` | 测试缺失 |
| `STYLE` | 代码风格 |

### IssueSeverity

| 值 | 含义 |
|---|---|
| `LOW` | 低严重程度 |
| `MEDIUM` | 中严重程度 |
| `HIGH` | 高严重程度 |

### IssueSource

| 值 | 含义 |
|---|---|
| `LLM` | 来自 LLM 分析 |
| `SEMGREP` | 来自 Semgrep 静态分析 |
