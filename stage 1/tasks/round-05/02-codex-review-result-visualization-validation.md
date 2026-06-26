# tasks/round-05/02-codex-review-result-visualization-validation.md

# Codex Task: Round 05 Review Result Visualization Validation

## 1. Task Metadata

- Project: CodeReviewX
- Round: Round 05
- Task ID: `02-codex-review-result-visualization-validation`
- Executor: Codex
- Task Type: Validation / Independent Build & Contract Review
- Depends On:
  - `tasks/round-05/00-round-05-start.md`
  - `tasks/round-05/01-cursor-review-result-visualization-mock-v1.md`
  - `handoff/round-05/01-cursor-review-result-visualization-mock-v1-handoff.md`
- Expected Handoff:
  - `handoff/round-05/02-codex-review-result-visualization-validation-handoff.md`

---

## 2. Background

Round 05 的目标是优先形成可视化 review result，用 deterministic mock issues 收敛 `ReviewIssue` contract，并快速验证实际产品效果。

Cursor 已完成第一阶段实现，并提交 handoff：

```text
handoff/round-05/01-cursor-review-result-visualization-mock-v1-handoff.md
```

Cursor 声明已完成：

1. backend typed `ReviewIssueResponse` contract；
2. backend deterministic mock issue generation；
3. `ReviewTask.issues` 从弱类型改为 typed issue list；
4. frontend `ReviewIssue` TypeScript types；
5. frontend detail 页面 summary panel；
6. frontend issue cards；
7. severity/category/file path/line range/recommendation 展示；
8. backend tests；
9. frontend typecheck/build/tests；
10. README 更新；
11. scope boundary compliance。

本任务不是继续新增功能，而是对 Cursor 的实现进行独立验证、复现、审计和必要的小修正建议。

---

## 3. Validation Goal

本任务目标是判断 Cursor Round 05 实现是否达到可进入下一阶段的质量标准。

重点验证：

1. backend/frontend typed issue contract 是否真实一致；
2. API endpoints 与 `ApiResponse<T>` wrapper 是否未破坏；
3. deterministic mock issues 是否真实稳定；
4. frontend review result visualization 是否符合任务目标；
5. tests 是否可独立复现通过；
6. README 与实际 API contract 是否一致；
7. 是否存在 scope violation；
8. 是否存在阻塞性 defect；
9. 是否存在需要 Qoder 独立审查的风险点；
10. 给出明确 validation verdict。

---

## 4. Non-goals

本任务不是：

1. 数据库持久化；
2. JPA/MyBatis 接入；
3. 真实 AI review；
4. ai-service integration；
5. GitHub API integration；
6. Semgrep integration；
7. LLM integration；
8. diff viewer；
9. syntax highlighting；
10. issue filtering/sorting；
11. design system；
12. auth；
13. deployment；
14. Round 06 implementation。

除非发现阻塞性问题，否则不要做大规模重构。

---

## 5. Strict Scope Boundaries

### 5.1 Strictly Forbidden

本任务严禁：

1. 引入数据库；
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
19. 迁移 Next.js；
20. 实现 SSR；
21. 开始 Round 06。

### 5.2 Allowed

本任务允许：

1. 运行测试；
2. 阅读代码；
3. 启动 backend/frontend；
4. 发起本地 curl；
5. 检查 API response；
6. 检查 frontend 页面行为；
7. 检查 README；
8. 检查 dead code；
9. 修改明显小问题；
10. 修正测试不稳定；
11. 修正文档与实际 API 不一致；
12. 修正非侵入式类型不一致；
13. 记录风险与建议。

如果需要修改代码，必须保持 minimal patch，不得扩展新功能。

---

## 6. Required Validation Areas

## 6.1 Repository State Review

先阅读以下文件：

```text
handoff/round-05/01-cursor-review-result-visualization-mock-v1-handoff.md
tasks/round-05/00-round-05-start.md
tasks/round-05/01-cursor-review-result-visualization-mock-v1.md
```

然后检查 Cursor handoff 中列出的变更文件是否真实存在：

