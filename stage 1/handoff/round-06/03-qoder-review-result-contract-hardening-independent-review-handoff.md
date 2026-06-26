# Round 06 Qoder Handoff: Review Result Contract Hardening Independent Review

## 1. Summary

Qoder 独立审查了 Round 06 "Review Result Contract Hardening" 的全部实现，覆盖 backend contract、frontend source-of-truth、issue lifecycle/source、文档边界、scope compliance，并独立复现了 backend 33 tests + frontend 26 tests + runtime curl 三端点验证。

核心结论：Round 06 成功将 review result contract 硬化为 persistence-ready 状态。backend 已成为 issue summary 与 risk level 的 authoritative source，frontend 优先消费 backend `issueSummary` 并保留集中式 fallback，`IssueSource`/`IssueStatus` 进入契约且默认值正确，legacy `IssueType` 已删除，无任何禁止 scope 被引入。

最终裁定：`ROUND_06_ACCEPTED_WITH_NOTES`。无 blocking issue，3 项非阻塞 notes 可带入 Round 07。当前契约可直接作为 Round 07 Database Persistence v1 的 schema 设计基础。

---

## 2. Verdict

```
ROUND_06_ACCEPTED_WITH_NOTES
```

---

## 3. Evidence Reviewed

### 上游交接文档

- `tasks/round-06/00-round-06-start.md` — Round 06 任务定义与 scope 边界
- `handoff/round-06/01-cursor-review-result-contract-hardening-handoff.md` — Cursor 实现交接
- `handoff/round-06/02-codex-review-result-contract-hardening-validation-handoff.md` — Codex 验证交接

### Backend 实现文件（逐行审查）

- `backend-java/src/main/java/com/codereviewx/backend/review/dto/IssueSummaryResponse.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/dto/ReviewIssueResponse.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/dto/ReviewTaskResponse.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/enums/RiskLevel.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/enums/IssueSource.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/enums/IssueStatus.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/enums/IssueCategory.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/enums/IssueSeverity.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/model/ReviewTask.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/service/ReviewTaskService.java`
- `backend-java/src/test/java/com/codereviewx/backend/review/controller/ReviewTaskControllerTest.java`
- `backend-java/src/test/java/com/codereviewx/backend/review/service/ReviewTaskServiceTest.java`

### Frontend 实现文件（逐行审查）

- `frontend/src/types/reviewTask.ts`
- `frontend/src/utils/reviewSummary.ts`
- `frontend/src/components/ReviewTaskDetail.tsx`
- `frontend/src/test/ReviewTaskDetail.test.tsx`
- `frontend/README.md`

### 文档文件

- `README.md`（根）
- `frontend/README.md`

### Qoder 独立执行的命令

1. **Scope grep 审计**：
   - `rg "IssueType" backend-java/src` → 0 matches（确认已删除）
   - `rg "JpaRepository|CrudRepository|@Entity|@Table|DataSource|Flyway|Liquibase|SecurityConfig|Swagger|OpenAPI|@Mapper|@Repository" backend-java/src/main/java` → 仅 1 match，为 ReviewTask.java 注释文字 "no persistence, no @Entity, no ORM annotations"
   - `rg "OpenAI|Anthropic|Gemini|LlmClient|AiServiceClient|clone\(|ProcessBuilder|Runtime\.getRuntime" backend-java/src` → 0 matches
   - `rg "Redux|MobX|ReactQuery|XState|recharts|chart\.js|echarts|antd|@mui|material-ui" frontend/src` → 0 matches
   - `rg "next|ssr|getServerSideProps|getStaticProps" frontend/package.json` → 0 matches
   - `glob backend-java/src/main/java/com/codereviewx/backend/review/enums/*.java` → 6 files（IssueCategory, IssueSeverity, IssueSource, IssueStatus, ReviewTaskStatus, RiskLevel），无 IssueType.java

2. **Backend 测试独立复现**：
   ```bash
   cd backend-java
   JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
   ```
   Result: Tests run: 33, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS

