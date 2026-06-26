# tasks/round-05/03-qoder-review-result-visualization-independent-review.md

# Qoder Task: Round 05 Review Result Visualization Independent Review

## 1. Task Metadata

- Project: CodeReviewX
- Round: Round 05
- Task ID: `03-qoder-review-result-visualization-independent-review`
- Executor: Qoder
- Task Type: Independent Review / Architecture & Quality Gate
- Depends On:
  - `tasks/round-05/00-round-05-start.md`
  - `tasks/round-05/01-cursor-review-result-visualization-mock-v1.md`
  - `tasks/round-05/02-codex-review-result-visualization-validation.md`
  - `handoff/round-05/01-cursor-review-result-visualization-mock-v1-handoff.md`
  - `handoff/round-05/02-codex-review-result-visualization-validation-handoff.md`
- Expected Handoff:
  - `handoff/round-05/03-qoder-review-result-visualization-independent-review-handoff.md`

---

## 2. Background

Round 05 的目标是让 CodeReviewX 从“ReviewTask mock CRUD”推进到“可视化 review result demo”，并用 deterministic mock issues 收敛 `ReviewIssue` contract。

Cursor 已完成实现：

- backend typed `ReviewIssueResponse`;
- deterministic mock issues;
- frontend `ReviewIssue` typed model;
- detail 页面 summary panel;
- issue cards;
- severity/category/file path/line range/recommendation visualization;
- README 更新;
- tests 更新。

Codex 已完成 validation，结论为：

```text
Verdict: ACCEPTED_WITH_NOTES
```

Codex 确认：

1. backend/frontend typed issue contract 对齐；
2. `POST /api/review-tasks` / `GET /api/review-tasks` / `GET /api/review-tasks/{id}` / `GET /api/health` 均保持兼容；
3. `ApiResponse<T>` wrapper 未破坏；
4. backend tests 24 passed；
5. frontend typecheck/build/tests passed；
6. frontend tests 22 passed；
7. runtime curl 验证通过；
8. browser UI 验证通过；
9. README create request 示例已被 Codex 修正为真实字段 `repoUrl` / `prNumber`；
10. scope 未越界。

Codex 也记录了非阻塞 notes：

1. legacy `IssueType` / `IssueSource` enums 仍存在但已无引用；
2. backend `riskLevel` 与 frontend computed risk 存在潜在双 source-of-truth；
3. controller tests 使用 shared Spring context 和 in-memory state；
4. 本机 bare `java -version` 环境不稳定，但 Maven 使用 explicit `JAVA_HOME` 可通过。

本任务要求 Qoder 作为第三方独立审查者，从架构、质量、扩展性和交付门禁角度做最终 Round 05 审议。

---

## 3. Review Goal

Qoder 本轮目标不是重复 Cursor 实现，也不是简单重复 Codex 测试。

你需要判断：

1. Round 05 是否可以正式关闭；
2. 当前 `ReviewIssue` contract 是否足以支撑下一阶段；
3. 当前 frontend visualization 是否可作为产品验证基线；
4. Codex 的 `ACCEPTED_WITH_NOTES` verdict 是否合理；
5. 是否存在必须在 Round 05 末尾修复的阻塞问题；
6. 是否可以进入 Round 06；
7. Round 06 应该优先推进 persistence，还是先处理 contract/risk aggregation cleanup；
8. 下一阶段任务边界应该如何定义。

最终必须输出一个明确 architecture verdict。

---

## 4. Non-goals

本任务不是：

1. 新增功能；
2. 数据库持久化；
3. JPA/MyBatis 接入；
4. GitHub API integration；
5. Semgrep integration；
6. LLM integration；
7. ai-service integration；
8. diff viewer；
9. syntax highlighting；
10. issue filtering/sorting；
11. design system；
12. auth；
13. CI/CD；
14. deployment；
15. Round 06 implementation。

除非发现极小、明显且不修会误导最终结论的问题，否则不要修改代码。

---

## 5. Strict Scope Boundaries

### 5.1 Strictly Forbidden

本任务严禁：