```text
backend-java/src/main/java/com/codereviewx/backend/review/enums/IssueCategory.java
backend-java/src/main/java/com/codereviewx/backend/review/dto/ReviewIssueResponse.java
backend-java/src/main/java/com/codereviewx/backend/review/model/ReviewTask.java
backend-java/src/main/java/com/codereviewx/backend/review/service/ReviewTaskService.java
backend-java/src/test/java/com/codereviewx/backend/review/service/ReviewTaskServiceTest.java
backend-java/src/test/java/com/codereviewx/backend/review/controller/ReviewTaskControllerTest.java
frontend/src/types/reviewTask.ts
frontend/src/components/ReviewTaskDetail.tsx
frontend/src/styles/app.css
frontend/src/test/ReviewTaskDetail.test.tsx
frontend/README.md
README.md
```

记录：

1. 是否全部存在；
2. 是否存在 handoff 未提及但实际被修改的相关文件；
3. 是否存在不合理的额外改动。

---

## 6.2 Backend Contract Validation

验证 backend 是否存在明确 typed issue contract。

必须检查：

### `ReviewIssueResponse`

字段应至少包含：

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

检查字段类型：

```text
id: String
severity: IssueSeverity
category: IssueCategory
filePath: String
startLine: Integer
endLine: Integer
title: String
description: String
recommendation: String
```

如实现略有不同，判断是否仍然满足 Round 05 contract。

### `IssueSeverity`

验证枚举值：

```text
LOW
MEDIUM
HIGH
```

### `IssueCategory`

验证枚举值：

```text
BUG
SECURITY
PERFORMANCE
MAINTAINABILITY
STYLE
TEST
```

### `ReviewTask`

验证：

```text
issues: List<ReviewIssueResponse>
```

不应再是：

```text
List<Object>
Object[]
unknown-like structure
String JSON blob
Map<String, Object>
```

### `ReviewTaskResponse`

验证 response DTO 是否返回 typed issue list。

重点确认：

1. create API 返回 issues；
2. list API 返回 issues；
3. detail API 返回 issues；
4. enum JSON serialization 是字符串；
5. `ApiResponse<T>` wrapper 未改变；
6. endpoints 未改变。

---

## 6.3 Frontend Contract Validation

检查 frontend type 定义。

必须验证：

```typescript
export type IssueSeverity = 'LOW' | 'MEDIUM' | 'HIGH';

export type IssueCategory =
  | 'BUG'
  | 'SECURITY'
  | 'PERFORMANCE'
  | 'MAINTAINABILITY'
  | 'STYLE'
  | 'TEST';

export interface ReviewIssue {
  id: string;
  severity: IssueSeverity;
  category: IssueCategory;
  filePath: string;
  startLine: number;
  endLine: number | null;
  title: string;
  description: string;
  recommendation: string;
}
```

如果 `endLine` 当前是 `number` 而非 `number | null`，请评估 backend `Integer endLine` 是否可能返回 null。

建议：

- 如果 backend mock 始终返回 number，但 schema 未来可能为空，可以接受；
- 如果 backend 或 tests 存在 null case，则 frontend 必须支持 `number | null`；
- 结论必须写入 handoff。

验证：

```text
ReviewTask.issues: ReviewIssue[]
```

不应仍为：

```text
unknown[]
any[]
object[]
```

---

## 6.4 API Compatibility Validation

验证现有 API 未破坏。

必须检查 backend routes 是否仍为：

```text
GET  /api/health
POST /api/review-tasks
GET  /api/review-tasks
GET  /api/review-tasks/{id}
```

如果项目已有其它 endpoint，可以保留，但不得破坏上述 endpoint。

验证 `ApiResponse<T>` wrapper 仍类似：

```json
{
  "success": true,
  "message": "OK",
  "data": {}
}
```

不得改变为裸对象返回，除非原项目本身就是裸对象。

---

## 6.5 Request Contract Consistency Validation

重点检查 create ReviewTask request 字段。

历史任务文档中可能出现过不同示例：

```json
{
  "repositoryUrl": "https://github.com/example/demo",
  "branch": "main"
}
```

而当前 handoff runtime response 显示项目可能实际使用：

```json
{
  "repoUrl": "https://github.com/example/demo",
  "prNumber": 1
}
```

你必须确认真实 request DTO。

检查：

1. backend create request DTO 字段名；
2. frontend form submit payload；
3. README curl 示例；
4. test payload；
5. handoff 示例。

如果 README 或任务文档中的示例与真实 API 不一致，请做 minimal README 修正，并在 handoff 中记录。

不要为了适配旧文档而大改 API，除非实际代码已破坏。

---

## 6.6 Deterministic Mock Issue Validation

验证 `ReviewTaskService` 或等效 service 是否每次 create task 都生成 deterministic mock issues。

