# tasks/round-09/00-round-09-start.md

# Round 09 Start: Xiaomi MiMo AI Review Provider v1

## 1. Round Metadata

- Project: CodeReviewX
- Round: Round 09
- Theme: Xiaomi MiMo AI Review Provider v1
- Task Type: Architecture-guided AI provider implementation round
- Primary Goal: 在 Round 08 pipeline/provider 架构之上，接入 Xiaomi MiMo 作为 CodeReviewX 的首个真实 AI Provider，并开始按标准 agent 构建流程呈现 agent 的结构、链路、输入、执行、输出与 fallback 机制。
- Previous Round:
  - Round 08: Review Pipeline Orchestrator Skeleton
- Previous Final Verdict:
  - `ROUND_08_CLOSED_READY_FOR_ROUND_09`
- First Task To Generate:
  - `tasks/round-09/01-cursor-xiaomi-mimo-ai-review-provider-v1.md`

---

## 2. Strategic Context

CodeReviewX 已进入 agent 能力构建阶段。

Round 08 已完成：

```text
ReviewTaskService
  -> ReviewPipelineService
      -> ReviewProvider
          -> MockReviewProvider
      -> ReviewFinding[]
  -> persist ReviewIssueEntity
  -> return unchanged API response
```

Round 09 不再继续做纯 mock 架构铺垫，而是要在这个 provider seam 后面接入确定的 AI Provider：

```text
Xiaomi MiMo
```

本轮必须开始按照标准 agent 构建方式推进，并且在后续 handoff 中持续向用户展示：

1. agent 构成结构；
2. agent 输入；
3. agent 执行链路；
4. provider 调用边界；
5. prompt 构建方式；
6. structured output 解析方式；
7. fallback 决策；
8. 最终 findings 如何落库；
9. 前端如何展示 agent 结果；
10. 当前真实能力与模拟能力边界。

---

## 3. Secret Handling Requirement

用户已指定 AI Provider 为：

```text
Xiaomi MiMo
```

用户也已提供 Xiaomi MiMo API key。

但是：

```text
不得把 API key 写入任何代码、任务文档、README、handoff、测试快照、日志、提交信息或前端文件。
```

必须通过环境变量读取：

```text
MIMO_API_KEY
```

可选环境变量：

```text
MIMO_BASE_URL
MIMO_MODEL
CODEREVIEWX_REVIEW_PROVIDER
```

推荐默认值：

```properties
codereviewx.review.provider=mock
codereviewx.ai.mimo.base-url=https://api.xiaomimimo.com/v1
codereviewx.ai.mimo.model=mimo-v2.5-pro
```

如果官方模型名或 endpoint 在实际环境中不同，Cursor 必须通过配置项实现可覆盖，不要硬编码不可变值。

严禁：

1. 将 key 写入 `application.yml`；
2. 将 key 写入 `application.properties`；
3. 将 key 写入 README；
4. 将 key 写入测试 fixture；
5. 将 key 打印到日志；
6. 将 key 返回给前端；
7. 将 key 放入 handoff。

允许：

```bash
export MIMO_API_KEY="..."
```

或本地 `.env`，但 `.env` 不得提交。

---

## 4. Current Project State

截至 Round 08，系统已经具备：

1. Spring Boot backend；
2. React + TypeScript + Vite frontend；
3. review task create/list/detail API；
4. frontend create/list/detail flow；
5. persisted `ReviewTaskEntity`；
6. persisted `ReviewIssueEntity`；
7. file-based H2 runtime database；
8. in-memory H2 test database；
9. backend-computed `issueSummary`；
10. `riskLevel == issueSummary.riskLevel` invariant；
11. deterministic `MockReviewProvider`；
12. internal `ReviewPipelineService`；
13. internal `ReviewProvider` abstraction；
14. internal `ReviewContext`；
15. internal `ReviewFinding`；
16. internal `ReviewProviderResult`；
17. restart persistence verified；
18. browser smoke verified；
19. backend tests passing；
20. frontend tests passing；
21. README documents current vs planned capability；
22. no real GitHub call；
23. no repository clone；
24. no Semgrep；
25. no LLM provider yet；
26. no `ai-service` integration yet。

