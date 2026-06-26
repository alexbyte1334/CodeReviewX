# Current Stage Qoder Test Plan

> 目的：交给 Qoder 对当前 MiMo 双 AI agent 阶段做只读验收，判断是否达到可上传 GitHub 的最低标准。
> 本文档只用于测试与审查，不要求 Qoder 修改代码。

## 1. 测试结论目标

Qoder 最终需要给出一个明确结论：

```text
Decision: READY_TO_UPLOAD | READY_WITH_NOTES | NOT_READY
```

判定标准：

- `READY_TO_UPLOAD`：测试全部通过，无密钥泄露、无 mock 主链路、无明显文档误导。
- `READY_WITH_NOTES`：核心链路可上传，但存在不阻塞开源的文档/体验小问题。
- `NOT_READY`：存在密钥泄露、测试失败、真实主链路不可用、或上传后会误导读者的严重问题。

## 2. 安全规则

Qoder 必须遵守：

1. 不要读取、复制、打印或总结 `docs/mimo_api_key.md` 的具体内容。
2. 只能确认该文件是否被 `.gitignore` 忽略。
3. 不要把任何 API key、token、Authorization header 写入报告。
4. 不要修改文件；只读审查即可。
5. 如果需要 runtime smoke，由项目所有者在本地提供环境变量，Qoder 不接触真实 key。

## 3. 当前阶段预期能力

当前项目应满足：

1. Review 主链路为 MiMo-only。
2. 不存在可配置切换到 mock 的 review 主链路。
3. `MIMO_PLANNER_API_KEY` 驱动 AI-1 Planner + Gatekeeper。
4. `MIMO_EXECUTOR_API_KEY` 驱动 AI-2 Executor。
5. ReviewTask 创建后流程为：

```text
ReviewTask
  -> ReviewRun
  -> AI-1 TaskPlan
  -> AI-2 CandidateReview
  -> AI-1 GateDecision
  -> MiMoIssueGenerator
  -> ReviewIssue persistence
  -> CommentPreview persistence
```

6. AI-2 不能直接落库 issue。
7. 只有 AI-1 gatekeeper 批准后的 `CandidateReview` 才能进入 `MiMoIssueGenerator`。
8. 缺少任一 role key 时 fail fast，错误码为 `MIMO_AUTH_MISSING`。
9. 非法 JSON / gate 拒绝 / MiMo 网络失败必须有明确失败路径，不 fallback 到 mock。
10. API response、tool trace、provider trace 不返回 raw prompt、raw model output、API key。

## 4. 必跑命令

### 4.1 Git 与密钥检查

在 repo root 执行：

```bash
git status --short
git check-ignore -v docs/mimo_api_key.md
git diff --check
```

预期：

- `docs/mimo_api_key.md` 必须显示被 `.gitignore` 命中。
- `git diff --check` 无输出。
- 如果 `docs/mimo_api_key.md` 出现在可提交文件中，结论必须是 `NOT_READY`。

### 4.2 敏感信息扫描

```bash
rg -n "MIMO_API_KEY|MIMO_PLANNER_API_KEY|MIMO_EXECUTOR_API_KEY|Authorization|Bearer|api[_-]?key|secret|token" \
  README.md .env.example docs backend-java/src frontend/src \
  -g '!docs/mimo_api_key.md'
```

预期：

- 允许出现环境变量名、占位符、文档说明。
- 不允许出现真实 key、Bearer token、完整 Authorization header。
- 不允许 raw prompt/raw model output 被描述为 API 返回字段。

### 4.3 Mock 主链路扫描

```bash
rg -n "MockReviewProvider|mock fallback|fallback to mock|provider=mock|CODEREVIEWX_REVIEW_PROVIDER|IssueSource\\.MOCK" \
  backend-java/src/main backend-java/src/test frontend/src README.md docs \
  -g '!docs/mimo_api_key.md'
```

预期：

- 不应存在 `MockReviewProvider` 生产主链路。
- 不应存在 `CODEREVIEWX_REVIEW_PROVIDER`。
- `IssueSource.MOCK` 如仍存在，只能用于历史数据兼容，不得作为新任务主链路。
- 文档中可以说明“已移除 mock fallback”，但不能宣传当前仍支持 mock fallback。

### 4.4 后端测试

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
```

预期：

```text
Tests run: 93, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

重点关注测试覆盖：

- 缺 role key -> `MIMO_AUTH_MISSING`
- AI-1 planner JSON 非法 -> `MIMO_PLAN_INVALID`
- AI-2 candidate JSON 非法 -> `MIMO_REVIEW_INVALID`
- AI-1 gate JSON 非法 -> `MIMO_GATE_INVALID`
- AI-1 gate 拒绝 -> `MIMO_GATE_REJECTED`
- provider 网络失败 -> `MIMO_PROVIDER_ERROR`
- `MiMoIssueGenerator` 生成 `MIMO-ISSUE-*`
- persisted issue source 为 `MIMO`

### 4.5 前端测试

```bash
cd frontend
npm test
npm run typecheck
npm run build
```

