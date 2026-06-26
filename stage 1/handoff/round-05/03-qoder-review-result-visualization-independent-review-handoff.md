# Qoder Handoff: Round 05 Review Result Visualization Independent Review

## 10.1 Summary

Qoder 作为独立架构与质量门禁审查者，对 Round 05 "Review Result Visualization Mock v1" 进行了全面审查。

审查范围包括：

1. 阅读 Round 05 start document、Cursor task、Codex task 及双方 handoff；
2. 检查 backend 全部核心代码（DTO/model/service/controller/enums/ApiResponse）；
3. 检查 frontend 全部核心代码（types/api/components/css/tests）；
4. 检查 README.md 和 frontend/README.md；
5. 独立运行 backend tests（24 tests, 0 failures）；
6. 独立运行 frontend typecheck/build/tests（22 tests, 0 failures）；
7. 独立启动 backend + frontend 进行 runtime curl 验证；
8. 独立通过 browser-use MCP 进行 UI create/list/detail flow spot check；
9. 执行 grep 范围审计，确认无 scope violation；
10. 对 10 个审查维度逐一给出架构判断。

Qoder 未修改任何 production code。本次审查为只读审查 + 测试/runtime 验证。

---

## 10.2 Verdict

```text
Verdict: ROUND_05_ACCEPTED_WITH_NOTES
```

理由：

1. Cursor 实现完成了 Round 05 全部核心目标：typed `ReviewIssue` contract、deterministic mock issues、frontend review result visualization；
2. backend/frontend typed issue contract 完全对齐，字段、枚举值、nullability 一致；
3. `POST /api/review-tasks` / `GET /api/review-tasks` / `GET /api/review-tasks/{id}` / `GET /api/health` 均保持兼容，`ApiResponse<T>` wrapper 未破坏；
4. backend tests 24 passed，frontend typecheck/build/tests 22 passed，Qoder 独立复现确认；
5. runtime curl 验证确认 create/list/detail 均返回 3 个 typed deterministic issues，enum 序列化为字符串；
6. browser UI 验证确认 create flow、summary panel、demo label、issue cards、severity/category badges、file path + line range、description、recommendation 全部可见；
7. README 准确标注 mock/demo 状态，curl 示例使用真实 `repoUrl` / `prNumber` 字段；
8. scope audit 逐项确认无 database、persistence、ai-service、GitHub、Semgrep、LLM、chart library 引入；
9. 存在非阻塞 notes：riskLevel 双 source-of-truth、legacy enums、shared in-memory test state、README planned vs current 区分；
10. 无 blocking issue，可以关闭 Round 05 并进入 Round 06。

---

## 10.3 Cursor Implementation Assessment

### Backend

1. **DTO/Model 职责清晰** — `ReviewIssueResponse` 为纯 DTO，字段完整（id/severity/category/filePath/startLine/endLine/title/description/recommendation），使用 `IssueSeverity` / `IssueCategory` enum 而非 String，contract 清晰；
2. **`ReviewTask` model** — `List<Object> issues` 已收敛为 `List<ReviewIssueResponse> issues`，类型安全；
3. **`ReviewTaskService`** — `buildMockIssues()` 方法集中生成 deterministic mock issues，逻辑清晰，3 个 issue 覆盖 HIGH/MEDIUM/LOW + SECURITY/MAINTAINABILITY/TEST；mock issue 描述明确标注 "demo issue"，不冒充真实分析；
4. **`ReviewTaskController`** — 未被修改，endpoints 不变，`ApiResponse<T>` wrapper 保持；
5. **`toResponse()` 方法** — 对 issues 做了 defensive copy (`new ArrayList<>(task.getIssues())`)，避免外部修改内部状态，细节合理；
6. **mock status flow** — `PENDING -> RUNNING -> SUCCESS` 三行连续 set，虽然是 mock 行为但不会误导，因为最终状态为 SUCCESS 且 summary 明确标注 DEMO DATA。

### Frontend