必须检查：

1. 每次 create 是否返回固定数量 issues；
2. issue 数量是否为 3 到 5；
3. issue 是否覆盖 HIGH/MEDIUM/LOW；
4. issue 是否包含 SECURITY/MAINTAINABILITY/TEST 等 category；
5. issue 是否包含 file path；
6. issue 是否包含 startLine/endLine；
7. issue 是否包含 title/description/recommendation；
8. issue 内容是否明确是 demo/mock；
9. issue 是否不依赖真实 repository code；
10. 是否没有 GitHub/Semgrep/LLM/ai-service 调用。

建议通过单元测试或本地 curl 验证两次 create response 的 issue shape 稳定性。

---

## 6.7 Risk Level Consistency Validation

Cursor handoff 中存在一个需要重点复核的问题：

1. backend `createTask` 可能设置 `riskLevel = HIGH`；
2. frontend 可能基于 issue severity 计算 `riskLevel`。

你必须检查是否存在 risk level 双 source-of-truth 问题。

验证：

1. frontend detail metadata 是否展示 backend `task.riskLevel`；
2. summary panel 是否展示 frontend computed risk；
3. 如果二者同时存在，是否文案清楚；
4. 是否可能出现 backend risk 与 computed risk 不一致；
5. 是否需要命名为 `computedRiskLevel` 或增加注释；
6. 当前是否为阻塞问题。

建议标准：

- 如果当前 mock issues 与 backend risk 一致，可以判为非阻塞；
- 如果页面重复展示两个不一致 risk，则需要修；
- 如果代码结构隐藏风险但当前不影响使用，记录为 non-blocking note。

---

## 6.8 Frontend Visualization Validation

检查 `ReviewTaskDetail` 是否真实展示以下内容：

1. ReviewTask basic metadata；
2. mock/demo result label；
3. issue summary panel；
4. total issue count；
5. high/medium/low count；
6. risk level；
7. issue cards；
8. severity badge；
9. category badge；
10. file path；
11. line number or line range；
12. title；
13. description；
14. recommendation block；
15. empty issues fallback。

检查页面对以下状态是否合理：

1. loading；
2. error；
3. selected task exists；
4. task is null / no selected task；
5. issues empty；
6. issues populated；
7. backend unavailable。

不要引入新的 UI library。

---

## 6.9 Test Reproducibility Validation

必须独立运行 backend tests：

```bash
cd backend-java
mvn test
```

如果项目使用 wrapper，也可以运行：

```bash
cd backend-java
./mvnw test
```

必须独立运行 frontend checks：

```bash
cd frontend
npm install
npm run typecheck
npm run build
npm test
```

如果 `package.json` 中 test script 是 `npm run test`，按实际项目执行。

记录每个命令：

```text
Command:
Result:
Evidence:
```

如果失败：

1. 判断是否环境问题；
2. 判断是否 Cursor 实现问题；
3. 尽可能做 minimal fix；
4. 修复后重新运行；
5. 在 handoff 中完整记录。

---

## 6.10 Runtime Verification

必须尝试本地运行 backend：

```bash
cd backend-java
mvn spring-boot:run
```

或：

```bash
cd backend-java
./mvnw spring-boot:run
```

验证 health：

```bash
curl http://localhost:8080/api/health
```

创建 task。

注意：必须使用实际 request DTO 字段。  
如果真实字段是 `repoUrl/prNumber`，使用：

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{
    "repoUrl": "https://github.com/example/demo",
    "prNumber": 1
  }'
