# CodeReviewX Project Summary and Next Steps

> 本文档是上传 GitHub 和简历讲解前的统一项目汇总。它替代历史阶段 handoff、测试计划和多 agent 协作过程文档。

## 1. 项目定位

CodeReviewX 是一个本地可运行的 AI Code Review Agent MVP，面向单人简历项目和面试讲解。

当前目标不是做完整生产级 SaaS，而是展示一个可运行、可解释、可验证的 AI agent 工程闭环：

- 从 GitHub PR 或手动 diff 创建 review task。
- 拉取并裁剪 PR diff。
- 通过 Xiaomi MiMo 双 AI agent 完成审查。
- 将模型输出转换成结构化 issue。
- 生成 comment preview，由用户选择并确认后发布到 GitHub PR。
- 展示 agent trace，支持定位每一步成功或失败原因。
- 用 evals、secret scan、Semgrep、dependency scan 做基础质量和安全验收。

## 2. 当前已完成模块

### 2.1 后端与持久化

- Spring Boot 3 + Java 17 + Maven。
- H2 本地文件数据库，支持重启后保留任务数据。
- ReviewTask 创建、列表、详情查询。
- ReviewRun、ReviewIssue、ReviewToolTrace、ReviewInputSnapshot、ReviewCommentPreview 持久化。
- 统一错误码和 fail-fast 行为。

### 2.2 前端体验

- React 18 + TypeScript + Vite。
- 支持创建 review task。
- 展示 review summary、risk level、issue list、provider 命中信息。
- 展示 agent trace timeline。
- 展示 comment preview，支持选择和发布状态反馈。

### 2.3 GitHub PR 输入

- 支持 `GITHUB_PR` 输入模式。
- 后端通过 GitHub API 加载 PR metadata。
- 后端通过 PR files API 加载 changed files patch。
- 默认边界：
  - changed files: 50
  - total diff bytes: 512000
  - per-file patch bytes: 20000
- `review_input_snapshot` 只保存 sanitized summary，不保存 raw full diff。

### 2.4 MiMo 双 AI Agent

当前主链路：

```text
ReviewTask
  -> ReviewRun
  -> github.pr.metadata.load
  -> github.pr.diff.load
  -> mimo.ai1.plan
  -> mimo.ai2.execute
  -> mimo.ai1.gate
  -> issue.generate
  -> comment.preview.build
```

职责拆分：

- AI-1 Planner：生成任务拆解计划。
- AI-2 Executor：按计划执行代码审查。
- AI-1 Gatekeeper：判断 AI-2 输出是否可接受。
- MiMoIssueGenerator：把获批 JSON deterministic mapping 成结构化 issue。

关键边界：

- AI-2 不直接落库 issue。
- `MiMoIssueGenerator` 不调用 LLM。
- 缺少 key、非法 JSON、gate 拒绝都会 fail fast。
- 当前不使用 mock fallback，避免演示时把 mock 结果误当真实 AI 结果。

### 2.5 GitHub Comment Publish

- 后端生成本地 comment preview。
- 前端允许用户选择要发布的 preview。
- 发布前必须由用户确认。
- 调用 GitHub PR review comment API 发布评论。
- 持久化 publish status、GitHub comment id、publishedAt 和失败摘要。
- token 不返回给 API 调用方，不写入 trace 或前端状态。

### 2.6 Evals 与静态分析

- `scripts/run-evals.mjs`：离线 eval benchmark。
- `evals/cases/`：当前覆盖 null pointer、secret-like config、SQL injection。
- `scripts/secret-scan.mjs`：高置信 secret pattern 扫描。
- `.semgrep.yml`：基础 Java/React 安全规则。
- `scripts/dependency-scan.mjs`：直接依赖 hygiene scan。
- `scripts/static-scan.mjs`：统一执行 secret scan、dependency scan 和 Semgrep。
- CI 中强制安装并运行 Semgrep。

## 3. 安全边界

当前必须保持的安全规则：

