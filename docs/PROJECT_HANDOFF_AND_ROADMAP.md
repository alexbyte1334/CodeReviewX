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
5. ReviewRun / agent trace / comment preview 的 Stage 2 基础结构与前端可视化。
6. Manual diff review。
7. GitHub PR metadata + diff loader 基础能力。
8. Xiaomi MiMo 双 AI agent review 主链路。
9. MiMo-only provider 策略，无 mock fallback。
10. 结构化 issue 生成和前端展示。
11. 离线 eval benchmark。
12. Secret scan / Semgrep / dependency hygiene scan 静态分析基础工具链。

当前最适合的简历描述：

```text
CodeReviewX: 基于 Spring Boot + React 的 AI Code Review Agent。
实现 MiMo 双 AI 协作审查流程：AI-1 负责任务拆解与质量门禁，AI-2 负责执行审查，
通过 IssueGenerator 将获批 JSON 转换为结构化代码问题，提供 GitHub PR diff 自动拉取、
本地 comment preview、人工确认后发布评论、agent trace 可观测性与 eval/static-scan 验收工具。
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
- PR 评论发布必须由用户选择 comment preview 并确认后执行，token 不出 API。

### 2.3 测试状态

当前已验证过的测试矩阵：

```text
backend-java:
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
116 tests passed

frontend:
npm test
7 files passed, 57 tests passed

npm run typecheck
passed

npm run build
passed

evals / static analysis:
node scripts/run-evals.mjs
schema pass rate 100%, expected finding hit rate 100%