Round 09 must introduce the first real AI provider path.

---

## 5. Round 09 Primary Goal

Implement:

```text
Xiaomi MiMo AI Review Provider v1
```

Target internal behavior:

```text
ReviewTaskService
  -> ReviewPipelineService
      -> ReviewProviderSelector / configured provider
          -> MockReviewProvider
          OR
          -> XiaomiMiMoReviewProvider
              -> ReviewPromptBuilder
              -> XiaomiMiMoClient
              -> XiaomiMiMoStructuredOutputParser
              -> ReviewFinding[]
  -> persist ReviewFinding as ReviewIssueEntity
  -> compute issueSummary from persisted issues
  -> return unchanged ReviewTaskResponse
```

Round 09 should make the backend capable of calling Xiaomi MiMo when configured, while preserving local/mock mode as the safe default.

---

## 6. Round 09 Product Positioning

Round 09 should be described as:

```text
CodeReviewX now has a configurable Xiaomi MiMo AI provider path.
```

Do not overclaim:

```text
CodeReviewX fully reviews real GitHub pull requests.
```

Because unless PR diff ingestion is implemented, the AI provider may still only receive `repoUrl`, `prNumber`, and limited synthetic/contextual prompt input.

Correct positioning:

```text
Round 09 introduces the Xiaomi MiMo provider integration and agent execution skeleton.
Realistic PR/diff context enrichment is planned for a following round.
```

---

## 7. Agent Construction Model

From Round 09 onward, CodeReviewX should be described and built as a review agent with the following structure.

### 7.1 Agent Components

```text
CodeReviewX Review Agent
├── Input Layer
│   └── ReviewContext
│       ├── taskId
│       ├── repoUrl
│       ├── prNumber
│       └── createdAt
├── Orchestration Layer
│   └── ReviewPipelineService
├── Provider Selection Layer
│   └── ReviewProviderSelector / configuration-driven provider wiring
├── Provider Layer
│   ├── MockReviewProvider
│   └── XiaomiMiMoReviewProvider
├── Prompt Layer
│   └── ReviewPromptBuilder
├── Model Client Layer
│   └── XiaomiMiMoClient
├── Output Parsing Layer
│   └── XiaomiMiMoFindingParser
├── Normalization Layer
│   └── ReviewFinding
├── Persistence Layer
│   ├── ReviewTaskEntity
│   └── ReviewIssueEntity
└── Presentation Layer
    └── Existing ReviewTaskResponse / frontend issue cards
```

### 7.2 Agent Runtime Flow

```text
User submits repoUrl + prNumber
  -> ReviewTaskService creates ReviewTaskEntity
  -> ReviewContext is built
  -> ReviewPipelineService runs
  -> Provider is selected by config
  -> XiaomiMiMoReviewProvider builds prompt
  -> XiaomiMiMoClient calls Xiaomi MiMo API
  -> Parser converts structured JSON to ReviewFinding[]
  -> Findings are persisted as ReviewIssueEntity
  -> issueSummary and riskLevel are computed
  -> Existing frontend renders the review result
```

### 7.3 Agent Fallback Flow

```text
If provider=mock:
  -> MockReviewProvider

If provider=mimo and MIMO_API_KEY exists:
  -> XiaomiMiMoReviewProvider

If provider=mimo but key/config/call/parser fails:
  -> fallback to MockReviewProvider
  -> keep API response stable
  -> log safe warning without key/raw secret
```

---

## 8. Provider Configuration

Introduce configuration such as:

```properties
codereviewx.review.provider=mock
codereviewx.ai.mimo.base-url=https://api.xiaomimimo.com/v1
codereviewx.ai.mimo.model=mimo-v2.5-pro
```

Environment variables should be supported through Spring configuration:

```text
MIMO_API_KEY
MIMO_BASE_URL
MIMO_MODEL
CODEREVIEWX_REVIEW_PROVIDER
```

Required behavior:

```text
Default provider = mock
```

Provider modes:

```text
mock
mimo
```

Expected behavior:

```text
mock:
  Always use MockReviewProvider.
  No Xiaomi MiMo call.
  No API key required.
  Round 08 behavior preserved.

mimo:
  Use XiaomiMiMoReviewProvider if MIMO_API_KEY exists and call succeeds.
  Fallback to MockReviewProvider if config/call/parser fails.
```

Do not require Xiaomi MiMo API key for tests.

Do not require Xiaomi MiMo API key for default local startup.

---

## 9. Xiaomi MiMo Client Design

Create a small HTTP adapter boundary, for example:

```text
XiaomiMiMoClient
```

Suggested package:

```text
backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/mimo
```

Suggested files:

```text
XiaomiMiMoReviewProvider.java
XiaomiMiMoClient.java
XiaomiMiMoClientRequest.java
XiaomiMiMoClientResponse.java
XiaomiMiMoProperties.java
XiaomiMiMoFindingParser.java
ReviewPromptBuilder.java
```

Alternative package structure is acceptable if clean.

### 9.1 Client Responsibility

```text
XiaomiMiMoClient
  -> send OpenAI-compatible chat completion request
  -> receive response
  -> return content string or structured response
```

Suggested endpoint:

```text
{baseUrl}/chat/completions
```

Suggested base URL default:

```text
https://api.xiaomimimo.com/v1
```

Authentication should support one of:

```text
Authorization: Bearer ${MIMO_API_KEY}
```

or:

```text
api-key: ${MIMO_API_KEY}
```

Prefer one method and document it. If simple, support both through configuration.

Do not log request headers.

Do not log API key.

Do not expose raw response through API.

---

## 10. Xiaomi MiMo Request Shape

Use OpenAI-compatible chat completion shape.

Recommended request:

```json
{
  "model": "${MIMO_MODEL}",
  "messages": [
    {
      "role": "system",
      "content": "You are CodeReviewX, an AI code review agent..."
    },
    {
      "role": "user",
      "content": "Review context..."
    }
  ],
  "temperature": 0.2
}
```

If Xiaomi MiMo requires model-specific parameters, isolate them in `XiaomiMiMoClient`.

Do not spread provider-specific request logic into `ReviewTaskService`.

---

## 11. Prompt Builder Requirements

Create:

```text
ReviewPromptBuilder
```

Purpose:

```text
Convert ReviewContext into a prompt suitable for code review finding extraction.
```

Minimum prompt content:

1. agent role；
2. review objective；
3. repo URL；
4. PR number；
5. current limitation that no PR diff is available yet, if true；
6. strict JSON output instructions；
7. allowed enum values；
8. required output schema；
9. instruction to avoid markdown fences；
10. instruction to return only JSON。

Recommended system prompt:

```text
You are CodeReviewX, an AI code review agent.
You identify security, maintainability, reliability, performance, and test risks.
Return only strict JSON. Do not wrap output in markdown.
```

Recommended user prompt:

```text
Review this pull request context.

repoUrl: ...
prNumber: ...

Current available context does not include the actual diff yet.
Return findings only if you can identify meaningful risks from the provided context.
For demo mode, you may return conservative synthetic findings clearly framed as review suggestions.

Return JSON array with objects:
[
  {
    "issueKey": "AI-ISSUE-1",
    "severity": "HIGH|MEDIUM|LOW",
    "category": "SECURITY|MAINTAINABILITY|PERFORMANCE|RELIABILITY|TEST|STYLE|DOCUMENTATION",
    "filePath": "string",
    "startLine": 1,
    "endLine": 1,
    "title": "string",
    "description": "string",
    "recommendation": "string"
  }
]
```

The exact enum list must match the existing backend enums.

---

## 12. Structured Output Parser

