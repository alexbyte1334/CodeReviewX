# CodeReviewX Project Handoff and Roadmap

> 本文档用于承接当前项目状态，并明确下一阶段最优先补全的路线。
> 当前目标定位：作为应届生简历中的 AI agent / AI 应用开发项目，优先展示真实 agent 工程能力，而不是堆叠概念。

## 1. 当前项目定位

CodeReviewX 是一个本地可运行的 AI Code Review Agent MVP。

当前已经具备：

1. Spring Boot 后端。
2. React + TypeScript 前端。
3. H2 本地持久化。
4. ReviewTask 创建、列表、详情查询。
5. ReviewRun / trace / comment preview 的 Stage 2 基础结构。
6. Manual diff review。
7. GitHub PR metadata loader 基础能力。
8. Xiaomi MiMo 双 AI agent review 主链路。
9. MiMo-only provider 策略，无 mock fallback。
10. 结构化 issue 生成和前端展示。

当前最适合的简历描述：

```text
CodeReviewX: 基于 Spring Boot + React 的 AI Code Review Agent。
实现 MiMo 双 AI 协作审查流程：AI-1 负责任务拆解与质量门禁，AI-2 负责执行审查，
通过 IssueGenerator 将获批 JSON 转换为结构化代码问题，并提供本地 comment preview。
```

## 2. 当前已完成的核心能力

### 2.1 MiMo 双 AI agent

当前主流程：

```text
ReviewTask
  -> ReviewRun
  -> AI-1 Planner: TaskPlan JSON
  -> AI-2 Executor: CandidateReview JSON
  -> AI-1 Gatekeeper: GateDecision JSON
  -> MiMoIssueGenerator
  -> ReviewIssue persistence
  -> CommentPreview persistence
```

关键边界：

- `MIMO_PLANNER_API_KEY` 用于 AI-1 Planner + Gatekeeper。
- `MIMO_EXECUTOR_API_KEY` 用于 AI-2 Executor。
- AI-2 不直接落库 issue。
- `MiMoIssueGenerator` 不调用 LLM，只做 deterministic mapping。
- 缺 key、非法 JSON、gate 拒绝都 fail fast。

### 2.2 安全边界

当前约束：

- `docs/mimo_api_key.md` 必须被 `.gitignore` 忽略。
- API key 不进入源码。
- API key 不入库。
- API key 不出 API response。
- raw prompt / raw model output 不出 API response。
- 当前 PR 评论只生成 preview，不写回 GitHub。

### 2.3 测试状态

当前已验证过的测试矩阵：

```text
backend-java:
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
93 tests passed

frontend:
npm test
7 files passed, 50 tests passed

npm run typecheck
passed

npm run build
passed
```

已做过真实 MiMo runtime smoke：

- `GET /api/health` 返回 `reviewProvider=mimo`、`mimoConfigured=true`
- `POST /api/review-tasks` 返回 `SUCCESS`
- `providerUsed=mimo`
- `providerHit=true`
- `issues[].source=MIMO`
- `commentPreviewCount > 0`

## 3. 当前还不是完整生产级 agent 的原因

当前项目已经是 agent MVP，但还不是完整生产级代码审查 agent。

主要缺口：

1. 没有 GitHub PR diff 自动拉取。
2. 没有 repository context loader。
3. 没有 PR 评论发布到 GitHub。
4. 没有 Semgrep / secret scan / dependency scan 等静态分析工具。
5. 没有 agent eval benchmark。
6. 没有可视化 agent step trace 页面。
7. 没有异步任务队列、取消、重试、超时治理。
8. 没有多用户、OAuth、GitHub App、生产级密钥托管。
9. 部分历史文档仍包含 ai-service / Semgrep / mock-era 规划描述，需要后续清理。

## 4. 下一阶段最优先路线

建议按以下顺序推进。前 4 项完成后，项目就很适合放进简历并用于面试展开。

### Priority 1: GitHub PR Diff Loader

目标：