node scripts/static-scan.mjs
passed locally; Semgrep runs when installed and is enforced in CI
```

已做过真实 MiMo runtime smoke：

- `GET /api/health` 返回 `reviewProvider=mimo`、`mimoConfigured=true`
- `POST /api/review-tasks` 返回 `SUCCESS`
- `providerUsed=mimo`
- `providerHit=true`
- `issues[].source=MIMO`
- `commentPreviewCount > 0`

## 3. 当前项目边界

当前项目定位为单人、可本地运行、适合简历展示的 AI Code Review Agent MVP。
它不包装成完整生产级 SaaS，也不把以下能力作为当前阶段缺口处理：

- 异步任务队列、取消、重试、超时治理。
- 多用户、OAuth、GitHub App、生产级密钥托管。

当前剩余的合理增强方向：

1. repository context loader：让 agent 看更多仓库上下文，而不只看 bounded PR diff。
2. live eval capture：把真实 MiMo/backend 输出自动保存到 `evals/actual/` 后跑回归。
3. 更完整的静态分析：扩展 Semgrep 规则、接入在线 CVE 数据库、Dependabot 或 OWASP Dependency-Check。
4. 历史阶段 handoff / 测试计划 / 协作过程文档已经清理，当前公开文档以 README、PRD、Architecture、API、Database、Security、Static Analysis 和项目汇总为准。

## 4. 下一阶段最优先路线

建议按以下顺序推进。Priority 1-5 已完成基础版后，项目已经适合放进简历并交给用户做最终本地验收。

### Priority 1: GitHub PR Diff Loader（已完成基础版）

目标：

输入 `repoUrl + prNumber` 后，不再要求用户手动粘贴 diff，而是自动通过 GitHub API 拉取 PR diff。

当前实现状态：

- `github.pr.metadata.load` 成功后继续执行 `github.pr.diff.load`。
- 通过 GitHub PR files API 拉取文本 patch。
- 默认限制：max changed files 50、max diff size 500KB、per-file patch 20KB 截断。
- `review_input_snapshot` 只保存 sanitized summary，不保存 raw patch。
- MiMo 双 AI agent 使用自动拉取的 bounded diff。
- 已覆盖 `GITHUB_AUTH_MISSING`、状态码映射、too large、diff unavailable、截断和 successful diff review 测试。

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

### Priority 2: Comment Preview Publish to GitHub（已完成基础版）

目标：

当前已有本地 comment preview。下一步补“人工选择 -> 发布到 GitHub PR”。

当前实现状态：

- 新增 selection endpoint：`PATCH /api/review-runs/{runId}/comment-previews/selection`。
- 新增 publish endpoint：
  - `POST /api/review-runs/{runId}/comment-previews/{previewId}/publish`
  - `POST /api/review-runs/{runId}/comment-previews/publish-selected`
- 只允许发布 `selectedForPublish=true` 的 preview。
- 请求必须 `confirmed=true`。
- 调 GitHub PR review comment API。
- 持久化 `publishStatus`、`githubCommentId`、`publishErrorMessage`、`publishedAt`。
- 前端支持选择 preview、发布已选择项并展示成功/失败状态。
- token 和 Authorization header 不出 API。

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

### Priority 3: Agent Trace UI（已完成基础版）

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

当前实现状态：

- MiMo provider 在运行时记录 `mimo.ai1.plan`、`mimo.ai2.execute`、`mimo.ai1.gate`、`issue.generate`。
- `ReviewTaskService` 把 provider step 与 `comment.preview.build` 持久化到 `review_tool_trace`。
- `GET /api/review-runs/{runId}/trace` 返回 `toolName`、`status`、`startedAt`、`finishedAt`、`durationMs`、`outputSummary`、`errorCode`。
- 前端 ReviewTask detail 增加 Agent Trace timeline 面板。
- 失败路径会停在具体失败 step，并显示安全摘要与错误码。
- API/UI 不返回 `inputSummary`、raw prompt、raw model output、raw full diff、token 或 Authorization header。

为什么重要：

- 体现可观测性和可调试性。
- 面试中很好讲：不是只调模型，而是做可解释 agent workflow。

建议实现：

1. 扩展 `review_tool_trace` 或新增 provider step trace。
2. 每步记录：
   - step name
   - status
   - startedAt / finishedAt / durationMs
   - internal sanitized input summary（public API/UI 不返回）
   - sanitized output summary
   - errorCode（public API/UI 不返回内部 errorMessage）
3. 前端 ReviewTask detail 增加 timeline。

验收：

- 成功任务显示完整 step timeline。
- 失败任务定位到具体失败 step。
- 不展示 key、raw prompt、raw output。

### Priority 4: Evals Benchmark（已完成基础版）

目标：

建立一个小型评测集，证明 agent 质量可回归。

当前实现状态：

- 新增 `evals/cases/`，覆盖 null pointer、secret-like config、SQL injection 三个小样本。
- 新增 `scripts/run-evals.mjs`，一条命令运行离线 evals。
- 默认评测 committed `baselineFindings`，无需 API key；后续可用 `evals/actual/<case-id>.json` 覆盖为 live/backend 输出。
- 输出 `evals/reports/latest.json` 与 `evals/reports/latest.md`。
- 当前报告：3 cases，schema pass rate 100%，expected finding hit rate 100%，severity/category match 100%，false positives 0，gate rejections 0。

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

### Priority 5: Security Hardening（已完成基础版）

目标：

把项目安全边界补到能放心开源和演示。

当前实现状态：

- 新增 `scripts/secret-scan.mjs`，一条命令扫描高置信 secret pattern。
- 新增 `.semgrep.yml`，覆盖 Authorization 泄露、token log、前端 API hardcode、后端 stdout 等基础规则。
- 新增 `scripts/dependency-scan.mjs`，生成 direct dependency hygiene report。
- 新增 `scripts/static-scan.mjs`，统一执行 secret scan、dependency scan、Semgrep（本地有 Semgrep 时）。
- 新增 `docs/SECURITY_CHECKLIST.md`，覆盖禁止提交文件、GitHub token 最小权限、API redaction、prompt injection 输入隔离和上传 checklist。
- 新增 `docs/STATIC_ANALYSIS.md`，说明 Semgrep / dependency scan 本地与 CI 用法。
- `.github/workflows/ci.yml` 增加 Static Analysis job，CI 中安装 Semgrep 并强制执行 `REQUIRE_SEMGREP=1 node scripts/static-scan.mjs`。
- `.env.example` 补充前端 API 地址与 GitHub token 最小权限注释。
- README 增加 Security Checks 入口。
- `node scripts/static-scan.mjs` 当前通过：未发现高置信 secrets，dependency scan 无 blocking issue。本机未安装 Semgrep 时本地跳过，CI 强制执行 Semgrep。

后续增强：

1. 增加更系统的 raw prompt/output redaction regression tests。
2. 扩展 Semgrep 规则覆盖更多 Java/React 安全模式。
3. 接入在线 CVE 数据库，例如 Dependabot、`npm audit`、OWASP Dependency-Check。
4. 增加针对 eval/live capture 文件的隐私检查。

验收：

- 一条命令完成 secret scan。
- 一条命令完成本地 static scan。
- 文档明确 token 权限和禁止提交文件。
- API 测试证明敏感字段不返回。

### 当前阶段暂不推进的生产化能力

以下能力有工程价值，但对当前“单人简历项目 + 本地可演示 MVP”的收益低于复杂度，暂不作为上传 GitHub 前任务：

1. 异步任务队列、取消、重试、超时治理。
2. 多用户、OAuth、GitHub App、生产级密钥托管。
3. 多租户权限模型、审计后台、生产级部署编排。

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

### 简历描述版本 B：当前完成 Priority 1-5 后

```text
基于 Spring Boot + React 构建 GitHub PR Code Review Agent，支持自动拉取 PR diff、
双 AI agent 协作审查、结构化 issue 生成、人工确认后发布 PR 评论、agent step trace
可观测性、离线 eval benchmark 与 Semgrep/secret/dependency 静态分析工具链。
设计了 planner/executor/gatekeeper 分层、敏感信息脱敏、人工确认外部 action、
失败定位和回归评测体系，用于提升 AI code review 的可靠性和可调试性。
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
已补 GitHub PR diff loader、人工确认后发布评论、agent trace UI、离线 eval benchmark、
secret scan、Semgrep 规则和 dependency hygiene scan。当前暂不做异步队列、多用户、
OAuth/GitHub App 或生产级密钥托管，因为这个项目定位是单人本地 MVP 和简历展示。
```

这比把未完成能力包装成已完成更可信。

## 7. 上传 GitHub 前建议

上传前建议先完成：

1. 用户本地验收：
   - `JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test`（`backend-java/`）
   - `npm run typecheck && npm run build && npm test -- --run`（`frontend/`）
   - `node scripts/run-evals.mjs`
   - `node scripts/static-scan.mjs`
2. 补充 LICENSE。
3. 确认 `.env`、`docs/mimo_api_key.md`、`backend-java/data/`、`frontend/dist/`、`backend-java/target/`、`frontend/node_modules/` 不会提交。
4. README 顶部明确当前是本地 MVP，不是生产级 SaaS。
5. 参考 `docs/PROJECT_SUMMARY_AND_NEXT_STEPS.md` 做最终上传前验收。
6. 保留一张架构图或 Mermaid 流程图，方便 GitHub 读者快速理解。

## 8. 当前公开文档与上传 GitHub 流程

当前建议保留并上传的公开文档：

```text
README.md
docs/PRD.md
docs/ARCHITECTURE.md
docs/API.md
docs/DATABASE.md
docs/PROJECT_HANDOFF_AND_ROADMAP.md
docs/PROJECT_SUMMARY_AND_NEXT_STEPS.md
docs/SECURITY_CHECKLIST.md
docs/STATIC_ANALYSIS.md
```

`docs/mimo_api_key.md` 是本地密钥记录文件，必须继续被 `.gitignore` 排除，不上传 GitHub。

建议上传 GitHub 前最后执行：

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test

cd ../frontend
npm run typecheck
npm run build
npm test -- --run

cd ..
node scripts/run-evals.mjs
node scripts/static-scan.mjs
git status --short
```

用户完成本地使用测试后，再执行 GitHub 上传。上传时不要提交本地密钥、H2 数据、构建产物或 IDE/process 目录。