Create:

```text
XiaomiMiMoFindingParser
```

Purpose:

```text
Parse Xiaomi MiMo model output into List<ReviewFinding>.
```

Parser must:

1. accept strict JSON array；
2. reject malformed JSON safely；
3. reject invalid severity；
4. reject invalid category；
5. default `status=OPEN`；
6. set `source=MIMO` or `source=AI` depending on enum decision；
7. generate issueKey if missing；
8. sanitize blank title/description/recommendation；
9. avoid persisting invalid partial records；
10. not expose raw model output through API。

Recommended issue key prefix:

```text
MIMO-ISSUE-1
MIMO-ISSUE-2
MIMO-ISSUE-3
```

or:

```text
AI-ISSUE-1
AI-ISSUE-2
AI-ISSUE-3
```

Choose one and keep it consistent.

Preferred:

```text
MIMO-ISSUE-*
```

because provider is now fixed to Xiaomi MiMo.

---

## 13. IssueSource Extension

Round 09 should add a new issue source:

```text
MIMO
```

or:

```text
AI
```

Preferred:

```text
MIMO
```

Rationale:

1. provider is explicitly Xiaomi MiMo；
2. frontend can display source precisely；
3. future providers can add `OPENAI`, `ANTHROPIC`, `SEMgrep`, etc. if needed；
4. current `MOCK` remains unchanged。

Required:

1. update backend enum；
2. update frontend TypeScript type if needed；
3. update badge rendering if source values are hardcoded；
4. update tests and fixtures；
5. ensure old MOCK tasks still render。

Do not remove existing `MOCK`.

---

## 14. Failure and Fallback Semantics

Round 09 must explicitly implement and document failure behavior.

### 14.1 Default Mock Mode

When:

```text
codereviewx.review.provider=mock
```

Behavior:

```text
MockReviewProvider runs.
No Xiaomi MiMo call.
No API key required.
Same Round 08 behavior.
```

---

### 14.2 MiMo Mode With Valid Key

When:

```text
codereviewx.review.provider=mimo
MIMO_API_KEY is present
```

Behavior:

```text
XiaomiMiMoReviewProvider runs.
XiaomiMiMoClient calls API.
Parser converts response to ReviewFinding[].
Findings are persisted.
source=MIMO.
API response shape unchanged.
```

---

### 14.3 MiMo Mode Without Key

When:

```text
codereviewx.review.provider=mimo
MIMO_API_KEY is absent or blank
```

Behavior:

```text
Fallback to MockReviewProvider.
Log safe warning.
Do not fail application startup.
Do not expose reason in API.
Do not expose key/config details.
```

---

### 14.4 MiMo API Call Failure

When MiMo call fails due to network, timeout, non-2xx, or invalid response:

```text
Fallback to MockReviewProvider.
Log safe warning.
Do not expose stack trace in API.
Task creation should remain successful unless existing service contract requires otherwise.
```

---

### 14.5 Parser Failure

When model output cannot be parsed safely:

```text
Fallback to MockReviewProvider.
Log safe warning.
Do not persist malformed partial findings.
```

---

### 14.6 Empty Valid Findings

If MiMo returns valid empty JSON array:

Preferred behavior:

```text
Treat as successful AI review with zero findings.
Persist no issues.
issueSummary.totalIssues=0.
riskLevel=NONE.
source does not appear because there are no issues.
```

Alternative acceptable behavior:

```text
Fallback to mock for demo stability.
```

If choosing the alternative, document it clearly.

Preferred for product correctness:

```text
valid empty AI result means no issues
```

---

## 15. API Contract Rules

Round 09 must preserve endpoints:

```text
GET  /api/health
POST /api/review-tasks
GET  /api/review-tasks
GET  /api/review-tasks/{id}
```

Create request remains:

```json
{
  "repoUrl": "https://github.com/example/demo",
  "prNumber": 9
}
```

Response wrapper remains:

```json
{
  "success": true,
  "message": "OK",
  "data": {}
}
```