3. **Frontend 测试独立复现**：
   ```bash
   cd frontend
   npm run typecheck  # 0 errors
   npm run build      # 34 modules, 3 output files, built in 213ms
   npm test           # 4 test files, 26 tests, all passed
   ```

4. **Backend runtime curl 验证**：
   ```bash
   curl http://localhost:8080/api/health
   curl -X POST http://localhost:8080/api/review-tasks -H "Content-Type: application/json" -d '{"repoUrl":"https://github.com/example/round-06-qoder-review","prNumber":6}'
   curl http://localhost:8080/api/review-tasks
   curl http://localhost:8080/api/review-tasks/1
   ```
   全部端点返回正确契约，详见第 4 节。

---

## 4. Backend Contract Review

### 4.1 IssueSummaryResponse

`IssueSummaryResponse.java` 存在且字段完整：

| 字段 | 类型 | 状态 |
|---|---|---|
| `totalIssues` | `int` | ✅ |
| `highCount` | `int` | ✅ |
| `mediumCount` | `int` | ✅ |
| `lowCount` | `int` | ✅ |
| `riskLevel` | `RiskLevel` | ✅ |

提供无参构造器和全参构造器，符合 Spring Boot Jackson 序列化要求。

### 4.2 ReviewTaskResponse.issueSummary

`ReviewTaskResponse.java` 第 21 行包含 `private IssueSummaryResponse issueSummary;`，配有 getter/setter。`riskLevel` 字段保留在第 16 行用于兼容。

### 4.3 riskLevel 一致性

`ReviewTaskService.createTask()` 中：
- 第 55 行：`IssueSummaryResponse issueSummary = buildIssueSummary(issues);`
- 第 57 行：`task.setRiskLevel(issueSummary.getRiskLevel());`

`ReviewTaskService.toResponse()` 中：
- 第 161 行：`IssueSummaryResponse issueSummary = buildIssueSummary(issues);`
- 第 169 行：`response.setRiskLevel(task.getRiskLevel());`
- 第 174 行：`response.setIssueSummary(issueSummary);`

由于 `task.riskLevel` 在 create 时从 `buildIssueSummary` 设置，`toResponse` 中 `issueSummary` 从同一 `buildIssueSummary` 重新计算，两者始终一致。Runtime curl 验证确认 `riskLevel == issueSummary.riskLevel` 在 create/list/detail 三个端点均为 `True`。

### 4.4 聚合规则

`buildIssueSummary()` 方法（第 130-157 行）实现正确的聚合规则：

```
highCount = count(severity == HIGH)
mediumCount = count(severity == MEDIUM)
lowCount = count(severity == LOW)
totalIssues = issues.size()

riskLevel:
  if highCount > 0 → HIGH
  else if mediumCount > 0 → MEDIUM
  else if lowCount > 0 → LOW
  else → NONE
```

优先级 `HIGH > MEDIUM > LOW > NONE` 正确。`RiskLevel.NONE` 用于无 issue 场景。

### 4.5 RiskLevel 枚举

```java
public enum RiskLevel {
    NONE,
    LOW,
    MEDIUM,
    HIGH
}
```

包含 `NONE`，无 `CRITICAL`。符合要求。

### 4.6 IssueSource 枚举

```java
public enum IssueSource {
    MOCK,
    SEMGREP,
    LLM,
    MANUAL
}
```

包含 `MOCK`（当前使用）、`SEMGREP`/`LLM`（未来 pipeline 扩展）、`MANUAL`（未来 human review 扩展）。集合 future-proof 且 bounded。

### 4.7 IssueStatus 枚举

```java
public enum IssueStatus {
    OPEN,
    RESOLVED,
    FALSE_POSITIVE
}
```

包含 `OPEN`（当前 mock 默认）、`RESOLVED`、`FALSE_POSITIVE`（未来 workflow 扩展）。未实现 status update API，符合 scope。

### 4.8 ReviewIssueResponse source/status

`ReviewIssueResponse.java` 第 13-14 行：
```java
private IssueSource source;
private IssueStatus status;
```

配有 getter/setter。Mock issues 在 `buildMockIssues()` 中均设置为 `IssueSource.MOCK` 和 `IssueStatus.OPEN`。