1. 引入 database；
2. 引入 persistence layer；
3. 引入 MyBatis/JPA/Hibernate；
4. 创建 Entity/Repository/Mapper；
5. 创建 migration；
6. 调用 `ai-service`；
7. 创建 ai-service client；
8. 调用 GitHub API；
9. clone repository；
10. parse real repository code；
11. 执行 Semgrep；
12. 调用 LLM；
13. 调用 OpenAI/Anthropic/Gemini/local model；
14. 引入 Spring Security；
15. 引入 Swagger/OpenAPI；
16. 引入 Chart.js/ECharts/D3；
17. 引入 Ant Design/Material UI；
18. 引入 Redux/MobX/React Query/XState；
19. 迁移到 Next.js；
20. 实现 SSR；
21. 开始 Round 06；
22. 大规模重构 Cursor 或 Codex 的实现。

### 5.2 Allowed

允许：

1. 阅读代码；
2. 阅读任务文档；
3. 阅读 Cursor/Codex handoff；
4. 运行 tests；
5. 运行 typecheck/build；
6. 启动 backend/frontend；
7. curl 验证；
8. browser spot check；
9. grep / search dead code；
10. 做极小 README/typo 修正；
11. 做极小 test/comment 修正；
12. 输出 architecture review；
13. 输出下一阶段建议。

如果发现必须修改 production code 的问题，应优先在 handoff 中列为 blocking issue，而不是自行扩展实现。

---

## 6. Required Inputs

请至少阅读以下文件：

```text
tasks/round-05/00-round-05-start.md
tasks/round-05/01-cursor-review-result-visualization-mock-v1.md
tasks/round-05/02-codex-review-result-visualization-validation.md
handoff/round-05/01-cursor-review-result-visualization-mock-v1-handoff.md
handoff/round-05/02-codex-review-result-visualization-validation-handoff.md
```

并检查以下核心代码：

```text
backend-java/src/main/java/com/codereviewx/backend/review/dto/CreateReviewTaskRequest.java
backend-java/src/main/java/com/codereviewx/backend/review/dto/ReviewIssueResponse.java
backend-java/src/main/java/com/codereviewx/backend/review/dto/ReviewTaskResponse.java
backend-java/src/main/java/com/codereviewx/backend/review/model/ReviewTask.java
backend-java/src/main/java/com/codereviewx/backend/review/service/ReviewTaskService.java
backend-java/src/main/java/com/codereviewx/backend/review/controller/ReviewTaskController.java
backend-java/src/main/java/com/codereviewx/backend/common/ApiResponse.java
backend-java/src/main/java/com/codereviewx/backend/review/enums/IssueSeverity.java
backend-java/src/main/java/com/codereviewx/backend/review/enums/IssueCategory.java
backend-java/src/main/java/com/codereviewx/backend/review/enums/IssueType.java
backend-java/src/main/java/com/codereviewx/backend/review/enums/IssueSource.java

frontend/src/types/reviewTask.ts
frontend/src/api/reviewTaskApi.ts
frontend/src/components/ReviewTaskCreateForm.tsx
frontend/src/components/ReviewTaskDetail.tsx
frontend/src/components/ReviewTaskList.tsx
frontend/src/styles/app.css
frontend/src/test/ReviewTaskDetail.test.tsx

README.md
frontend/README.md
```

---

## 7. Review Dimensions

## 7.1 Round 05 Objective Fit

判断当前实现是否符合 Round 05 原始目标：

```text
优先呈现可视化 review result，快速验证实际使用效果；用 deterministic mock issues 收敛 ReviewIssue contract，但不做数据库、不做 ai-service、不做 GitHub、不做 Semgrep、不做 LLM。
```

请明确回答：

1. 是否已经形成可演示 review result UI；
2. 是否已经有稳定 mock issues；
3. 是否已完成 typed issue contract；
4. 是否仍然只是 mock/demo，没有冒充真实 code review；
5. 是否避免过早进入复杂后端能力。

---

## 7.2 Cursor Implementation Review

独立评估 Cursor 的实现质量。

重点检查：

1. backend DTO/model/service 是否职责清晰；
2. deterministic mock issue 生成位置是否合理；
3. 是否存在不必要复杂化；
4. 是否破坏原有 API；
5. frontend component 拆分是否适合当前阶段；
6. `IssueSummaryPanel` / `IssueCard` 是否可维护；
7. CSS 是否在 minimal polish 范围内；
8. tests 是否覆盖核心展示逻辑；
9. README 是否准确表达 current mock state；
10. 是否存在被 Cursor handoff 漏报的重要问题。