`ReviewTaskResponse` remains:

```text
id
repoUrl
prNumber
status
riskLevel
summary
errorMessage
issues
issueSummary
createdAt
updatedAt
```

`ReviewIssueResponse` remains:

```text
id
severity
category
source
status
filePath
startLine
endLine
title
description
recommendation
```

`IssueSummaryResponse` remains:

```text
totalIssues
highCount
mediumCount
lowCount
riskLevel
```

Invariant remains:

```text
ReviewTaskResponse.riskLevel == ReviewTaskResponse.issueSummary.riskLevel
```

Do not expose:

```text
providerName
successful
provider message
raw prompt
raw model output
API key
headers
stack trace
internal DB issue id
fallback reason
```

---

## 16. Persistence Rules

Preserve Round 08 persistence model.

Expected:

1. `ReviewTaskEntity` remains persisted；
2. `ReviewIssueEntity` remains persisted；
3. Xiaomi MiMo findings persist as `ReviewIssueEntity`；
4. summary computed from persisted issues；
5. risk derived from summary；
6. no `IssueSummaryEntity`；
7. no provider result table；
8. no raw model output table；
9. no prompt table；
10. no token/cost table；
11. no execution trace table；
12. no production DB migration unless absolutely unavoidable。

Strong preference:

```text
No new database columns in Round 09.
```

Adding enum value `MIMO` is acceptable if it maps to existing string/enum persistence safely.

---

## 17. Frontend Rules

Round 09 should avoid frontend redesign.

Allowed frontend changes:

1. update `IssueSource` TypeScript union to include `MIMO`；
2. update source badge rendering if values are hardcoded；
3. update fixtures/tests；
4. tiny copy update if needed；
5. ensure `MIMO` and `MOCK` both render cleanly。

Forbidden:

1. visual redesign；
2. dashboard rebuild；
3. new UI library；
4. chart library；
5. route overhaul；
6. state management library；
7. design system migration。

Final visual polish remains reserved for a later round.

---

## 18. Backend Tests Required

### 18.1 Provider Selection Tests

Verify:

1. default mode uses `MockReviewProvider`；
2. explicit mock mode uses `MockReviewProvider`；
3. mimo mode with key uses `XiaomiMiMoReviewProvider`；
4. mimo mode without key falls back to `MockReviewProvider`；
5. invalid provider config falls back safely or fails startup with clear internal error, depending on chosen design；
6. no API key required for tests。

---

### 18.2 XiaomiMiMoReviewProvider Tests

Use fake/stub `XiaomiMiMoClient`.

Verify:

1. builds prompt from `ReviewContext`；
2. calls client；
3. parses valid structured JSON；
4. maps JSON findings to `ReviewFinding`；
5. sets `source=MIMO`；
6. sets `status=OPEN`；
7. uses deterministic issue keys；
8. returns `ReviewProviderResult.successful=true` for valid result；
9. handles valid empty array according to chosen behavior。

---

### 18.3 XiaomiMiMoFindingParser Tests

Verify:

1. parses valid JSON array；
2. rejects malformed JSON safely；
3. rejects invalid severity safely；
4. rejects invalid category safely；
5. handles missing issueKey by generating one；
6. handles blank optional fields safely；
7. does not allow invalid records to be persisted；
8. does not expose raw output through public API。

---

### 18.4 Failure / Fallback Tests

Verify:

1. missing `MIMO_API_KEY` does not fail default test suite；
2. MiMo client exception triggers fallback；
3. non-2xx response triggers fallback；
4. parser exception triggers fallback；
5. fallback returns deterministic mock findings；
6. task creation still succeeds under fallback；
7. persisted issues remain valid；
8. summary/risk remain correct；
9. no key or raw response appears in API output。

---

### 18.5 Existing Behavior Preservation Tests

Verify:

1. mock mode still returns exactly 3 issues；
2. mock mode severities remain HIGH/MEDIUM/LOW；
3. mock mode source remains `MOCK`；
4. mock mode ids remain `ISSUE-1/2/3`；
5. API wrapper unchanged；
6. get/list/detail unchanged；
7. `riskLevel == issueSummary.riskLevel`；
8. missing task behavior unchanged。

---

## 19. Frontend Tests Required

Run:

```bash
cd frontend
npm run typecheck
npm run build
npm test -- --run
```

If adding `MIMO` source requires frontend updates:

1. update source type；
2. update source badge test；
3. ensure existing `MOCK` badge still passes；
4. ensure `MIMO` badge renders。

No frontend redesign.

---

## 20. Runtime Verification Requirements

### 20.1 Mock Mode Runtime

Default startup:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run
```

Create:

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{
    "repoUrl": "https://github.com/example/round-09-mock-mode",
    "prNumber": 9
  }'
```

Confirm:

```text
success=true
issues.length=3
source=MOCK
status=OPEN
issueSummary.totalIssues=3
riskLevel=HIGH
riskLevel == issueSummary.riskLevel
```

---

### 20.2 Xiaomi MiMo Mode Runtime

Set environment variable locally:

```bash
export MIMO_API_KEY="<local-secret-not-committed>"
```

Start backend in MiMo mode:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run \
  -Dspring-boot.run.arguments="--codereviewx.review.provider=mimo"
```

Create:

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{
    "repoUrl": "https://github.com/example/round-09-mimo-mode",
    "prNumber": 9
  }'
```

Confirm:

```text
success=true
response shape unchanged
if MiMo call succeeds:
  issues source=MIMO or zero findings with riskLevel=NONE
if MiMo call fails:
  fallback issues source=MOCK
riskLevel == issueSummary.riskLevel
```

Do not print API key in command output or handoff.

---

### 20.3 Missing Key Fallback Runtime

Start backend with MiMo mode but without `MIMO_API_KEY`:

```bash
unset MIMO_API_KEY

cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run \
  -Dspring-boot.run.arguments="--codereviewx.review.provider=mimo"
```

Create task.

Confirm:

```text
task creation succeeds
fallback to MOCK occurs
response shape unchanged
no stack trace in API
no secret/config details in API
```

---

### 20.4 Restart Persistence

Create at least one task in mock mode and one in MiMo/fallback mode.

Restart backend.

Confirm:

```text
GET /api/review-tasks/{id}
task still exists
issues still exist
source values persisted
issueSummary still correct
risk invariant still true
```

---

### 20.5 Browser Smoke

If feasible:

```bash
cd frontend
npm run dev -- --host 127.0.0.1
```

Confirm:

1. backend status UP；
2. create task works；
3. list updates；
4. detail renders；
5. summary panel renders；
6. issue cards render；
7. `MOCK` source badge renders；
8. `MIMO` source badge renders if MiMo call succeeds；
9. no browser console errors。

---

## 21. README Update Requirements

Update root README and backend README.

Must document:

1. Round 09 introduces Xiaomi MiMo provider；
2. default mode remains mock；
3. MiMo mode is config-driven；
4. `MIMO_API_KEY` is read from environment；
5. API key must never be committed；
6. MiMo OpenAI-compatible client boundary；
7. fallback behavior；
8. public API remains unchanged；
9. findings still persist as `ReviewIssue`；
10. summary/risk still computed from persisted issues；
11. current limitation: no PR diff context yet unless implemented；
12. current vs planned agent capability；
13. next round direction。

Correct wording:

```text
Round 09 introduces a Xiaomi MiMo provider behind the ReviewProvider interface.
Default local behavior remains mock mode.
MiMo mode is enabled through configuration and MIMO_API_KEY.
```

Incorrect wording:

```text
CodeReviewX now fully reviews real GitHub pull requests with production AI agents.
```

---

## 22. Required Agent Structure Documentation

From Round 09 onward, every Cursor/Codex/Qoder handoff must include a section:

```markdown
## Agent Structure and Flow
```