### 4.9 IssueType 清理

`IssueType.java` 已删除。grep 确认 `backend-java/src` 中 0 处引用。enums 目录下 6 个文件均无 `IssueType.java`。

### 4.10 端点 / Wrapper 兼容性

- `POST /api/review-tasks` — 路径不变 ✅
- `GET /api/review-tasks` — 路径不变 ✅
- `GET /api/review-tasks/{id}` — 路径不变 ✅
- `GET /api/health` — 路径不变 ✅
- `ApiResponse<T>` wrapper（`success`/`message`/`data`）— 结构不变 ✅
- in-memory `ConcurrentHashMap` — 存储方式不变 ✅

### 4.11 Backend 测试覆盖

Controller 测试（12 tests）新增：
- `createTask_issuesHaveSourceAndStatus` — 验证 3 个 issue 的 source=MOCK, status=OPEN
- `createTask_hasIssueSummary` — 验证 issueSummary 字段及 5 个子字段
- `createTask_riskLevelConsistentWithIssueSummary` — 验证 riskLevel 一致性
- `listTasks_containsIssueSummary` — 验证 list 端点返回 issueSummary
- `getTask_containsIssueSummary` — 验证 detail 端点返回 issueSummary 及一致性

Service 测试（20 tests）新增：
- `createTask_hasIssueSummary` — 验证 issueSummary 非空及全部字段值
- `createTask_riskLevelConsistentWithIssueSummary` — 验证 riskLevel 一致性
- `createTask_allIssuesHaveSourceMock` — 验证所有 issue source=MOCK
- `createTask_allIssuesHaveStatusOpen` — 验证所有 issue status=OPEN

Qoder 独立复现：33 tests, 0 failures, 0 errors, 0 skipped。

### 4.12 Runtime Curl 验证结果

| 验证项 | Create | List | Detail |
|---|---|---|---|
| `success = true` | ✅ | ✅ | ✅ |
| `issueSummary` 存在 | ✅ | ✅ | ✅ |
| `issueSummary.totalIssues = 3` | ✅ | ✅ | ✅ |
| `issueSummary.highCount = 1` | ✅ | — | ✅ |
| `issueSummary.mediumCount = 1` | ✅ | — | ✅ |
| `issueSummary.lowCount = 1` | ✅ | — | ✅ |
| `issueSummary.riskLevel = HIGH` | ✅ | ✅ | ✅ |
| `riskLevel = HIGH` | ✅ | ✅ | ✅ |
| `riskLevel == issueSummary.riskLevel` | ✅ | ✅ | ✅ |
| `issues[0].source = MOCK` | ✅ | ✅ | ✅ |
| `issues[0].status = OPEN` | ✅ | ✅ | ✅ |
| `issues[1].source = MOCK` | ✅ | — | ✅ |
| `issues[2].source = MOCK` | ✅ | — | ✅ |

---

## 5. Frontend Contract Review

### 5.1 前端类型定义

`reviewTask.ts` 类型与 backend 完全对齐：

| 类型 | 定义 | 状态 |
|---|---|---|
| `RiskLevel` | `'NONE' \| 'LOW' \| 'MEDIUM' \| 'HIGH'` | ✅ 包含 NONE |
| `IssueSource` | `'MOCK' \| 'SEMGREP' \| 'LLM' \| 'MANUAL'` | ✅ |
| `IssueStatus` | `'OPEN' \| 'RESOLVED' \| 'FALSE_POSITIVE'` | ✅ |
| `IssueSummary` | `{ totalIssues, highCount, mediumCount, lowCount, riskLevel }` | ✅ |
| `ReviewIssue.source` | `IssueSource` (required) | ✅ |
| `ReviewIssue.status` | `IssueStatus` (required) | ✅ |
| `ReviewTask.issueSummary` | `IssueSummary?` (optional) | ✅ 兼容 fallback |

### 5.2 getIssueSummary 优先 backend summary