---

## 7.3 Codex Validation Review

独立评估 Codex 的 validation 是否充分。

请判断：

1. Codex 是否覆盖了 contract validation；
2. Codex 是否覆盖了 API request contract；
3. Codex 是否覆盖了 runtime curl；
4. Codex 是否覆盖了 browser UI；
5. Codex 是否覆盖了 README；
6. Codex 是否覆盖了 scope audit；
7. Codex minimal fixes 是否合理；
8. `ACCEPTED_WITH_NOTES` verdict 是否准确；
9. Codex 是否遗漏任何应该阻塞 Round 05 的问题。

---

## 7.4 ReviewIssue Contract Future-readiness

重点审查当前 contract 是否足以支撑后续真实 review pipeline。

当前 issue 字段：

```text
id
severity
category
filePath
startLine
endLine
title
description
recommendation
```

请分析它是否足以支撑：

1. Semgrep result mapping；
2. LLM review suggestion mapping；
3. GitHub diff comment mapping；
4. database persistence；
5. frontend issue cards；
6. issue filtering/sorting；
7. future code snippet/diff viewer；
8. issue lifecycle/status；
9. false-positive marking；
10. human override/comment。

注意：你不需要实现上述能力，只需要判断当前 contract 是否作为 Round 05 基线足够，以及哪些字段应该延后到 Round 06+。

建议重点思考是否未来需要：

```text
source
ruleId
confidence
fingerprint
status
createdAt
updatedAt
codeSnippet
diffHunk
```

请明确说明：这些字段是否应该现在加入，还是延后。

---

## 7.5 Risk Level Source-of-truth Review

这是本轮 Qoder 必须重点判断的问题。

当前状态：

1. backend `ReviewTask.riskLevel` 存在；
2. frontend summary panel 基于 issue severity 计算 risk；
3. 当前 mock 数据下二者一致；
4. 未来真实数据可能不一致。

请判断：

1. 这是 blocking issue 吗？
2. 是否必须在 Round 05 修？
3. 是否可以作为 Round 06 前置设计项？
4. 后续应由 backend 还是 frontend 作为 authoritative source？
5. 是否建议新增 backend `issueSummary` / `riskSummary`？
6. 是否建议 frontend 只展示 backend aggregate？
7. 是否建议保留 frontend computed fallback？

请给出明确架构建议。

---

## 7.6 Legacy Enum Review

Codex 已确认 `IssueType` / `IssueSource` 仍存在但无引用。

请独立检查：

1. 它们是否真的无引用；
2. 是否会误导后续开发；
3. 是否应该在 Round 05 末尾删除；
4. 是否应该留到下一轮 cleanup；
5. 如果删除，是否会造成不必要 churn。

请给出判断。

---

## 7.7 Test Quality Review

检查测试是否足以保障 Round 05 目标。

重点评估：

1. backend tests 是否覆盖 typed issue fields；
2. backend tests 是否覆盖 create/list/detail；
3. backend tests 是否覆盖 deterministic issue behavior；
4. frontend tests 是否覆盖 issue summary；
5. frontend tests 是否覆盖 issue cards；
6. frontend tests 是否覆盖 empty/loading/error；
7. risk-level calculation 是否有测试；
8. API wrapper 是否有测试；
9. 是否存在 shared in-memory state 风险；
10. 是否需要立刻补测试。

如果运行测试，记录命令和结果。  
如果不运行，必须说明为什么，并基于 Codex 结果与代码审查给出判断。

---

## 7.8 Runtime/Product Experience Review

如条件允许，请启动 backend/frontend 做 spot check。

重点从产品经理和架构师视角判断：

1. demo label 是否足够清楚；
2. review result summary 是否一眼可理解；
3. issue cards 是否信息层级清晰；
4. recommendation 是否突出；
5. 用户是否可能误以为这是“真实代码审查”；
6. create/list/detail flow 是否顺；
7. backend unavailable/error state 是否可接受；
8. 当前 UI 是否足以支持产品迭代反馈；
9. 是否需要在 Round 05 末尾调整文案或布局；
10. 是否可以将 UI 作为 Round 06 的展示基线。

---