1. **TypeScript types** — `ReviewIssue` interface 与 backend `ReviewIssueResponse` 完全对齐，`endLine: number | null` 兼容 backend `Integer endLine`（可能为 null）；
2. **`ReviewTaskDetail` 组件拆分** — `IssueSummaryPanel` 和 `IssueCard` 作为内部函数组件，职责清晰，适合当前阶段；未过度抽取为独立文件，符合 MVP 原则；
3. **`computeRiskLabel` / `computeRiskClass`** — 简单 severity-based 规则，逻辑透明，未引入复杂 scoring；
4. **CSS** — 纯 CSS，无 Tailwind / CSS-in-JS，样式范围在 minimal polish 内；summary panel、issue cards、badges、recommendation highlight 样式完整；
5. **empty issues fallback** — `task.issues.length === 0` 时显示 "No review issues are available for this task yet."，保留未来真实 review 场景的 fallback；
6. **demo label** — summary panel 中 "Demo result — no real code was analyzed" 紫色 pill，视觉醒目。

### Tests

1. **backend tests** — 16 个 service tests + 7 个 controller tests + 1 个 context test = 24 tests，覆盖 typed issue fields（severity/category/filePath/startLine/title/description/recommendation）、issue count、severity coverage、create/list/detail flow、not-found、validation error；
2. **frontend tests** — 15 个 detail tests + 3 个 list tests + 1 个 create form test + 3 个 api tests = 22 tests，覆盖 summary panel、risk level、issue cards、severity/category badges、file path、line range、recommendation、empty fallback、loading、error；
3. **测试质量** — 对当前阶段足够，断言准确。

### README

1. 根 README 明确标注 "No real code review is performed at this stage"，列出 6 项 mock 限制；
2. frontend README 明确标注 "Mock Review Result Notice (Round 05)"，列出 6 项 mock 限制 + curl 示例；
3. `VITE_API_BASE_URL` 配置说明准确，明确警告 "Do not set to /api"；
4. Round Progress 表格显示 Round 05 ✅ Complete。

### Scope Control

Cursor 实现严格控制在 Round 05 范围内，未越界。无被 Cursor handoff 漏报的重要问题。

---

## 10.4 Codex Validation Assessment

1. **contract validation 充分** — Codex 验证了 backend/frontend typed issue contract 对齐，包括 `endLine: number | null` nullability 判断；
2. **API request contract 验证充分** — Codex 确认真实 create request fields 为 `repoUrl` / `prNumber`，并修正了 README curl 示例；
3. **runtime curl 验证充分** — Codex 执行了 health/create/list/detail curl 验证，记录了两次 create 的 deterministic 行为；
4. **browser UI 验证充分** — Codex 执行了 create flow、detail page、backend unavailable 验证；
5. **README 验证充分** — Codex 检查了 mock notice、curl 示例、VITE_API_BASE_URL 说明；
6. **scope audit 充分** — Codex 逐项确认 14 项 scope boundary；
7. **minimal fixes 合理** — Codex 仅修改了 README curl 示例字段，未改 production code；
8. **`ACCEPTED_WITH_NOTES` verdict 准确** — 与 Qoder 独立审查结论一致；
9. **未遗漏关键问题** — Codex 记录的 4 项 non-blocking notes（legacy enums、riskLevel dual source、shared in-memory state、bare java flaky）均被 Qoder 独立确认。

Codex validation 质量合格。

---

## 10.5 ReviewIssue Contract Assessment

### 当前字段

```text
id:             String
severity:       IssueSeverity (LOW | MEDIUM | HIGH)
category:       IssueCategory (BUG | SECURITY | PERFORMANCE | MAINTAINABILITY | STYLE | TEST)
filePath:       String
startLine:      Integer
endLine:        Integer
title:          String
description:    String
recommendation: String
```

### 作为 Round 05 基线的充分性判断

当前 contract **足以作为 Round 05 基线**，理由：

1. 9 个字段覆盖了 issue card 展示所需的全部信息；
2. severity/category 使用 enum，contract 清晰且前后端对齐；
3. filePath + startLine/endLine 支持未来 diff viewer 的基础定位；
4. title/description/recommendation 三段式结构适合 LLM review suggestion mapping；
5. id 字段（当前为 "ISSUE-1" 等固定字符串）未来可替换为 UUID 或 fingerprint。

### 后续 Semgrep/LLM/GitHub/persistence 能力支撑分析