`reviewSummary.ts` 第 27-29 行：
```typescript
export function getIssueSummary(task: ReviewTask): IssueSummary {
  return task.issueSummary ?? computeIssueSummaryFromIssues(task.issues);
}
```

优先返回 `task.issueSummary`（backend 数据），仅在缺失时 fallback 到本地计算。逻辑集中在此单一函数，无重复计算散落。

### 5.3 Fallback 行为

`computeIssueSummaryFromIssues()` 实现 backend 等价的聚合逻辑：
- 按 severity 统计 high/medium/low count
- risk level 优先级 `HIGH > MEDIUM > LOW > NONE`

测试 `fallback summary works when issueSummary is missing` 验证当 `issueSummary: undefined` 时 summary panel 仍正确渲染。

### 5.4 Summary Panel source-of-truth

`ReviewTaskDetail.tsx` 第 147 行：
```tsx
<IssueSummaryPanel summary={getIssueSummary(task)} />
```

`IssueSummaryPanel` 接收归一化的 `IssueSummary` 对象，内部无本地计算。source-of-truth 链路清晰：backend `issueSummary` → `getIssueSummary()` → `IssueSummaryPanel`。

### 5.5 Issue Card source/status badges

`IssueCard` 组件第 86-91 行渲染四个 badge：
```tsx
<span className="badge badge-source" aria-label={`Source: ${issue.source}`}>{issue.source}</span>
<span className="badge badge-status" aria-label={`Status: ${issue.status}`}>{issue.status}</span>
```

Badge 顺序：`[severity] [category] [source] [status]`。测试验证 3 个 MOCK badge 和 3 个 OPEN badge。

### 5.6 Metadata Risk Level 显示

`ReviewTaskDetail.tsx` 第 139 行：
```tsx
<DetailRow label="Risk Level" value={task.riskLevel} />
```

Metadata 表格使用顶层 `task.riskLevel`，而 summary panel 使用 `getIssueSummary(task).riskLevel`。这是 Codex note #3 指出的双源问题。由于 backend runtime 验证确认两者始终相等，当前非阻塞，但属于 Round 07 设计 note（详见第 8.3 节）。

### 5.7 Demo/Mock 标签

`IssueSummaryPanel` 第 43 行：
```tsx
<span className="demo-label">Demo result — no real code was analyzed. Issue source: MOCK.</span>
```

标签清晰，包含 source 信息，不会让用户误认为真实分析。

### 5.8 无 workflow scope creep

确认未引入：
- status update 按钮 ✅
- resolve/false-positive 操作 ✅
- issue 编辑功能 ✅
- 复杂状态管理（Redux/MobX/React Query/XState）✅

### 5.9 Frontend 测试覆盖

`ReviewTaskDetail.test.tsx`（19 tests）新增：
- `renders source badges` — 验证 3 个 MOCK badge
- `renders status badges` — 验证 3 个 OPEN badge
- `summary panel prefers backend issueSummary over computed values` — 用故意不一致数据（totalIssues=55）验证 backend 优先
- `fallback summary works when issueSummary is missing` — 验证 fallback 路径

Qoder 独立复现：4 test files, 26 tests, all passed。typecheck 0 errors, build success。

---

## 6. Documentation Review

### 6.1 根 README

| 检查项 | 状态 |
|---|---|
| 明确当前实现为 mock/demo | ✅ "CodeReviewX currently runs as a mock/demo review task system" |
| Current Implementation 区段 | ✅ 包含 Round 06 contract 描述 |
| Current Review Result Contract | ✅ 展示 issueSummary JSON 和 source/status JSON |
| Demo / Mock Data Notice | ✅ 列出 6 项 "No..." 边界 |
| Planned Architecture 区段 | ✅ 与 current 分离 |
| Out of Scope 区段 | ✅ 列出 8 项未实现功能 |
| backend authoritative issue summary | ✅ "The backend is the authoritative source for review result aggregation and risk level" |
| frontend fallback | ✅ "only computes a local summary as a compatibility fallback" |
| MOCK source 文档化 | ✅ "Current issue source is `MOCK`" |
| OPEN status 文档化 | ✅ "Current issue status is `OPEN`" |
| 无 database | ✅ "All task data remains in-memory" |
| 无 GitHub/Semgrep/LLM/ai-service | ✅ Demo Notice 中明确列出 |
| curl 示例使用 repoUrl/prNumber | ✅ |