输入 `repoUrl + prNumber` 后，不再要求用户手动粘贴 diff，而是自动通过 GitHub API 拉取 PR diff。

为什么最重要：

- 让项目从“本地 diff review demo”升级为“真实 PR review agent”。
- 面试官很容易理解价值。
- 能复用当前 `GITHUB_PR` metadata loader、run/trace、input snapshot 结构。

建议实现：

1. 扩展 `GithubPrMetadataLoader` 或新增 `GithubPrDiffLoader`。
2. 调 GitHub API 获取 PR diff 或 files patch。
3. 做大小限制：
   - max changed files: 50
   - max diff size: 500KB
   - per-file patch truncate
4. 写入 sanitized `review_input_snapshot`。
5. 进入现有 MiMo 双 AI agent pipeline。
6. 增加错误码：
   - `GITHUB_DIFF_LOAD_FAILED`
   - `GITHUB_DIFF_TOO_LARGE`
   - `GITHUB_DIFF_UNAVAILABLE`

验收：

- 无 `diffText` 创建 `GITHUB_PR` task 时能自动拉 diff。
- 缺 `GITHUB_TOKEN` 仍返回 `GITHUB_AUTH_MISSING`。
- diff 过大时安全失败或截断。
- API 不返回 raw full diff。

### Priority 2: Comment Preview Publish to GitHub

目标：

当前已有本地 comment preview。下一步补“人工选择 -> 发布到 GitHub PR”。

为什么重要：

- Agent 从“分析工具”变成“能执行外部 action 的 agent”。
- 展示 tool use / action safety / human-in-the-loop 能力。

建议实现：

1. 新增 publish endpoint：

```text
POST /api/review-runs/{runId}/comment-previews/{previewId}/publish
POST /api/review-runs/{runId}/comment-previews/publish-selected
```

2. 只允许发布 `selectedForPublish=true` 的 preview。
3. 发布前必须人工确认。
4. 调 GitHub review comment API。
5. 更新 `publishStatus`：

```text
NOT_PUBLISHED -> PUBLISHING -> PUBLISHED | FAILED
```

6. 记录 GitHub comment id，但不记录 token。

验收：

- 用户可在前端选择 preview。
- 点击 publish 后 GitHub PR 出现评论。
- 发布失败有明确错误。
- token 不出 API。

### Priority 3: Agent Trace UI

目标：

前端展示 agent 每一步执行状态：

```text
github.pr.metadata.load
github.pr.diff.load
mimo.ai1.plan
mimo.ai2.execute
mimo.ai1.gate
issue.generate
comment.preview.build
```

为什么重要：

- 体现可观测性和可调试性。
- 面试中很好讲：不是只调模型，而是做可解释 agent workflow。

建议实现：

1. 扩展 `review_tool_trace` 或新增 provider step trace。
2. 每步记录：
   - step name
   - status
   - startedAt / finishedAt / durationMs
   - sanitized input summary
   - sanitized output summary
   - errorCode / errorMessage
3. 前端 ReviewTask detail 增加 timeline。

验收：

- 成功任务显示完整 step timeline。
- 失败任务定位到具体失败 step。
- 不展示 key、raw prompt、raw output。

### Priority 4: Evals Benchmark

目标：

建立一个小型评测集，证明 agent 质量可回归。

为什么重要：

- AI 应用岗位很看重 evals。
- 这会让项目从“能跑”升级到“能衡量质量”。

建议实现：

```text
evals/
  cases/
    case-001-null-pointer.json
    case-002-secret-leak.json
    case-003-sql-injection.json
  expected/
  run-evals.md 或 scripts/run-evals.*
```

指标：

- schema pass rate
- issue count
- severity match
- category match
- expected finding hit rate
- false positive count
- gate rejection count

验收：

- 本地一条命令跑 evals。
- 输出 markdown 或 JSON report。
- README 可展示一次 eval 结果。

### Priority 5: Security Hardening

目标：

把项目安全边界补到能放心开源和演示。

建议补：