| 后续能力 | 当前 contract 是否足够 | 需要新增的字段 |
|---|---|---|
| Semgrep result mapping | 基本足够 | `ruleId`（Semgrep rule ID）、`source`（SEMGREP/LLM） |
| LLM review suggestion mapping | 足够 | 无强制需求，title/description/recommendation 可承载 |
| GitHub diff comment mapping | 基本足够 | `codeSnippet` / `diffHunk`（可选，用于 PR comment 上下文） |
| database persistence | 足够 | `createdAt` / `updatedAt`（issue 级别时间戳）、`status`（OPEN/RESOLVED） |
| frontend issue cards | 完全足够 | 无 |
| issue filtering/sorting | 足够 | 可基于 severity/category/filePath 客户端过滤 |
| future code snippet/diff viewer | 不足 | `codeSnippet` / `diffHunk` |
| issue lifecycle/status | 不足 | `status`（OPEN/RESOLVED/FALSE_POSITIVE） |
| false-positive marking | 不足 | `status` + `reviewerComment` |
| human override/comment | 不足 | `reviewerComment` / `overriddenBy` |

### 字段演进建议

**应在 Round 06 加入的字段（persistence 前置需求）：**

```text
source:         IssueSource (SEMGREP | LLM | MANUAL)  — 区分 issue 来源
ruleId:         String                                — Semgrep rule ID 或 LLM prompt ID
status:         IssueStatus (OPEN | RESOLVED | FALSE_POSITIVE)
createdAt:      LocalDateTime
updatedAt:      LocalDateTime
```

**应延后到 Round 07+ 的字段：**

```text
confidence:     Double        — LLM 置信度，需要真实 LLM 集成后才有意义
fingerprint:    String        — issue 去重指纹，需要真实分析后才能定义
codeSnippet:    String        — 代码片段，需要 diff viewer 阶段
diffHunk:       String        — diff hunk，需要 GitHub PR diff 集成
reviewerComment: String       — 人工评审注释，需要 auth + multi-user
```

**结论：当前 contract 作为 Round 05 基线足够，不需要本轮补字段。Round 06 应在 persistence 之前或同时加入 `source` / `ruleId` / `status` / `createdAt` / `updatedAt`。**

---

## 10.6 Risk Level Source-of-truth Assessment

### 当前状态

1. **backend `ReviewTask.riskLevel`** — `ReviewTaskService.createTask()` 硬编码为 `RiskLevel.HIGH`，存储在 `ReviewTask` model 中，通过 `ReviewTaskResponse.riskLevel` 返回；
2. **frontend computed risk** — `ReviewTaskDetail.tsx` 中 `computeRiskLabel()` 基于 `task.issues` 的 severity 分布计算 risk level（any HIGH → High Risk, any MEDIUM → Medium Risk, ...）；
3. **detail 页面同时展示两处 risk** — metadata table 的 "Risk Level" 行展示 backend `task.riskLevel`（值为 "HIGH"），summary panel 的 "Risk Level" badge 展示 frontend computed risk（值为 "High Risk"）；
4. **当前 mock 数据下二者一致** — mock issues 包含 1 个 HIGH，backend riskLevel = HIGH，frontend computed = High Risk。

### 是否 blocking

**不是 blocking issue。** 理由：

1. 当前 mock 数据下二者一致，不会产生 UI 矛盾；
2. 用户看到的两个 risk 信号（metadata "HIGH" 和 summary "High Risk"）语义相同，只是格式略有差异；
3. 不影响 create/list/detail flow 功能；
4. 不影响 contract 对齐。

### 是否必须在 Round 05 修

**不需要。** 理由：

1. 修复需要引入 backend issue summary aggregation 或重命名 frontend computed risk，超出 Round 05 "mock visualization" 范围；
2. 当前实现已满足 Round 05 目标——"用 deterministic mock issues 收敛 ReviewIssue contract"；
3. 过早调整 risk aggregation 逻辑可能在 Round 06 persistence 时再次变更。

### 推荐 authoritative source

**推荐 backend 作为 authoritative source。** 理由：

1. backend 拥有完整的 issue 数据，是计算 risk 的正确位置；
2. frontend computed risk 是临时方案，未来真实 review 场景下 issue 可能异步返回，frontend 无法在 issue 未到齐时正确计算 risk；
3. backend 可以在 `createTask` 或异步 review 完成时计算 risk，保证一致性。

### 是否建议新增 backend `issueSummary` / `riskSummary`

**建议 Round 06 新增 backend aggregate。** 推荐方案：

```java
// ReviewTaskResponse 新增
private IssueSummary issueSummary;

// IssueSummary
public class IssueSummary {
    private int totalIssues;
    private int highCount;
    private int mediumCount;
    private int lowCount;
    private RiskLevel riskLevel;  // backend computed
}
```