### 6.2 前端 README

| 检查项 | 状态 |
|---|---|
| Round 06 标题 | ✅ |
| Summary Source-of-Truth | ✅ "The backend is the **authoritative source**" |
| frontend fallback | ✅ "computes a local fallback summary from `task.issues`" |
| Issue Cards 四 badge | ✅ "[severity] [category] [source] [status]" |
| MOCK source / OPEN status | ✅ "source = MOCK, status = OPEN" |
| Demo / Mock Data Notice | ✅ 8 项 "No..." 边界 |
| 无 database/GitHub/Semgrep/LLM | ✅ |
| in-memory storage | ✅ "Restarting the backend will clear all task data" |

### 6.3 README Project Overview 用词 Note

根 README 第 93-95 行 "Project Overview" 区段使用 broad planned-product 措辞：

> "CodeReviewX is a GitHub Pull Request code review system. A user provides a repository URL and PR number. The system fetches the PR diff, runs static analysis with Semgrep, generates a structured review report via LLM, and displays the results in a web interface."

此段出现在 Current Implementation / Planned Architecture / Out of Scope 之后，描述的是规划中的产品愿景而非当前实现。虽然不会让仔细阅读的读者混淆（因为前面已有明确分区），但快速浏览者可能误读。

**Qoder 裁定**：非阻塞。建议 Round 07 将此段重命名为 "Planned Product Vision" 或添加一句澄清。不要求 Round 06 立即修改。

---

## 7. Scope Compliance Review

Qoder 通过 grep + 文件结构检查 + 源码逐行审查，确认以下禁止 scope 均未引入：

| 禁止项 | 检查方式 | 结果 |
|---|---|---|
| database / persistence | grep `@Entity\|@Table\|DataSource\|Flyway\|Liquibase` | ✅ 未引入（仅注释文字命中） |
| JPA/MyBatis/Hibernate | grep `JpaRepository\|CrudRepository\|@Mapper\|@Repository` | ✅ 未引入 |
| Entity/Repository/Mapper | 文件结构 + grep | ✅ 未引入 |
| migration | grep | ✅ 未引入 |
| GitHub API | grep `GitHub` in src | ✅ 未调用 |
| repository clone | grep `clone\(\|ProcessBuilder` | ✅ 未引入 |
| Semgrep execution | grep `Semgrep` in src | ✅ 未执行（仅 enum 值 `SEMGREP`） |
| LLM / AI service | grep `OpenAI\|Anthropic\|Gemini\|LlmClient\|AiServiceClient` | ✅ 未调用（仅 enum 值 `LLM`） |
| Spring Security / auth | grep `SecurityConfig` | ✅ 未引入 |
| Swagger / OpenAPI | grep `Swagger\|OpenAPI` | ✅ 未引入 |
| chart library | grep `recharts\|chart\.js\|echarts` | ✅ 未引入 |
| UI component library | grep `antd\|@mui\|material-ui` | ✅ 未引入 |
| Redux/MobX/React Query/XState | grep | ✅ 未引入 |
| Next.js / SSR | grep `next\|ssr\|getServerSideProps` in package.json | ✅ 未引入 |
| status update API | 源码审查 | ✅ 未实现 |
| resolve/false-positive workflow | 源码审查 | ✅ 未实现 |
| human reviewer workflow | 源码审查 | ✅ 未实现 |
| Round 07 work | 源码审查 | ✅ 未开始 |

**结论**：Round 06 scope 完全合规，无任何 scope creep。

---

## 8. Codex Notes Re-evaluation

### 8.1 Git Repository Unavailable

**Codex note**: `/Users/liyi/projects/CodeReviewX` is not currently a git repository, so `git status --short` could not produce a workspace diff.

**Qoder 裁定**: Accept — 非阻塞。