## 7.9 Documentation Review

检查 README 是否清晰区分：

1. current implemented state；
2. deterministic mock result；
3. no real code analysis；
4. no GitHub API；
5. no Semgrep；
6. no LLM/AI service；
7. in-memory storage；
8. planned MVP/future architecture；
9. actual create request fields；
10. frontend `VITE_API_BASE_URL` 配置。

尤其关注 README 是否仍可能让读者误解当前系统已经具备真实 review 能力。

---

## 7.10 Scope Boundary Review

确认没有越界引入：

```text
Database
Persistence
MyBatis/JPA/Hibernate
ai-service call
GitHub API call
Semgrep execution
LLM call
Repository clone
Real code parsing
Chart/component library
Complex state management
Round 06 implementation
```

如果发现任何越界，必须标记为 blocking issue。

---

## 8. Optional Commands

如条件允许，建议运行：

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
```

或：

```bash
cd backend-java
mvn test
```

Frontend：

```bash
cd frontend
npm install
npm run typecheck
npm run build
npm test
```

Runtime：

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run
```

```bash
cd frontend
npm run dev -- --host 127.0.0.1
```

API spot check：

```bash
curl http://localhost:8080/api/health
```

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{
    "repoUrl": "https://github.com/example/qoder-demo",
    "prNumber": 11
  }'