frontend summary panel 应消费 `task.issueSummary.riskLevel`，而非自行计算。

### frontend fallback 策略

建议保留 frontend computed risk 作为 fallback：

```typescript
const riskLevel = task.issueSummary?.riskLevel ?? computeRiskLabel(task.issues);
```

当 backend 未返回 `issueSummary` 时（如旧数据兼容），frontend 回退到 computed risk。

### Round 06 建议

将 risk aggregation 作为 Round 06 前置设计项，在 persistence 或 contract cleanup 阶段一并处理。

---

## 10.7 Legacy Enum Assessment

### `IssueType` / `IssueSource` 现状

1. **`IssueType`** 仍存在于 `backend-java/src/main/java/com/codereviewx/backend/review/enums/IssueType.java`，定义了 `BUG / SECURITY / PERFORMANCE / TEST / STYLE`；
2. **`IssueSource`** 仍存在于 `backend-java/src/main/java/com/codereviewx/backend/review/enums/IssueSource.java`，定义了 `LLM / SEMGREP`；
3. **Qoder 独立 grep 确认**：在整个 `backend-java/src` 目录中，`IssueType` 和 `IssueSource` 仅出现在各自定义文件中，无任何其他文件引用它们；
4. 不影响编译和测试；
5. 不会误导 frontend 或 API consumer，因为它们不出现在 response 中。

### 是否阻塞

**不阻塞。** 理由：

1. 无引用，不影响运行时行为；
2. 不影响 contract；
3. 不影响测试。

### 是否应该在 Round 05 末尾删除

**不建议在 Round 05 末尾删除。** 理由：

1. 删除它们是纯 cleanup 操作，不改变任何运行时行为；
2. Round 05 的核心目标是 visualization mock，不是 codebase cleanup；
3. 在 Round 05 末尾删除会增加不必要的 churn；
4. `IssueSource` 的 `LLM / SEMGREP` 枚举值在未来 real integration 时可能复用（作为 `ReviewIssue.source` 字段）。

### 删除时机

建议在 Round 06 的 contract cleanup 阶段处理：

- 如果 Round 06 决定引入 `source` 字段，则 `IssueSource` enum 可复用并迁移到 `ReviewIssueResponse`；
- 如果 Round 06 决定不使用 `IssueSource`，则删除；
- `IssueType` 在 `IssueCategory` 存在后已无意义，可在 Round 06 安全删除。

---

## 10.8 Test and Runtime Assessment

### Backend Tests

```text
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
Result: PASS
Evidence: Tests run: 24, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS.
```

测试分布：
- `CodeReviewXBackendApplicationTests`: 1 test (context loads)
- `ReviewTaskControllerTest`: 7 tests (create success, typed fields, list, detail, not-found, invalid repoUrl, invalid prNumber)
- `ReviewTaskServiceTest`: 16 tests (create status, typed issues, severity, category, filePath, startLine, title, description, recommendation, severity coverage, unique ids, list empty, list created, get by id, get issues, not-found)

### Frontend Tests

```text
cd frontend
npm run typecheck
Result: PASS
Evidence: tsc --noEmit completed successfully.

npm run build
Result: PASS
Evidence: 33 modules transformed, built in 234ms.

npm test
Result: PASS
Evidence: Test Files 4 passed (4); Tests 22 passed (22).
```

测试分布：
- `reviewTaskApi.test.ts`: 3 tests
- `ReviewTaskCreateForm.test.tsx`: 1 test
- `ReviewTaskList.test.tsx`: 3 tests
- `ReviewTaskDetail.test.tsx`: 15 tests (placeholder, metadata, summary panel, total count, risk level, issue cards, severity badges, category badges, file path, line range, recommendation, empty fallback, no issues, loading, error)

### Runtime Verification

**Backend startup:**

```text
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run
Result: PASS
Evidence: Spring Boot 3.2.5 started on port 8080, Java 17.0.19, profile "local".
```

**Health check:**

```text
GET http://localhost:8080/api/health
HTTP 200
success=true, message=OK, data.status=UP, data.service=backend-java
```

**Create task:**