**分析**：
1. Codex 已通过静态文件审查 + 33 backend tests + 26 frontend tests + runtime curl + browser validation 充分补偿了 git diff 的缺失。
2. Qoder 独立复现了全部测试和 runtime curl，进一步验证了实现完整性。
3. 对 handoff 信任度无影响：Codex 提供的文件级变更清单 + Qoder 逐行审查已覆盖所有变更文件。
4. 建议：未来 agent 任务在 git 不可用时，应继续在 handoff 中提供显式 changed file list（Codex 和 Cursor handoff 均已做到）。

### 8.2 README Project Overview Wording

**Codex note**: Root `README.md` still has a broad "Project Overview" sentence phrased as if CodeReviewX is a full GitHub/Semgrep/LLM review system, but it appears after clear `Current Implementation`, `Planned Architecture`, and `Out of Scope` sections.

**Qoder 裁定**: Accept — 非阻塞，建议 Round 07 做小范围清理。

**分析**：
1. 该段位于 README 第 93 行，在 Current Implementation（第 7 行）、Planned Architecture（第 74 行）、Out of Scope（第 78 行）之后，上下文已明确这是规划描述。
2. 不会让仔细阅读的读者混淆当前实现状态。
3. 但快速浏览者或新 agent 可能误读为当前能力。
4. **建议**：Round 07 将 "Project Overview" 重命名为 "Planned Product Vision"，或在段首添加 "The following describes the planned end-state product vision, not the current implementation."。
5. 不要求 Round 06 立即修改——当前 README 已有充分的 current/planned/out-of-scope 分区。

### 8.3 Metadata Uses task.riskLevel

**Codex note**: `ReviewTaskDetail` still displays the top-level `task.riskLevel` in the metadata table while the summary panel uses `getIssueSummary(task)`.

**Qoder 裁定**: Accept — 非阻塞 for Round 06，重要 Round 07 设计 note。

**分析**：
1. **当前安全**：backend `toResponse()` 中 `task.riskLevel` 和 `issueSummary.riskLevel` 均来自同一 `buildIssueSummary(issues)` 计算，runtime curl 验证确认三者（create/list/detail）始终相等。
2. **未来风险**：一旦 Round 07 引入持久化且 issues 可变（如 status update workflow），`task.riskLevel` 可能与重新计算的 `issueSummary.riskLevel` 产生不一致。
3. **Round 07 设计建议**：
   - `riskLevel` 应成为 derived-only 字段，从 issue 聚合计算，不允许独立 mutation。
   - 或前端 metadata risk label 也改用 `getIssueSummary(task).riskLevel`，消除双源。
   - persistence schema 中 `riskLevel` 可作为 computed column 或 read-time aggregate，不作为独立可变列。
4. 不要求 Round 06 修改——当前 in-memory 无 mutation 场景下双源等价。

---

## 9. Blocking Findings

```
None.
```

无 blocking issue。Round 06 的核心契约目标全部达成。

---

## 10. Non-blocking Findings

以下 notes 带入 Round 07，不阻塞 Round 06 关闭：

1. **README "Project Overview" 用词**（来自 Codex note #2）：建议 Round 07 将该段重命名为 "Planned Product Vision" 或添加澄清句。

2. **前端 metadata 双源 risk display**（来自 Codex note #3）：`ReviewTaskDetail` metadata 表使用 `task.riskLevel`，summary panel 使用 `getIssueSummary(task).riskLevel`。当前等价，但 Round 07 引入可变 issues 后需统一为单源。

3. **toResponse 重复计算 issueSummary**：`createTask` 已计算 `issueSummary` 并设置 `task.riskLevel`，但 `toResponse` 每次读取时重新调用 `buildIssueSummary`。当前 in-memory 无性能影响，但 Round 07 持久化后可考虑缓存或 materialized view（仅在性能需要时）。

4. **IssueStatus/IssueSource 契约已就位但无 workflow**：`IssueStatus.RESOLVED`、`FALSE_POSITIVE` 和 `IssueSource.SEMGREP`、`LLM`、`MANUAL` 均已定义但未被使用。这是正确的 contract-first 做法，Round 07+ 可逐步启用。