It must show:

```text
Input -> Context -> Pipeline -> Provider Selection -> Provider -> Prompt -> Model Client -> Parser -> Finding -> Persistence -> API DTO -> Frontend
```

For Round 09, use:

```text
Input:
  repoUrl + prNumber

Context:
  ReviewContext

Orchestrator:
  ReviewPipelineService

Provider:
  MockReviewProvider or XiaomiMiMoReviewProvider

Model:
  Xiaomi MiMo API

Output:
  ReviewFinding[]

Persistence:
  ReviewIssueEntity

Presentation:
  ReviewTaskResponse + frontend issue cards
```

This is required because the user wants visibility into how the agent is being constructed.

---

## 23. Round 09 Non-goals

Do not implement:

1. real GitHub PR diff ingestion unless trivial and explicitly scoped；
2. repository clone；
3. Semgrep execution；
4. full multi-agent planner；
5. async job queue；
6. streaming；
7. tool execution trace UI；
8. provider registry UI；
9. auth；
10. organization/team model；
11. dashboard analytics；
12. frontend redesign；
13. component library migration；
14. production DB hardening；
15. Flyway/Liquibase；
16. deployment/CI/CD。

Round 09 is Xiaomi MiMo provider capability, not the full platform.

---

## 24. Acceptance Criteria

### 24.1 Xiaomi MiMo Provider Architecture

- [ ] `XiaomiMiMoReviewProvider` introduced；
- [ ] it implements `ReviewProvider`；
- [ ] `XiaomiMiMoClient` or equivalent introduced；
- [ ] `ReviewPromptBuilder` or equivalent introduced；
- [ ] `XiaomiMiMoFindingParser` or equivalent introduced；
- [ ] parser maps model output to `ReviewFinding`；
- [ ] provider selection is configuration-driven；
- [ ] default provider mode remains mock；
- [ ] `MIMO_API_KEY` is read from environment only；
- [ ] mock provider remains available；
- [ ] fallback behavior implemented and documented。

### 24.2 Agent Structure

- [ ] handoff includes `Agent Structure and Flow`；
- [ ] input/context/pipeline/provider/model/parser/persistence/presentation chain documented；
- [ ] current capability vs future capability boundary clear；
- [ ] no overclaiming of full PR review if no diff is available。

### 24.3 Failure Semantics

- [ ] missing key behavior defined；
- [ ] MiMo call failure behavior defined；
- [ ] parser failure behavior defined；
- [ ] fallback-to-mock behavior defined；
- [ ] public API does not expose stack traces；
- [ ] provider internals are not exposed；
- [ ] tests cover failure/fallback path。

### 24.4 API Contract

- [ ] endpoints unchanged；
- [ ] request shape unchanged；
- [ ] response wrapper unchanged；
- [ ] `ReviewTaskResponse` fields preserved；
- [ ] `ReviewIssueResponse` fields preserved；
- [ ] `IssueSummaryResponse` fields preserved；
- [ ] `riskLevel == issueSummary.riskLevel` preserved；
- [ ] no raw prompt/model/provider details exposed。

### 24.5 Persistence

- [ ] `ReviewTaskEntity` persistence preserved；
- [ ] `ReviewIssueEntity` persistence preserved；
- [ ] MiMo/fallback findings persist as issues；
- [ ] summary computed from persisted issues；
- [ ] risk derived from summary；
- [ ] no provider result/raw output table；
- [ ] restart persistence preserved。

### 24.6 Tests

- [ ] backend tests pass；
- [ ] provider selection tests added；
- [ ] Xiaomi MiMo provider tests added；
- [ ] parser tests added；
- [ ] fallback tests added；
- [ ] existing mock behavior tests still pass；
- [ ] frontend typecheck passes；
- [ ] frontend build passes；
- [ ] frontend tests pass。

### 24.7 Runtime

- [ ] mock mode runtime works；
- [ ] MiMo mode runtime works or safely falls back；
- [ ] missing-key fallback runtime works；
- [ ] restart persistence works；
- [ ] browser smoke works if feasible。