```text
POST http://localhost:8080/api/review-tasks
Body: {"repoUrl":"https://github.com/example/qoder-demo","prNumber":11}
HTTP 200
success=true, data.id=1, data.status=SUCCESS, data.riskLevel=HIGH
data.issues.length=3
data.issues[0].severity=HIGH, data.issues[0].category=SECURITY
data.issues[1].severity=MEDIUM, data.issues[1].category=MAINTAINABILITY
data.issues[2].severity=LOW, data.issues[2].category=TEST
```

**Second create (deterministic check):**

```text
POST http://localhost:8080/api/review-tasks
Body: {"repoUrl":"https://github.com/example/second","prNumber":99}
HTTP 200
data.id=2, issueCount=3
issueIds=ISSUE-1, ISSUE-2, ISSUE-3
severities=HIGH, MEDIUM, LOW
deterministic=True (identical to first create ignoring task-level id)
```

**List:**

```text
GET http://localhost:8080/api/review-tasks
HTTP 200
success=true, count=2, ids=1,2, issueCounts=3,3
```

**Detail:**

```text
GET http://localhost:8080/api/review-tasks/1
HTTP 200
success=true, id=1, issueCount=3
issueIds=ISSUE-1, ISSUE-2, ISSUE-3
severities=HIGH, MEDIUM, LOW
categories=SECURITY, MAINTAINABILITY, TEST
```

**Frontend runtime + browser UI:**

```text
cd frontend
npm run dev -- --host 127.0.0.1
Result: PASS
Evidence: Vite ready at http://127.0.0.1:5173/
```

Browser spot check via browser-use MCP:

1. Page loaded: title "CodeReviewX", backend status "UP" ✅
2. Pre-existing tasks #1 and #2 visible in list ✅
3. Clicked task #1: detail page showed metadata table + summary panel + issue cards ✅
4. Summary panel: "Review Result Summary", "Demo result — no real code was analyzed", Total Issues 3, High 1, Medium 1, Low 1, "HIGH RISK" badge ✅
5. Issue cards: 3 cards with severity badges (HIGH/MEDIUM/LOW), category badges (SECURITY/MAINTAINABILITY/TEST), file paths, line ranges (42–48, 76–93, 21), descriptions, recommendation blocks ✅
6. Create flow: filled form with repo "https://github.com/example/browser-test" PR 42, clicked "Create Review Task" → success message appeared, task #3 appeared in list, detail auto-selected showing full visualization ✅

### 测试质量评价

1. backend tests 覆盖 typed issue fields 充分；
2. frontend tests 覆盖 summary panel、issue cards、empty fallback、loading、error 充分；
3. risk-level calculation 有间接测试（"renders risk level High Risk when high issues exist"）；
4. API wrapper 有独立测试；
5. **shared in-memory state 风险**：`ReviewTaskControllerTest` 使用 `@SpringBootTest` shared context，controller tests 中的 `createTask` 调用会累积 task 到 in-memory store。当前测试不依赖精确 count 所以不失败，但未来如果增加 list-count 精确断言可能出问题。

### Known Limitations

1. 未测试 mobile/responsive viewport（任务文档不要求）；
2. 未测试 backend unavailable error state 在 browser 中的表现（Codex 已验证，Qoder 基于 Codex 结果 + 代码审查确认 `fetchJson` catch 分支会显示 error message）；
3. bare `java -version` 在本机不稳定，但使用 `JAVA_HOME=/opt/homebrew/opt/openjdk@17` 的 Maven 可正常运行。

---

## 10.9 Product Experience Assessment

### Demo Label

- summary panel 中 "Demo result — no real code was analyzed" 紫色 pill，视觉醒目，不会被忽略；
- backend summary 文本包含 "[DEMO DATA — no real analysis performed]"；
- 用户不太可能误以为这是真实代码审查。

### Summary Panel

- "Review Result Summary" 标题清晰；
- stat grid 展示 Total Issues / High / Medium / Low / Risk Level，信息层级合理；
- 数字 + 标签的垂直排列方式易于扫读；
- risk level badge 颜色编码（红色 High Risk / 黄色 Medium Risk / 绿色 Low Risk）直观。

### Issue Cards

- severity badge + category badge 在卡片顶部并排，易于快速识别；
- issue title 作为 h4，视觉层级正确；
- file path 使用 monospace 字体，line range 使用蓝色高亮，开发者友好；
- description 和 recommendation 分段展示，recommendation 有绿色背景高亮，突出修复建议；
- 信息层级：badges → title → location → description → recommendation，符合从"是什么"到"怎么修"的阅读流。