5. **app.css 无 badge-source/badge-status 专属样式**：source 和 status badge 回退到基础 `.badge` 样式，视觉上无差异化。非功能性问题，Round 07 可选择性添加。

6. **RiskLevel.NONE 未有 runtime 验证场景**：mock 始终生成 3 个 issue（1H/1M/1L），`NONE` 仅在代码和 fallback 逻辑中被覆盖，无 runtime 空 issue 场景验证。Round 07 持久化后可补充空 task 测试。

---

## 11. Round 07 Readiness

### 11.1 当前契约是否可支撑 persistence schema 设计？

**是。** 当前 `ReviewTaskResponse` / `ReviewIssueResponse` / `IssueSummaryResponse` 的字段集合和类型已足够稳定，可直接映射为数据库表结构：

- `ReviewTask` → `review_task` 表（id, repo_url, pr_number, status, summary, error_message, created_at, updated_at）
- `ReviewIssue` → `review_issue` 表（id, review_task_id FK, severity, category, source, status, file_path, start_line, end_line, title, description, recommendation）
- `IssueSummary` → 不持久化，read-time 从 `review_issue` 聚合计算

### 11.2 issueSummary 应持久化还是动态计算？

**建议动态计算（read-time aggregate）。**

理由：
1. 当前 mock 数据量极小，聚合计算无性能压力。
2. 持久化 summary 会引入数据一致性问题（issues 变更时需同步更新 summary）。
3. 除非未来出现高频读取 + 大量 issues 的性能瓶颈，否则 read-time aggregate 是最简且最一致的方案。
4. 如确需 materialized view，应通过数据库触发器或应用层 invariant 强制同步，不允许独立 mutation。

### 11.3 riskLevel 应持久化还是 derived？

**建议 derived-only（从 issue 聚合计算）。**

理由：
1. 当前 `task.riskLevel` 和 `issueSummary.riskLevel` 来自同一计算，持久化独立列会引入双源不一致风险。
2. derived-only 保证 `riskLevel` 始终反映当前 issues 的真实风险。
3. 如需查询效率，可使用数据库 computed column 或 view，但不作为应用层可独立更新的字段。
4. 这直接解决了 Codex note #3 的前端双源问题——如果 `riskLevel` 始终从 aggregate 派生，前端 metadata 和 summary panel 天然一致。

### 11.4 IssueSource/IssueStatus 是否应进入 persistence schema？

**是。** `source` 和 `status` 应作为 `review_issue` 表的列：
- `source` — `VARCHAR` 或 `ENUM`，默认 `MOCK`
- `status` — `VARCHAR` 或 `ENUM`，默认 `OPEN`

### 11.5 IssueStatus 是否应保持 read-only？

**是，Round 07 应保持 read-only。**

Round 07 的目标是 persistence，不是 workflow。status update / resolve / false-positive workflow 应在更后续的 round 引入，且需配套 invariant（如 status 变更时重新计算 riskLevel）。

### 11.6 Round 07 是否可安全引入 database 而不接 GitHub/Semgrep/LLM？

**是。** 当前 mock issue generation 逻辑（`buildMockIssues()`）与外部集成完全解耦，持久化后只需将 `ConcurrentHashMap` 替换为数据库读写，mock 生成逻辑不变。API contract 保持不变，frontend 无需改动。

### 11.7 Round 07 应继续禁止什么？

- GitHub API / repository clone / real code parsing
- Semgrep execution
- LLM / ai-service 调用
- auth / Spring Security
- status update workflow / false-positive workflow / human reviewer
- chart / component library
- 复杂状态管理
- diff viewer / syntax highlighting
- issue filtering / sorting

### 11.8 Round 07 契约兼容性验收标准

- 现有 `POST/GET /api/review-tasks` 端点路径不变
- `ApiResponse<T>` wrapper 不变
- `ReviewTaskResponse` / `ReviewIssueResponse` 字段不变（`issueSummary` 仍由 backend 计算）
- `riskLevel == issueSummary.riskLevel` invariant 保持
- backend 重启后 task 数据不再丢失（持久化验证）
- 现有 33 backend tests + 26 frontend tests 仍通过（或等效更新后通过）
- mock issue generation 仍 deterministic（3 issues: 1H/1M/1L, source=MOCK, status=OPEN）