- 真实 API key 只能放本地环境变量或被忽略的本地文件。
- `.env` 不提交。
- `docs/mimo_api_key.md` 不提交。
- `backend-java/data/` 不提交。
- `frontend/dist/`、`frontend/node_modules/`、`backend-java/target/` 不提交。
- API response 不返回 GitHub token、MiMo key、Authorization header。
- API/UI 不返回 raw prompt、raw model output、raw full diff。
- GitHub 评论发布必须经过用户选择和确认。

GitHub token 建议最小权限：

- Metadata: read
- Contents: read
- Pull requests: read/write

## 4. 本地运行地址

当前服务默认不常驻运行，需要使用时手动启动。

后端：

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run
```

地址：

```text
http://127.0.0.1:8080
```

前端：

```bash
cd frontend
npm run dev -- --host 127.0.0.1
```

地址：

```text
http://127.0.0.1:5173/
```

## 5. 当前验收命令

后端测试：

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
```

前端测试：

```bash
cd frontend
npm run typecheck
npm run build
npm test -- --run
```

Evals：

```bash
node scripts/run-evals.mjs
```

静态分析：

```bash
node scripts/static-scan.mjs
```

上传 GitHub 前检查：

```bash
git status --short
git diff --check
```

## 6. GitHub 上传前状态

已适合进入用户本机最终验收的能力：

- GitHub PR diff 自动拉取基础版。
- MiMo 双 AI agent 审查主链路。
- comment preview + 人工确认发布基础版。
- agent trace UI 基础版。
- eval benchmark 基础版。
- secret scan / Semgrep / dependency scan 基础版。
- README、API、Architecture、Database、安全和静态分析文档。

上传前仍建议完成：

1. 确认 `.gitignore` 覆盖所有本地密钥、数据库和构建产物。
2. 如需保持仓库整洁，可在检查完证据后关闭临时安全测试 PR。

## 6.1 本轮验收进展（2026-06-27）

已完成：

- 通过 Homebrew 安装 Semgrep `1.168.0`。
- 修正 `.semgrep.yml` 语言和规则误报问题。
- `REQUIRE_SEMGREP=1 node scripts/static-scan.mjs` 已通过：
  - secret scan: pass
  - dependency scan: pass
  - Semgrep: 4 rules, 0 findings
- 使用本地真实 MiMo core/monitor key 启动后端。
- `GET /api/health` 返回 `reviewProvider=mimo`、`mimoConfigured=true`。
- 使用 manual diff 安全样例完成真实 MiMo 端到端 smoke：
  - ReviewTask `289`
  - ReviewRun `161`
  - status `SUCCESS`
  - `providerUsed=mimo`
  - `providerHit=true`
  - 2 个 `MIMO` issue
  - 2 个 comment preview
  - agent trace 5 步全部 `SUCCESS`
- 前端已启动并验证能展示真实任务的 history、agent trace 和 comment preview。
- 已保存前端截图：`docs/assets/codereviewx-review-workspace.jpg`。
- `LICENSE` 已存在，当前为 MIT License。
- README 已补充截图、Semgrep 安装/验收说明和本地演示验收结果。
- 已完成真实 GitHub PR 端到端验收：
  - 临时安全测试 PR：`https://github.com/alexbyte1334/CodeReviewX/pull/4`
  - ReviewTask `385`
  - ReviewRun `257`
  - `reviewMode=GITHUB_PR`
  - `github.pr.metadata.load`：SUCCESS
  - `github.pr.diff.load`：SUCCESS，`fileCount=1`，`diffBytes=420`，`diffTruncated=false`
  - `mimo.ai1.plan`：SUCCESS
  - `mimo.ai2.execute`：SUCCESS，2 findings
  - `mimo.ai1.gate`：SUCCESS
  - `issue.generate`：SUCCESS，2 issues
  - `comment.preview.build`：SUCCESS，2 previews
  - 发布 preview `65` 到 GitHub PR，`publishStatus=PUBLISHED`
  - GitHub comment id `3485123121`
- 完整本地验收命令已通过：
  - `JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test`：116 tests passed
  - `npm run typecheck`：passed
  - `npm run build`：passed
  - `npm test -- --run`：57 tests passed
  - `node scripts/run-evals.mjs`：schema pass rate 100%，expected finding hit rate 100%
  - `git diff --check`：passed