1. secret scan 脚本。
2. `.env.example` 完整化。
3. raw prompt/output redaction 测试。
4. prompt injection 输入隔离规则。
5. GitHub token 最小权限文档。
6. 上传 GitHub 前 checklist。

验收：

- 一条命令完成 secret scan。
- 文档明确 token 权限和禁止提交文件。
- API 测试证明敏感字段不返回。

### Priority 6: Async Review Run State Machine

目标：

把同步 review 改成更真实的后台任务。

目标状态：

```text
PENDING
INGESTING
PLANNING
REVIEWING
GATING
BUILDING_PREVIEW
SUCCESS
FAILED
```

建议实现：

1. `POST /api/review-tasks` 快速返回 task/run id。
2. 后台执行 review。
3. 前端轮询 run status。
4. 支持超时和失败恢复。

验收：

- 长耗时 MiMo 调用不阻塞创建接口。
- 前端可显示实时状态。
- 失败状态可定位 step。

## 5. 推荐简历写法

### 简历项目标题

```text
CodeReviewX - AI Code Review Agent
```

### 简历描述版本 A：当前阶段

```text
基于 Spring Boot + React 实现 AI Code Review Agent，支持用户提交 PR 信息与 diff 后触发
MiMo 双 AI 工作流：AI-1 负责任务拆解与质量门禁，AI-2 负责代码审查执行，
后端通过 IssueGenerator 将获批 JSON 转换为结构化 issue，并持久化 review task、
review run、issue summary 与 comment preview。实现了 key 隔离、schema 校验、
fail-fast 错误码、前后端测试与真实 MiMo runtime smoke。
```

### 简历描述版本 B：补完 Priority 1-4 后

```text
基于 Spring Boot + React 构建 GitHub PR Code Review Agent，支持自动拉取 PR diff、
双 AI agent 协作审查、结构化 issue 生成、人工确认后发布 PR 评论、agent step trace
可观测性与 eval benchmark。设计了 planner/executor/gatekeeper 分层、敏感信息脱敏、
失败状态机与回归评测体系，用于提升 AI code review 的可靠性和可调试性。
```

## 6. 面试讲解重点

建议围绕这 5 个点讲：

1. 为什么拆成 AI-1 Planner/Gatekeeper 和 AI-2 Executor。
2. 为什么不让 AI-2 直接落库 issue。
3. `MiMoIssueGenerator` 为什么必须是普通 deterministic code。
4. 如何处理 LLM JSON 不稳定、缺 key、gate 拒绝、网络失败。
5. 如何保证 key、prompt、model output 不泄露。

可以主动说明当前限制：

```text
当前版本是本地 MVP，已完成 MiMo 双 AI review 主链路。
下一步我会补 GitHub PR diff loader、评论发布、agent trace UI 和 evals，
把它从 demo 进一步推进成完整的 PR review agent。
```

这比把未完成能力包装成已完成更可信。

## 7. 上传 GitHub 前建议

上传前建议先完成：

1. 运行 `docs/CURRENT_STAGE_QODER_TEST_PLAN.md` 的 Qoder 验收。
2. 补充 LICENSE。
3. 确认 `.env`、`docs/mimo_api_key.md`、`backend-java/data/`、`frontend/dist/` 不会提交。
4. README 顶部明确当前是 MVP。
5. 删除或标注历史规划文档中尚未实现的 ai-service / Semgrep / RAG 描述。
6. 保留一张架构图或 Mermaid 流程图，方便 GitHub 读者快速理解。

## 8. 推荐下一轮任务文档

建议下一轮新建：

```text
docs/GITHUB_PR_DIFF_LOADER_PLAN.md
```

主题：

```text
Implement GitHub PR diff loader and feed real PR diff into MiMo dual-AI agent.
```

最小成功标准：

1. `GITHUB_PR` mode 能自动拉取 PR diff。
2. diff 写入 sanitized snapshot summary。
3. 大 diff 有截断/失败策略。
4. MiMo 双 AI agent 使用真实 diff review。
5. 测试覆盖 missing token、not found、rate limit、oversized diff、successful diff review。