### 24.8 Documentation

- [ ] README documents Round 09；
- [ ] backend README documents MiMo provider；
- [ ] environment variable setup documented；
- [ ] API key safety documented；
- [ ] fallback behavior documented；
- [ ] current vs planned capability clear；
- [ ] no overclaiming。

---

## 25. Suggested Round 09 Task Sequence

Continue Cursor → Codex → Qoder.

### 25.1 Cursor Implementation

Generate:

```text
tasks/round-09/01-cursor-xiaomi-mimo-ai-review-provider-v1.md
```

Responsibilities:

1. inspect Round 08 pipeline/provider code；
2. design minimal config-driven provider selection；
3. introduce `XiaomiMiMoReviewProvider`；
4. introduce `XiaomiMiMoClient`；
5. introduce prompt builder；
6. introduce structured output parser；
7. add `IssueSource.MIMO` if safe；
8. implement missing-key/call-failure/parser-failure fallback；
9. preserve API contract；
10. preserve persistence；
11. update frontend type/tests only if needed；
12. add backend tests；
13. run backend/frontend validations；
14. runtime verify mock mode；
15. runtime verify MiMo mode if key available；
16. runtime verify missing-key fallback；
17. update README；
18. include `Agent Structure and Flow` in handoff；
19. output Cursor handoff。

---

### 25.2 Codex Validation

Generate:

```text
tasks/round-09/02-codex-xiaomi-mimo-ai-review-provider-v1-validation.md
```

Responsibilities:

1. independently inspect MiMo provider implementation；
2. verify provider selection；
3. verify default mock behavior；
4. verify MiMo behavior；
5. verify missing-key fallback；
6. verify parser safety；
7. verify no key leakage；
8. verify API contract unchanged；
9. verify persistence unchanged；
10. run backend/frontend tests；
11. runtime verify modes；
12. scope creep check；
13. README accuracy check；
14. verify `Agent Structure and Flow`；
15. output Codex handoff。

---

### 25.3 Qoder Independent Review

Generate:

```text
tasks/round-09/03-qoder-xiaomi-mimo-ai-review-provider-v1-independent-review.md
```

Responsibilities:

1. independently judge MiMo provider architecture；
2. judge whether CodeReviewX is now AI-provider-capable；
3. verify fallback semantics；
4. verify local/demo stability；
5. verify no secret leakage；
6. verify no API/persistence/frontend drift；
7. verify no overclaiming；
8. decide whether Round 09 can close；
9. recommend exact Round 10 direction；
10. output Qoder handoff。

---

## 26. Recommended Round 10 Direction

If Round 09 succeeds, Round 10 should likely be:

```text
PR / Diff Context v1
```

Goal:

```text
Give Xiaomi MiMo meaningful review input instead of only repoUrl/prNumber.
```

Possible Round 10 scope:

1. manual pasted diff input；
2. changed files model；
3. `PullRequestContext`；
4. `ChangedFile`；
5. `FileDiff`；
6. prompt enrichment with actual diff；
7. optional GitHub API only if scoped safely。

Recommended priority:

```text
Manual pasted diff or mock diff context first.
GitHub API second.
```

This avoids getting blocked by GitHub auth and lets the AI review become substantively useful faster.

---

## 27. Final Instruction for Round 09

Round 09 must make CodeReviewX a Xiaomi MiMo-powered review agent prototype while preserving stability.

Essential instruction:

```text
Add XiaomiMiMoReviewProvider behind the Round 08 ReviewProvider boundary.
Keep mock mode as safe default.
Read API key only from environment.
Define fallback/failure semantics.
Preserve API, persistence, frontend behavior, and local demo stability.
Show the agent structure and execution flow in every handoff.
Do not overclaim full PR review until real diff context exists.
```

First task to generate:

```text
tasks/round-09/01-cursor-xiaomi-mimo-ai-review-provider-v1.md
```