---

## 12. Recommended Round 07 Direction

```
Round 07: Database Persistence v1
```

### 具体建议

1. **引入数据库依赖**：Spring Data JPA + H2（开发）或 MySQL（后续），通过 `docker-compose.yml` 提供数据库容器。

2. **持久化 ReviewTask**：
   - `review_task` 表：id, repo_url, pr_number, status, summary, error_message, created_at, updated_at
   - 不持久化 `riskLevel` 作为独立可变列（derived from issues）

3. **持久化 ReviewIssue**：
   - `review_issue` 表：id, review_task_id (FK), severity, category, source, status, file_path, start_line, end_line, title, description, recommendation
   - `source` 默认 `MOCK`，`status` 默认 `OPEN`

4. **保持 mock issue generation**：`buildMockIssues()` 逻辑不变，create 时仍生成 3 个 deterministic mock issues，但写入数据库而非 ConcurrentHashMap。

5. **保持 API contract**：
   - 端点路径不变
   - `ApiResponse<T>` wrapper 不变
   - `ReviewTaskResponse` / `ReviewIssueResponse` 字段不变
   - `issueSummary` 在 `toResponse()` 时从数据库读取的 issues 动态聚合

6. **riskLevel 一致性 invariant**：
   - `ReviewTaskResponse.riskLevel` 从 `buildIssueSummary(issues).getRiskLevel()` 派生
   - 不在数据库中存储独立 `risk_level` 列（或仅作为 computed column）
   - 保证 `riskLevel == issueSummary.riskLevel` 始终成立

7. **IssueStatus 保持 read-only**：Round 07 不实现 status update API。

8. **继续排除**：GitHub/Semgrep/LLM/ai-service/auth/workflow/diff viewer/filtering。

9. **验收标准**：backend 重启后数据持久、现有测试通过（或等效更新）、runtime curl 契约不变。

---

## 13. Final Recommendation

### Round 06 是否可以关闭？

**是。**

Round 06 成功完成了 review result contract hardening 的全部目标：

1. ✅ backend 成为 issue summary / risk level 的 authoritative source
2. ✅ `IssueSummaryResponse` 存在且字段正确
3. ✅ `ReviewTaskResponse.issueSummary` 存在
4. ✅ `riskLevel` 与 `issueSummary.riskLevel` 一致（runtime 三端点验证）
5. ✅ frontend summary panel 优先消费 backend `issueSummary`
6. ✅ frontend computed summary 仅作为 fallback（集中式，无散落）
7. ✅ `IssueStatus` 存在（OPEN/RESOLVED/FALSE_POSITIVE）
8. ✅ `ReviewIssueResponse.status` 进入契约
9. ✅ `IssueSource` 复用并扩展（MOCK/SEMGREP/LLM/MANUAL）
10. ✅ `ReviewIssueResponse.source` 进入契约
11. ✅ legacy `IssueType` 已删除（0 引用）
12. ✅ deterministic mock issue generation 保持
13. ✅ 现有 endpoints 和 `ApiResponse<T>` wrapper 不变
14. ✅ 未引入 database
15. ✅ 未引入 ai-service / GitHub / Semgrep / LLM
16. ✅ backend 33 tests + frontend 26 tests 通过（Qoder 独立复现）
17. ✅ README current/planned boundary 清晰
18. ✅ 无 scope creep

无 blocking issue。3 项 Codex notes 经 Qoder 重新评估均为非阻塞。当前 review result contract 已足够稳定，可直接作为 Round 07 database persistence 的 schema 设计基础。

### 建议生成

```
tasks/round-07/00-round-07-start.md
```

方向：**Database Persistence v1** — 持久化 ReviewTask 和 ReviewIssue，保持 API contract 不变，issueSummary 在 read-time 动态聚合，riskLevel 作为 derived field，继续排除 GitHub/Semgrep/LLM/workflow。