预期：

- `npm test`：7 files passed，50 tests passed。
- `npm run typecheck`：通过。
- `npm run build`：通过。

重点关注：

- 前端不再提供 Mock provider 选择。
- 创建任务固定提交 `provider: "mimo"` 或不暴露 provider 切换。
- 历史 `MOCK` 只以 historical label 显示，不推荐 fallback。
- health widget 使用 MiMo role key 配置状态。

## 5. Runtime Smoke 可选项

如果项目所有者已经在 shell 中配置：

```bash
export MIMO_PLANNER_API_KEY="..."
export MIMO_EXECUTOR_API_KEY="..."
```

Qoder 可要求项目所有者执行隔离 smoke。不要让 Qoder 接触真实 key。

### 5.1 启动后端

建议使用隔离端口和临时 H2：

```bash
cd backend-java
BACKEND_PORT=18081 \
SPRING_DATASOURCE_URL="jdbc:h2:file:/private/tmp/codereviewx-qoder-smoke;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE" \
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
mvn spring-boot:run
```

### 5.2 健康检查

```bash
curl -s http://127.0.0.1:18081/api/health
```

预期：

```json
{
  "success": true,
  "data": {
    "status": "UP",
    "service": "backend-java",
    "reviewProvider": "mimo",
    "mimoConfigured": true
  }
}
```

### 5.3 创建 Manual Diff ReviewTask

```bash
curl -s -X POST http://127.0.0.1:18081/api/review-tasks \
  -H 'Content-Type: application/json' \
  -d '{
    "repoUrl": "https://github.com/example/repo",
    "prNumber": 77,
    "diffText": "diff --git a/src/main/java/example/UserService.java b/src/main/java/example/UserService.java\n@@ -1,3 +1,6 @@\n+String token = request.getParameter(\"token\");\n+System.out.println(token);\n+return userRepository.findById(id).get();\n"
  }'
```

预期：

- `status=SUCCESS`
- `providerUsed=mimo`
- `providerHit=true`
- `issues[].source=MIMO`
- `issues[].id` 形如 `MIMO-ISSUE-1`
- `commentPreviewCount > 0`
- response 不包含 key、Authorization、raw prompt、raw model output

### 5.4 Run API 检查

```bash
curl -s http://127.0.0.1:18081/api/review-runs/1
curl -s http://127.0.0.1:18081/api/review-runs/1/trace
curl -s http://127.0.0.1:18081/api/review-runs/1/comment-previews
```

预期：

- run status 为 `SUCCESS`
- provider summary 为 `mimo`
- trace 不暴露 raw prompt / raw model output
- comment previews 为 `NOT_PUBLISHED`

## 6. 代码审查重点

请重点审查这些文件：

```text
backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/mimo/XiaomiMiMoReviewProvider.java
backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/mimo/MiMoIssueGenerator.java
backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/mimo/MiMoAgentJsonParser.java
backend-java/src/main/java/com/codereviewx/backend/review/service/ReviewTaskService.java
backend-java/src/main/java/com/codereviewx/backend/review/dto/CreateReviewTaskRequest.java
backend-java/src/main/resources/application.yml
frontend/src/components/ReviewTaskCreateForm.tsx
frontend/src/components/StatusWidget.tsx
```

审查问题：

1. AI-2 是否可能绕过 gatekeeper 直接写 issue？
2. `MiMoIssueGenerator` 是否只接收获批后的 candidate review？
3. 是否还有 mock provider 可达？
4. 是否还有 provider switch 可配置到 mock？
5. 缺 key / 非法 JSON / gate 拒绝是否都能持久化为 failed task/run？
6. API 是否可能返回 raw prompt、raw output、key 或 Authorization？
7. 测试是否覆盖了新增失败路径？
8. 文档是否真实描述当前能力，没有夸大 GitHub PR diff 自动拉取、PR 评论回写、Semgrep、RAG、MCP？

## 7. GitHub 上传前检查

上传 GitHub 前必须确认：

1. `docs/mimo_api_key.md` 未跟踪且被忽略。
2. `.env`、`.env.*` 未跟踪。
3. `backend-java/data/` 未跟踪。
4. `frontend/dist/` 未跟踪，除非项目决定发布静态产物。
5. 没有真实 token/key/Authorization header。
6. README 明确说明当前是 MVP，不包含 GitHub PR diff 自动拉取和 PR 评论回写。
7. 测试通过。
8. LICENSE 如要开源，需要补充。

## 8. Qoder 输出模板

请按以下格式输出：

```markdown
# Qoder Review Result

Decision: READY_TO_UPLOAD | READY_WITH_NOTES | NOT_READY

## Evidence

- Backend tests:
- Frontend tests:
- Security scan:
- Git ignore check:
- Runtime smoke:

## Findings

1. [P0/P1/P2] ...

## Upload Readiness

- Secrets safe:
- Mock path removed:
- MiMo dual-agent flow verified:
- Docs accurate:

## Required Fixes Before GitHub Upload

- ...

## Optional Follow-ups

- ...
```