```

如果真实字段是 `repositoryUrl/branch`，使用真实字段。

验证 create response：

1. `success=true`；
2. `data.issues` 是数组；
3. issue 数量符合预期；
4. issue 字段完整；
5. severity/category 是字符串；
6. summary/riskLevel 合理；
7. response wrapper 未破坏。

验证 list：

```bash
curl http://localhost:8080/api/review-tasks
```

验证 detail：

```bash
curl http://localhost:8080/api/review-tasks/{id}
```

必须记录关键 response 摘要。

---

## 6.11 Frontend Runtime Verification

启动 frontend：

```bash
cd frontend
npm run dev
```

浏览器验证：

1. frontend 可以打开；
2. backend health 显示正常；
3. create ReviewTask 成功；
4. list 页面显示新 task；
5. detail 页面可打开；
6. summary panel 可见；
7. mock/demo label 可见；
8. total/high/medium/low counts 正确；
9. risk level 可见；
10. issue cards 可见；
11. severity/category badges 可见；
12. file path + line range 可见；
13. description 可见；
14. recommendation block 可见；
15. empty issues fallback 仍可通过 test 或 mock render 验证；
16. backend unavailable error state 仍可显示。

如果无法进行浏览器验证，必须说明原因，并至少通过 tests + build + code review 补充验证。

---

## 6.12 README Validation

检查根目录 README 和 frontend README。

必须确认 README 说明：

1. current review issues are deterministic mock data；
2. no real repository code is analyzed；
3. no GitHub API is called；
4. no Semgrep is executed；
5. no LLM / AI service is called；
6. data remains in-memory；
7. backend restart may lose created tasks；
8. frontend displays demo review result for product validation；
9. API base URL 配置准确；
10. create task curl 示例与实际 backend request DTO 一致。

尤其检查是否存在错误说明：

```text
VITE_API_BASE_URL=/api
```

如果当前 frontend 实际会拼接 `/api/review-tasks`，则 README 应避免导致：

```text
/api/api/review-tasks
```

如有不一致，做 minimal README 修正。

---

## 6.13 Dead Code / Legacy Enum Review

Cursor handoff 提到：

```text
IssueType and IssueSource enums remain in the codebase but are no longer referenced.
```

你必须验证：

1. `IssueType` 是否仍存在；
2. `IssueSource` 是否仍存在；
3. 是否还有引用；
4. 是否造成编译 warning 或测试风险；
5. 是否需要本轮删除。

建议标准：

- 如果完全无引用，但不影响编译和测试：记录为 non-blocking cleanup；
- 如果仍被错误引用或造成 contract confusion：做 minimal fix；
- 如果 README 或 docs 仍提到旧 enum：修正文档。

---

## 7. Allowed Minimal Fix Policy

如果发现小问题，可以直接修复。

允许修复：

1. README curl 示例字段错误；
2. frontend type 中 `endLine` nullability 不一致；
3. test assertion 不稳定；
4. typo；
5. dead import；
6. UI 文案小错误；
7. risk level 命名不清晰但可小改；
8. demo/mock label 不明显；
9. empty fallback test 缺失；
10. obvious contract mismatch。

不允许修复：

1. 大规模重构；
2. 改 API 设计；
3. 新增数据库；
4. 新增真实 review pipeline；
5. 新增外部服务；
6. 新增 UI framework；
7. 新增复杂状态管理；
8. 开始 Round 06。

如果发现重大问题，不要强行大改；应将 verdict 标记为 `CHANGES_REQUIRED`，并明确列出 required changes。

---

## 8. Verdict Standard

最终必须给出三种之一：

```text
ACCEPTED
ACCEPTED_WITH_NOTES
CHANGES_REQUIRED
```

### 8.1 ACCEPTED

仅当：

1. 所有 tests/checks/runtime verification 通过；
2. backend/frontend contract 完全一致；
3. no blocking issues；
4. docs accurate；
5. no scope violation；
6. only trivial notes or no notes。

### 8.2 ACCEPTED_WITH_NOTES

适用于：

1. 核心功能完成；
2. tests 通过；
3. runtime 可用；
4. contract 基本一致；
5. 存在非阻塞问题，例如：
   - unused legacy enums；
   - in-memory test state shared；
   - riskLevel source-of-truth ambiguity 但当前不影响；
   - responsive polish deferred；
   - docs 小修后通过。

这是本轮较可能的目标状态。

### 8.3 CHANGES_REQUIRED

适用于：

1. backend tests fail 且非环境问题；
2. frontend typecheck/build/tests fail 且非环境问题；
3. create/list/detail API broken；
4. `issues` 仍是 weak type；
5. frontend 仍是 `unknown[]`/`any[]`；
6. issue cards 不可见；
7. deterministic mock issues 不存在；
8. response wrapper 被破坏；
9. scope violation；
10. README 严重误导；
11. runtime 基本不可用。

---

## 9. Expected Handoff Structure

完成后生成：

```text
handoff/round-05/02-codex-review-result-visualization-validation-handoff.md
```

handoff 必须包含以下章节。

---

### 9.1 Summary

说明你验证了什么，是否做了 minimal fixes。

---

### 9.2 Verdict

必须写：

```text
Verdict: ACCEPTED
```

或：

```text
Verdict: ACCEPTED_WITH_NOTES
```

或：

```text
Verdict: CHANGES_REQUIRED
```

并给出 3 到 8 条理由。

---

### 9.3 Files Inspected

列出检查过的关键文件。

---

### 9.4 Files Changed

如果无修改，写：

```text
Files changed by Codex: None.
```

如果有修改，列出具体文件与修改原因。

---

### 9.5 Backend Contract Validation

说明：

1. `ReviewIssueResponse` 字段；
2. `IssueSeverity` enum；
3. `IssueCategory` enum；
4. `ReviewTask.issues` 类型；
5. create/list/detail response 是否返回 typed issues；
6. `ApiResponse<T>` wrapper 是否保持。

---

### 9.6 Frontend Contract Validation

说明：

1. `ReviewIssue` TypeScript type；
2. `ReviewTask.issues` 类型；
3. frontend/backend field alignment；
4. `endLine` nullability 判断；
5. 是否存在 `any[]`/`unknown[]` 遗留。

---

### 9.7 API Request Contract

说明真实 create request DTO。

必须明确：

```text
Actual create request fields:
- ...
```

并说明 README/curl/frontend/test 是否一致。

---

### 9.8 Mock Issue Validation

说明：

1. issue 数量；
2. severity 分布；
3. category 分布；
4. deterministic 行为；
5. 是否 demo/mock；
6. 是否未调用真实外部服务。

---

### 9.9 Frontend Visualization Validation

说明页面是否展示：

1. summary panel；
2. demo label；
3. issue counts；
4. risk level；
5. issue cards；
6. severity/category badges；
7. file path + line range；
8. recommendation block；
9. empty fallback；
10. loading/error states。

---

### 9.10 Risk Level Analysis

必须单独说明：

1. backend risk level；
2. frontend computed risk level；
3. 是否存在双 source-of-truth；
4. 当前是否阻塞；
5. 后续建议。

---

### 9.11 Commands Run

记录实际执行命令和结果。

格式：

```text
cd backend-java
mvn test
Result: PASS/FAIL
Evidence: ...