### Error/Empty States

- empty issues fallback："No review issues are available for this task yet." 清晰；
- loading state："Loading detail…" 带 spinner；
- error state：alert role，红色背景；
- no task selected："Select a review task to view details."

### 产品反馈支撑判断

1. 当前 UI 足以支撑产品迭代反馈——用户可以直观看到 review result 的形态；
2. issue card 的信息密度适中，不会过载；
3. summary panel 提供了快速概览能力；
4. demo label 清楚地界定了当前阶段的能力边界；
5. **可以作为 Round 06 的展示基线**。

### 建议的 UI polish（非阻塞，延后）

1. 两栏布局在小屏幕下可能偏宽，已有 `@media (max-width: 768px)` fallback 但未测试；
2. list 中 task summary 文本较长时可能截断不理想；
3. issue cards 无 filtering/sorting，当 issue 数量增多时可能需要（延后到真实 review 集成后）。

---

## 10.10 Documentation Assessment

### 根 README.md

1. **Current Status** — 明确标注 "Round 05: Review Result Visualization Mock v1"，列出本轮新增内容；
2. **Demo / Mock Data Notice** — 6 项明确声明（deterministic mock data, no GitHub API, no clone, no Semgrep, no LLM, in-memory）；
3. **Mock API Quick Check** — curl 示例使用真实字段 `repoUrl` / `prNumber`（Codex 已修正）；
4. **Project Overview** — 描述了 planned MVP flow，包括 backend-java calls ai-service、ai-service fetches GitHub diff 等；这些是 **planned** 描述，不是 current 实现；
5. **Planned Module Overview** — 表格描述了 planned 技术栈和职责，明确标注 "Planned"；
6. **Round Progress** — Round 05 标记为 ✅ Complete。

**潜在误导点（非阻塞）：** Project Overview 部分描述了完整的 MVP flow（"backend-java calls ai-service → ai-service fetches GitHub PR diff → ai-service runs Semgrep → ai-service calls LLM"），可能让快速浏览的读者误以为这些能力已实现。但 Demo / Mock Data Notice 部分已明确声明当前状态，且 Project Overview 标题本身是 "Project Overview"（远景描述），不是 "Current Implementation"。整体可接受。

### frontend/README.md

1. **Tech Stack** — React 18 + TypeScript + Vite，准确；
2. **Mock Review Result Notice (Round 05)** — 6 项明确声明 + curl 示例；
3. **API Base URL Configuration** — 明确警告 "Do not set VITE_API_BASE_URL to /api"；
4. **Data Notice** — "Restarting the backend will clear all task data"；
5. **Basic User Flow** — 8 步流程描述准确，包括 summary panel 和 issue cards。

**README 整体评估：准确，无严重误导。**

---

## 10.11 Scope Audit

Qoder 独立执行 grep 范围审计，逐项确认：

```text
Database introduced:                  NO
Persistence introduced:               NO
MyBatis/JPA introduced:               NO
ai-service called:                    NO
GitHub API called:                    NO
Semgrep executed:                     NO
LLM called:                           NO
Repository cloned:                    NO
Real code parsed:                     NO
Chart/component library introduced:   NO
Complex state management introduced:  NO
Existing endpoints changed:           NO
ApiResponse wrapper changed:          NO
Round 06 started:                     NO
```

Grep 验证证据：

1. `import.*persistence|import.*mybatis|import.*jpa|import.*hibernate|@Entity|@Table|@Repository|@Mapper` → 仅匹配到 `ReviewTask.java` 注释 "no persistence, no @Entity, no ORM annotations"，无实际引入；
2. `import.*antd|@mui|chart\.js|echarts|d3|redux|mobx|react-query|xstate|next/` in frontend → 0 matches；
3. `import.*openai|anthropic|langchain|llm|semgrep|github` in ai-service → 0 matches；
4. backend endpoints 仍为 `GET /api/health`, `POST /api/review-tasks`, `GET /api/review-tasks`, `GET /api/review-tasks/{id}`；
5. `ApiResponse<T>` wrapper 结构不变（`success` / `message` / `data`）。

**无 scope violation。**

---

## 10.12 Blocking Issues

```text
Blocking issues: None known.
```

---

## 10.13 Non-blocking Notes

### 1. riskLevel dual source-of-truth