- 本轮临时启动的后端 `8080` 和前端 `5173` 已关闭。

本轮任务结束状态：

- Semgrep 已安装、配置并纳入本地强制验收。
- 真实 MiMo key 已完成 manual diff 和 GitHub PR 两条链路 smoke。
- GitHub PR diff loader、MiMo 双 agent、comment preview、人工确认发布到 GitHub PR 已完成端到端验收。
- LICENSE、截图、README 展示材料、项目汇总文档已补齐。
- 本轮成果准备上传到 GitHub `origin/main`，临时安全测试 PR #4 保留为验收证据，不合并。
- 生产化队列、多用户、OAuth/GitHub App、生产级密钥托管继续保持当前阶段不处理。

## 6.2 GitHub 上传状态

本轮上传内容：

- GitHub PR diff loader 基础版。
- GitHub comment preview 选择、确认和发布基础版。
- Agent trace UI 基础版。
- Evals benchmark 基础版。
- Secret scan、Semgrep、dependency scan 静态分析工具链。
- README 展示材料、截图、LICENSE、Security/Static Analysis 文档。
- 清理历史阶段 handoff / 测试计划 / 多 agent 协作文档。

上传前安全确认：

- `.env` 已被 `.gitignore` 排除。
- `docs/mimo_api_key.md` 已被 `.gitignore` 排除。
- `backend-java/data/` 已被 `.gitignore` 排除。
- `frontend/dist/` 已被 `.gitignore` 排除。
- `node scripts/secret-scan.mjs` 已通过。
- `REQUIRE_SEMGREP=1 node scripts/static-scan.mjs` 已通过。

本轮结论：

```text
CodeReviewX 当前已完成单人简历 MVP 的核心闭环：
真实 GitHub PR diff 输入 -> MiMo 双 agent 审查 -> 结构化 issue ->
comment preview -> 人工确认发布 GitHub PR 评论 -> trace/evals/static scan 验收。

本轮任务结束。
```

## 7. 接下来优化方向

### 7.1 Repository Context Loader

当前 agent 主要依赖 bounded PR diff。下一步可以让后端加载更多仓库上下文，例如相关文件片段、调用方/被调用方、配置文件或测试文件摘要。

价值：

- 提高审查准确率。
- 面试中能解释 context selection 和 token budget。
- 比直接上 RAG 更适合当前 MVP。

建议边界：

- 只读取 PR touched files 附近上下文。
- 限制总字符数。
- 保存 sanitized snapshot summary，不保存完整源码上下文到公开 API。

### 7.2 Live Eval Capture

当前 evals 默认跑 baseline findings。下一步可以把真实 backend/MiMo 输出保存到 `evals/actual/`，用于回归。

价值：

- 能证明 AI 输出质量不是一次性演示。
- 能比较不同 prompt/model 配置的变化。

建议边界：

- 默认不自动保存敏感 diff。
- 保存前做脱敏和体积限制。
- report 中展示 schema pass、expected hit、severity/category match、false positive。

### 7.3 静态分析增强

当前已有 Semgrep、secret scan、dependency hygiene scan 基础版。后续可以增强为更完整的开源安全检查。

方向：

- 扩展 `.semgrep.yml` 中的 Java/React 规则。
- 加入 Dependabot。
- 接入 OWASP Dependency-Check 或其他 CVE 数据源。
- 为 prompt/output redaction 增加 regression tests。

### 7.4 README 与演示材料

上传 GitHub 前可以继续优化项目展示：

- 增加架构图。
- 增加前端截图。
- 增加一段真实 review demo 输入输出。
- 增加简历项目描述和面试讲解要点。

## 8. 当前暂不处理的能力

以下能力不是当前阶段缺口，后续有时间再做：

- 异步任务队列、取消、重试、超时治理。
- 多用户、OAuth、GitHub App、生产级密钥托管。
- 多租户权限模型。
- 生产级部署和审计后台。