cd frontend
npm install
Result: PASS/FAIL
Evidence: ...

npm run typecheck
Result: PASS/FAIL
Evidence: ...

npm run build
Result: PASS/FAIL
Evidence: ...

npm test
Result: PASS/FAIL
Evidence: ...
```

---

### 9.12 Runtime Verification

记录：

1. backend startup；
2. health response；
3. create task response summary；
4. list response summary；
5. detail response summary；
6. frontend startup；
7. browser/manual verification summary；
8. any runtime limitations。

---

### 9.13 Scope Audit

必须逐项确认：

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

任何 YES 都必须解释。  
如果出现 scope violation，通常应判定为 `CHANGES_REQUIRED`。

---

### 9.14 Known Issues

分为：

```text
Blocking issues:
- ...

Non-blocking notes:
- ...

Recommended follow-up:
- ...
```

如果没有 blocking issue，写：

```text
Blocking issues: None known.
```

---

## 10. Acceptance Criteria

本 Codex validation task 完成后必须满足：

- [ ] 已阅读 Cursor handoff；
- [ ] 已检查 Cursor 声明的关键变更文件；
- [ ] 已验证 backend typed issue contract；
- [ ] 已验证 frontend typed issue contract；
- [ ] 已验证 create/list/detail API；
- [ ] 已验证 deterministic mock issues；
- [ ] 已验证 frontend review result visualization；
- [ ] 已验证 empty issues fallback；
- [ ] 已验证 risk level source-of-truth；
- [ ] 已验证 README 与实际 API 一致；
- [ ] 已检查 legacy `IssueType` / `IssueSource`；
- [ ] 已运行 backend tests；
- [ ] 已运行 frontend typecheck；
- [ ] 已运行 frontend build；
- [ ] 已运行 frontend tests；
- [ ] 已尽量完成 runtime verification；
- [ ] 已确认 scope boundary；
- [ ] 已生成 Codex handoff；
- [ ] 已给出明确 verdict。

---

## 11. Final Instruction

请严格按照本任务执行独立验证。

本任务核心原则：

```text
验证 Cursor 的 Round 05 实现是否真实、可运行、contract 一致、scope 未越界；只做必要小修，不扩展新功能，不进入 Round 06。
```

完成后只输出：

```text
handoff/round-05/02-codex-review-result-visualization-validation-handoff.md
```