- **状态**：backend `ReviewTask.riskLevel` 硬编码 HIGH + frontend `computeRiskLabel()` 基于 issues 计算；
- **当前影响**：无（mock 数据下二者一致）；
- **未来风险**：真实 review 场景下 issue 可能异步返回或 severity 分布不同，导致 backend riskLevel 与 frontend computed risk 不一致；
- **建议**：Round 06 引入 backend `IssueSummary` aggregate，frontend 消费 backend computed risk，保留 computed fallback。

### 2. legacy enum cleanup

- **状态**：`IssueType` / `IssueSource` 仍存在但无引用；
- **当前影响**：无；
- **建议**：Round 06 contract cleanup 阶段决定是否复用（`IssueSource` 可用于 `ReviewIssue.source` 字段）或删除。

### 3. shared in-memory state test risk

- **状态**：`ReviewTaskControllerTest` 使用 `@SpringBootTest` shared context，create 操作累积 task；
- **当前影响**：无（测试不依赖精确 count）；
- **未来风险**：增加 list-count 精确断言时可能 flaky；
- **建议**：Round 06 persistence 阶段使用 `@DirtiesContext` 或 `@Transactional` + rollback 隔离测试状态。

### 4. responsive UI polish

- **状态**：两栏布局在 `@media (max-width: 768px)` 下切换为单栏，但未实际测试 mobile viewport；
- **建议**：延后到真实 UI polish round。

### 5. README planned vs current state distinction

- **状态**：根 README Project Overview 描述了完整 MVP flow（包括 ai-service、GitHub、Semgrep、LLM），可能让快速浏览者误以为已实现；
- **建议**：可在 Project Overview 部分增加 "Planned — not yet implemented" 前缀，但不阻塞 Round 05。

### 6. fetchJson non-JSON error robustness

- **状态**：`fetchJson` 直接 `response.json()`，遇到 non-JSON error body（如 reverse proxy 502 HTML）会抛出 JSON parse error 而非有意义的 HTTP error；
- **建议**：Round 04 已记录此 note，延后到 API client hardening 阶段。

---

## 10.14 Recommended Next Step

```text
Proceed to Round 06 start document
```

### Round 06 优先方向建议

**推荐：risk-contract-first → persistence-first 的组合路径**

理由：

1. **risk-contract-first**（优先）：
   - 引入 backend `IssueSummary` aggregate（totalIssues / highCount / mediumCount / lowCount / riskLevel）；
   - frontend summary panel 消费 backend aggregate，移除 frontend computed risk 作为主 source；
   - 清理 legacy `IssueType` enum（`IssueSource` 可复用）；
   - 为 persistence 提供稳定的 contract，避免持久化后再次调整 schema；
   - 工作量小，风险低。

2. **persistence-first**（紧随其后）：
   - 在 contract 稳定后引入 database（MySQL/PostgreSQL + JPA）；
   - persist `ReviewTask` 和 `ReviewIssue`（含新增的 `source` / `ruleId` / `status` / `createdAt` / `updatedAt`）；
   - 保持 mock review generation 逻辑不变；
   - preserve API contract；
   - 为后续 real review integration 提供持久化基础。

3. **ai-integration-first**：
   - **不推荐立即推进**，除非 persistence 和 contract boundary 已稳定；
   - 真实 AI/GitHub/Semgrep integration 应在 persistence 完成后进行，否则 review result 无法持久化，用户体验会退化。

### 推荐路径

```text
Round 06: Contract cleanup + Risk aggregation
    ↓
Round 07: Database persistence v1
    ↓
Round 08: ai-service integration (GitHub + Semgrep + LLM mock)
    ↓
Round 09: Real LLM integration
```

### Round 06 具体建议

1. 新增 `IssueSummary` DTO，由 backend 在 `createTask` 时计算并返回；
2. `ReviewTaskResponse` 新增 `issueSummary` 字段；
3. frontend `IssueSummaryPanel` 改为消费 `task.issueSummary`；
4. 新增 `ReviewIssueResponse.source` (IssueSource enum) 和 `ReviewIssueResponse.status` (IssueStatus enum)；
5. 删除 `IssueType` enum；
6. 保留或重命名 `IssueSource` enum；
7. 更新 tests 覆盖 `IssueSummary`；
8. 不引入 database（留到 Round 07）；
9. 不调用 ai-service / GitHub / Semgrep / LLM。