```

```bash
curl http://localhost:8080/api/review-tasks
```

```bash
curl http://localhost:8080/api/review-tasks/1
```

如果不能运行全部命令，可以选择重点运行，也可以基于 Cursor/Codex handoff + code review 完成独立审查，但必须说明限制。

---

## 9. Verdict Standard

最终必须给出以下之一：

```text
ROUND_05_ACCEPTED
ROUND_05_ACCEPTED_WITH_NOTES
ROUND_05_CHANGES_REQUIRED
```

### 9.1 ROUND_05_ACCEPTED

适用于：

1. Cursor implementation 合格；
2. Codex validation 充分；
3. 无 blocking issue；
4. 无需要 Round 05 末尾处理的设计风险；
5. 可以直接生成 Round 06 start task。

### 9.2 ROUND_05_ACCEPTED_WITH_NOTES

适用于：

1. 核心目标完成；
2. no blocking issues；
3. 存在非阻塞 notes；
4. 建议进入 Round 06 前在 start doc 中承接 notes；
5. 不需要退回 Cursor/Codex。

这是当前最可能的结果。

### 9.3 ROUND_05_CHANGES_REQUIRED

适用于：

1. contract 仍不一致；
2. issues 仍是 weak type；
3. frontend detail visualization 不可用；
4. tests 失败且非环境问题；
5. create/list/detail API broken；
6. README 严重误导；
7. scope violation；
8. riskLevel 双来源已经造成 UI/contract 实际冲突；
9. mock issues 冒充真实 review；
10. 必须在 Round 05 内修复后才能进入下一阶段。

---

## 10. Expected Handoff Structure

完成后生成：

```text
handoff/round-05/03-qoder-review-result-visualization-independent-review-handoff.md
```

handoff 必须包含以下章节。

---

### 10.1 Summary

说明 Qoder 审查了什么，是否运行测试/runtime，是否修改代码。

---

### 10.2 Verdict

必须写：

```text
Verdict: ROUND_05_ACCEPTED
```

或：

```text
Verdict: ROUND_05_ACCEPTED_WITH_NOTES
```

或：

```text
Verdict: ROUND_05_CHANGES_REQUIRED
```

并说明理由。

---

### 10.3 Cursor Implementation Assessment

评价 Cursor 实现质量：

1. backend；
2. frontend；
3. tests；
4. README；
5. scope control。

---

### 10.4 Codex Validation Assessment

评价 Codex 验证质量：

1. 是否充分；
2. verdict 是否合理；
3. minimal fixes 是否合理；
4. 是否遗漏关键问题。

---

### 10.5 ReviewIssue Contract Assessment

评估当前 contract：

1. 当前字段是否足够；
2. 支撑后续 Semgrep/LLM/GitHub/persistence 的能力；
3. 哪些字段应延后；
4. 是否需要本轮补字段；
5. contract 演进建议。

---

### 10.6 Risk Level Source-of-truth Assessment

必须单独写：

1. 当前状态；
2. 是否 blocking；
3. 是否 Round 05 修；
4. 推荐 authoritative source；
5. 是否建议 backend aggregate；
6. frontend fallback 策略；
7. Round 06 建议。

---

### 10.7 Legacy Enum Assessment

说明：

1. `IssueType` / `IssueSource` 是否仍存在；
2. 是否仍被引用；
3. 是否阻塞；
4. 是否建议删除；
5. 删除时机。

---

### 10.8 Test and Runtime Assessment

说明：

1. 是否运行 backend tests；
2. 是否运行 frontend typecheck/build/tests；
3. 是否 runtime spot check；
4. 结果；
5. 测试质量评价；
6. known limitations。

---

### 10.9 Product Experience Assessment

从产品体验角度说明：

1. demo label；
2. summary panel；
3. issue cards；
4. information hierarchy；
5. error/empty states；
6. 是否足够支撑产品反馈。

---

### 10.10 Documentation Assessment

说明 README 是否准确，是否还有误导点。

---

### 10.11 Scope Audit

逐项确认：

```text
Database introduced:                  YES/NO
Persistence introduced:               YES/NO
MyBatis/JPA introduced:               YES/NO
ai-service called:                    YES/NO
GitHub API called:                    YES/NO
Semgrep executed:                     YES/NO
LLM called:                           YES/NO
Repository cloned:                    YES/NO
Real code parsed:                     YES/NO
Chart/component library introduced:   YES/NO
Complex state management introduced:  YES/NO
Existing endpoints changed:           YES/NO
ApiResponse wrapper changed:          YES/NO
Round 06 started:                     YES/NO
```

任何 YES 必须解释。  
scope violation 通常意味着 `ROUND_05_CHANGES_REQUIRED`。

---

### 10.12 Blocking Issues

如果没有，写：

```text
Blocking issues: None known.
```

如果有，明确：

1. issue；
2. evidence；
3. required fix；
4. recommended assignee；
5. whether Round 05 can close。

---

### 10.13 Non-blocking Notes

至少覆盖：

1. riskLevel dual source-of-truth；
2. legacy enum cleanup；
3. shared in-memory state test risk；
4. any responsive/UI polish note；
5. README planned/current state distinction if relevant。

---

### 10.14 Recommended Next Step

必须明确建议：

```text
Proceed to Round 06 start document
```

或：

```text
Return to Cursor for fixes
```

或：

```text
Return to Codex for further validation
```

如果建议进入 Round 06，请给出 Round 06 优先方向。

推荐候选方向：

1. persistence-first：
   - introduce database;
   - persist ReviewTask and ReviewIssue;
   - keep mock review generation;
   - preserve API contract;
   - prepare for real review integration.

2. risk-contract-first：
   - add backend issue summary/risk aggregation;
   - remove frontend risk source ambiguity;
   - cleanup legacy enums;
   - then persistence.

3. ai-integration-first：
   - not recommended immediately unless persistence/contract boundaries are stable.

请说明推荐理由。

---

## 11. Acceptance Criteria

Qoder handoff 完成后必须满足：

- [ ] 已阅读 Cursor handoff；
- [ ] 已阅读 Codex handoff；
- [ ] 已检查关键 backend contract；
- [ ] 已检查关键 frontend contract；
- [ ] 已审查 ReviewIssue future-readiness；
- [ ] 已审查 riskLevel source-of-truth；
- [ ] 已审查 legacy enums；
- [ ] 已审查 tests；
- [ ] 已审查 product experience；
- [ ] 已审查 documentation；
- [ ] 已审查 scope boundary；
- [ ] 已给出 architecture verdict；
- [ ] 已给出是否关闭 Round 05 的建议；
- [ ] 已给出 Round 06 建议方向；
- [ ] 已生成 Qoder handoff。

---

## 12. Final Instruction

请严格作为独立架构与质量门禁审查者执行本任务。

本轮核心原则：

```text
Round 05 不追求真实 review 能力；它追求 typed ReviewIssue contract、deterministic mock issues 和可演示的 review result visualization。Qoder 的职责是判断该阶段是否足够稳，可以关闭并进入下一阶段。
```

完成后只输出：

```text
handoff/round-05/03-qoder-review-result-visualization-independent-review-handoff.md
```