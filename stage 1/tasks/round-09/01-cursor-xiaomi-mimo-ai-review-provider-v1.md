# tasks/round-09/01-cursor-xiaomi-mimo-ai-review-provider-v1.md

# Cursor Task: Xiaomi MiMo AI Review Provider v1

## 1. Task Metadata

- Project: CodeReviewX
- Round: Round 09
- Task: 01
- Owner: Cursor
- Theme: Xiaomi MiMo AI Review Provider v1
- Task Type: Implementation
- Upstream Dependency:
  - Round 08: Review Pipeline Orchestrator Skeleton
- Expected Handoff:
  - `tasks/round-09/01-cursor-xiaomi-mimo-ai-review-provider-v1-handoff.md`

## 2. Objective

在 Round 08 已完成的 review pipeline/provider 架构基础上，接入 Xiaomi MiMo 作为 CodeReviewX 的首个真实 AI Review Provider。

本任务必须实现：

```text
ReviewTaskService
  -> ReviewPipelineService
      -> configured ReviewProvider path
          -> MockReviewProvider
          OR
          -> XiaomiMiMoReviewProvider
              -> ReviewPromptBuilder
              -> XiaomiMiMoClient
              -> XiaomiMiMoFindingParser
              -> ReviewFinding[]
  -> persist ReviewFinding as ReviewIssueEntity
  -> compute issueSummary from persisted issues
  -> return unchanged ReviewTaskResponse
```

核心目标：

1. 默认仍然使用 mock provider；
2. Xiaomi MiMo provider 通过配置启用；
3. API key 只从环境变量读取；
4. MiMo 调用失败、缺少 key、解析失败时安全 fallback 到 mock；
5. 不改变 public API contract；
6. 不破坏 Round 08 的持久化、summary、risk invariant；
7. 开始按照 review agent 的工程结构呈现输入、上下文、pipeline、provider、prompt、client、parser、finding、persistence、API、frontend 的完整链路。

---

## 3. Critical Secret Handling Rules

用户已指定 AI Provider 为：

```text
Xiaomi MiMo
```

用户已提供 Xiaomi MiMo API key，但你不得在任何代码、配置文件、README、测试 fixture、日志、handoff、commit message、前端文件中写入、打印或暴露该 key。

必须通过环境变量读取：

```text
MIMO_API_KEY
```

允许支持的环境变量：

```text
MIMO_API_KEY
MIMO_BASE_URL
MIMO_MODEL
CODEREVIEWX_REVIEW_PROVIDER
```

推荐默认配置：

```properties
codereviewx.review.provider=mock
codereviewx.ai.mimo.base-url=https://api.xiaomimimo.com/v1
codereviewx.ai.mimo.model=mimo-v2.5-pro
```

严禁：

```text
1. 将 API key 写入 application.yml
2. 将 API key 写入 application.properties
3. 将 API key 写入 README
4. 将 API key 写入测试 fixture
5. 将 API key 打印到日志
6. 将 API key 返回给前端
7. 将 API key 放入 handoff
8. 将 API key 放入任何 snapshot
9. 将 API key 放入 git diff
```

允许本地运行时使用：

```bash
export MIMO_API_KEY="<local-secret-not-committed>"
```

如存在 `.env`，必须确保 `.env` 不被提交。

---

## 4. Current Architecture Assumption

Round 08 已经形成如下内部链路：

```text
ReviewTaskService
  -> ReviewPipelineService
      -> ReviewProvider
          -> MockReviewProvider
      -> ReviewFinding[]
  -> persist ReviewIssueEntity
  -> return unchanged API response
```

请先 inspect 当前代码，确认实际类名、包名、方法签名、enum、DTO、测试结构。

不要盲目重写已有架构。

优先在现有 Round 08 seam 上做最小增量实现。

---

## 5. Agent Structure and Flow

从 Round 09 开始，CodeReviewX 必须按 review agent 结构建设和说明。

本任务需要实现并在 handoff 中复述以下结构：

```text
CodeReviewX Review Agent
├── Input Layer
│   └── repoUrl + prNumber
├── Context Layer
│   └── ReviewContext
├── Orchestration Layer
│   └── ReviewPipelineService
├── Provider Selection Layer
│   └── configuration-driven provider selection
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
    └── ReviewTaskResponse + frontend issue cards
```

Runtime flow:

```text
User submits repoUrl + prNumber
  -> ReviewTaskService creates ReviewTaskEntity
  -> ReviewContext is built
  -> ReviewPipelineService runs
  -> provider is selected by config
  -> XiaomiMiMoReviewProvider builds prompt
  -> XiaomiMiMoClient calls Xiaomi MiMo API
  -> XiaomiMiMoFindingParser parses model output
  -> ReviewFinding[] is returned
  -> findings are persisted as ReviewIssueEntity
  -> issueSummary and riskLevel are computed
  -> frontend renders existing response shape
```

Fallback flow:

```text
provider=mock
  -> MockReviewProvider

provider=mimo + valid MIMO_API_KEY + successful MiMo call + valid parser output
  -> XiaomiMiMoReviewProvider
  -> source=MIMO

provider=mimo + missing key / client failure / parser failure
  -> fallback to MockReviewProvider
  -> source=MOCK
  -> API response shape unchanged
  -> safe warning log only
```

---

## 6. Scope

### 6.1 In Scope

Implement:

1. configuration-driven provider selection;
2. default mock provider mode;
3. Xiaomi MiMo provider mode;
4. Xiaomi MiMo client boundary;
5. prompt builder;
6. structured JSON parser;
7. fallback to mock on missing key, client failure, parser failure;
8. `IssueSource.MIMO` if safe;
9. backend tests;
10. minimal frontend type/test update if needed;
11. README updates;
12. runtime verification;
13. Cursor handoff with implementation summary and validation evidence.

### 6.2 Out of Scope

Do not implement:

1. real GitHub PR diff ingestion;
2. repository clone;
3. GitHub API integration;
4. Semgrep execution;
5. async job queue;
6. streaming;
7. tool execution trace UI;
8. provider registry UI;
9. auth;
10. org/team model;
11. dashboard analytics;
12. frontend redesign;
13. design system migration;
14. production DB migration;
15. Flyway/Liquibase;
16. deployment or CI/CD.

Round 09 是 Xiaomi MiMo provider capability，不是完整 PR review platform。

---

## 7. Backend Implementation Requirements

### 7.1 Configuration

Introduce or extend configuration to support:

```properties
codereviewx.review.provider=mock
codereviewx.ai.mimo.base-url=https://api.xiaomimimo.com/v1
codereviewx.ai.mimo.model=mimo-v2.5-pro
```

Environment variable support should work through Spring configuration:

```text
CODEREVIEWX_REVIEW_PROVIDER
MIMO_BASE_URL
MIMO_MODEL
MIMO_API_KEY
```

Implementation requirements:

1. default provider must be `mock`;
2. default local startup must not require `MIMO_API_KEY`;
3. tests must not require `MIMO_API_KEY`;
4. invalid or blank `MIMO_API_KEY` must not fail application startup;
5. provider mode values:
   - `mock`
   - `mimo`

Suggested property class:

```text
XiaomiMiMoProperties
```

Suggested fields:

```text
baseUrl
model
apiKey
```

Do not expose `apiKey` through `toString()`.

If using Lombok or record types, ensure secret fields are not accidentally logged.

---

### 7.2 Provider Selection

Implement provider selection in the cleanest way compatible with the current codebase.

Acceptable designs:

```text
ReviewProviderSelector
```

or:

```text
ReviewPipelineService chooses configured provider internally
```

or:

```text
Spring bean wiring selects active provider
```

Preferred behavior:

```text
codereviewx.review.provider=mock
  -> always use MockReviewProvider

codereviewx.review.provider=mimo
  -> try XiaomiMiMoReviewProvider
  -> fallback to MockReviewProvider on safe failure
```

Do not spread provider selection logic into controller or DTO layer.

Do not put MiMo-specific logic in `ReviewTaskService`.

---

### 7.3 Xiaomi MiMo Provider

Create provider implementation, suggested name:

```text
XiaomiMiMoReviewProvider
```

It must implement the existing `ReviewProvider` interface.

Responsibilities:

1. accept `ReviewContext`;
2. build prompt via `ReviewPromptBuilder`;
3. call `XiaomiMiMoClient`;
4. parse model content via `XiaomiMiMoFindingParser`;
5. return `ReviewProviderResult`;
6. produce `ReviewFinding[]`;
7. set source to `MIMO`;
8. handle empty valid array as successful zero findings;
9. never expose raw model output through public API;
10. never log API key or headers.

Preferred empty result behavior:

```text
Valid empty JSON array from MiMo means successful AI review with zero findings.
No fallback.
Persist no issues.
issueSummary.totalIssues=0.
riskLevel=NONE.
```

---

### 7.4 Xiaomi MiMo Client

Create small HTTP adapter boundary:

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

Use the actual project package conventions after inspection.

Client responsibility:

```text
XiaomiMiMoClient
  -> send OpenAI-compatible chat completion request
  -> receive response
  -> return assistant content string
```

Suggested endpoint:

```text
{baseUrl}/chat/completions
```

Suggested request shape:

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

Authentication:

Prefer:

```text
Authorization: Bearer ${MIMO_API_KEY}
```

If the actual MiMo API requires a different auth header, isolate that decision inside `XiaomiMiMoClient` and make it configurable if simple.

Do not log:

```text
request headers
Authorization header
api key
raw request body with secrets
raw provider response
```

Client failure should be converted into a safe internal exception or unsuccessful provider result so the pipeline can fallback to mock.

---

### 7.5 Prompt Builder

Create:

```text
ReviewPromptBuilder
```

Purpose:

```text
Convert ReviewContext into system/user prompts for code review finding extraction.
```

Minimum prompt requirements:

1. agent role;
2. review objective;
3. repo URL;
4. PR number;
5. state current limitation: no PR diff context is available yet;
6. strict JSON output instruction;
7. allowed enum values matching backend enums;
8. required output schema;
9. instruction to avoid markdown fences;
10. instruction to return only JSON.

Recommended system prompt:

```text
You are CodeReviewX, an AI code review agent.
You identify security, maintainability, reliability, performance, test, style, and documentation risks.
Return only strict JSON. Do not wrap output in markdown.
```

Recommended user prompt template:

```text
Review this pull request context.

repoUrl: <repoUrl>
prNumber: <prNumber>

Current available context does not include the actual PR diff yet.
Return findings only if you can identify meaningful risks from the provided context.
For demo mode, you may return conservative synthetic findings clearly framed as review suggestions.

Return only a JSON array with objects using this schema:
[
  {
    "issueKey": "MIMO-ISSUE-1",
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

Rules:
- Return only JSON.
- Do not wrap JSON in markdown fences.
- Do not include prose before or after JSON.
- Use only allowed enum values.
- If there are no meaningful findings, return [].
```

Important:

The enum values in the prompt must match the existing backend enums. Inspect and adjust the above category list if the codebase uses different values.

---

### 7.6 Structured Output Parser

Create:

```text
XiaomiMiMoFindingParser
```

Purpose:

```text
Parse model output into List<ReviewFinding>.
```

Parser must:

1. accept strict JSON array;
2. reject malformed JSON safely;
3. reject invalid severity;
4. reject invalid category;
5. default `status=OPEN`;
6. set `source=MIMO`;
7. generate issueKey if missing;
8. use deterministic issue keys:
   - `MIMO-ISSUE-1`
   - `MIMO-ISSUE-2`
   - `MIMO-ISSUE-3`
9. sanitize blank title/description/recommendation;
10. avoid persisting invalid partial records;
11. not expose raw model output through public API;
12. not log raw model output unless explicitly sanitized and short.

Suggested parser behavior:

```text
If JSON is malformed:
  throw safe parser exception or return unsuccessful provider result

If any record has invalid enum:
  reject the entire model output and fallback to mock

If issueKey is missing or blank:
  generate deterministic MIMO-ISSUE-N

If optional text fields are blank:
  fill safe default text

If filePath is blank:
  use "unknown"

If startLine/endLine are missing or invalid:
  use 1 or existing project-safe default
```

Do not persist malformed partial findings.

---

### 7.7 IssueSource Extension

Add a new issue source if current enum supports it safely:

```text
MIMO
```

Keep existing:

```text
MOCK
```

Required changes:

1. backend enum update;
2. parser/provider mapping to `MIMO`;
3. tests and fixtures update;
4. frontend TypeScript type update if source values are typed;
5. badge rendering update if hardcoded;
6. ensure old `MOCK` tasks still render.

Do not remove or rename `MOCK`.

---

### 7.8 Fallback Semantics

Implement explicit fallback behavior.

#### Default mock mode

When:

```text
codereviewx.review.provider=mock
```

Behavior:

```text
MockReviewProvider runs.
No Xiaomi MiMo call.
No API key required.
Round 08 behavior preserved.
```

#### MiMo mode with valid key

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

#### MiMo mode without key

When:

```text
codereviewx.review.provider=mimo
MIMO_API_KEY is absent or blank
```

Behavior:

```text
Fallback to MockReviewProvider.
Log safe warning.
Do not fail app startup.
Do not expose reason in API.
Do not expose key/config details.
```

#### MiMo API failure

When MiMo call fails due to timeout, network, non-2xx, unexpected response shape:

```text
Fallback to MockReviewProvider.
Log safe warning.
Do not expose stack trace in API.
Task creation remains successful.
```

#### Parser failure

When model output cannot be parsed safely:

```text
Fallback to MockReviewProvider.
Log safe warning.
Do not persist malformed partial findings.
```

#### Empty valid findings

Preferred behavior:

```text
Valid [] from MiMo is successful AI review with zero findings.
No fallback.
Persist no issues.
issueSummary.totalIssues=0.
riskLevel=NONE.
```

If you cannot safely preserve this due to current service assumptions, document the reason in handoff.

---

## 8. API Contract Preservation

Do not change public endpoints:

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

`ReviewTaskResponse` must preserve:

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

`ReviewIssueResponse` must preserve:

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

`IssueSummaryResponse` must preserve:

```text
totalIssues
highCount
mediumCount
lowCount
riskLevel
```

Invariant must remain true:

```text
ReviewTaskResponse.riskLevel == ReviewTaskResponse.issueSummary.riskLevel
```

Do not expose in public API:

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

## 9. Persistence Rules

Preserve Round 08 persistence model.

Expected:

1. `ReviewTaskEntity` remains persisted;
2. `ReviewIssueEntity` remains persisted;
3. Xiaomi MiMo findings persist as `ReviewIssueEntity`;
4. fallback mock findings persist as `ReviewIssueEntity`;
5. summary is computed from persisted issues;
6. risk is derived from summary;
7. no `IssueSummaryEntity`;
8. no provider result table;
9. no raw model output table;
10. no prompt table;
11. no token/cost table;
12. no execution trace table;
13. no production DB migration unless absolutely unavoidable.

Strong preference:

```text
No new database columns in Round 09.
```

Adding enum value `MIMO` is acceptable if current enum persistence is string-based or otherwise safe.

If the current DB enum mapping makes adding `MIMO` risky, document the exact issue and choose the smallest safe implementation.

---

## 10. Frontend Requirements

Do not redesign frontend.

Allowed changes:

1. update `IssueSource` TypeScript union to include `MIMO`;
2. update source badge rendering if hardcoded;
3. update tests/fixtures;
4. tiny copy update if needed;
5. ensure `MIMO` and `MOCK` both render cleanly.

Forbidden:

1. visual redesign;
2. dashboard rebuild;
3. new UI library;
4. chart library;
5. route overhaul;
6. state management library;
7. design system migration.

Frontend final polish remains for a later round.

---

## 11. Backend Tests Required

Add or update tests for the following.

### 11.1 Provider Selection Tests

Verify:

1. default mode uses `MockReviewProvider`;
2. explicit mock mode uses `MockReviewProvider`;
3. mimo mode with key attempts `XiaomiMiMoReviewProvider`;
4. mimo mode without key falls back to `MockReviewProvider`;
5. invalid provider config falls back safely or fails startup with clear internal error, depending on chosen design;
6. no API key required for default tests.

### 11.2 XiaomiMiMoReviewProvider Tests

Use fake/stub `XiaomiMiMoClient`.

Verify:

1. builds prompt from `ReviewContext`;
2. calls client;
3. parses valid structured JSON;
4. maps JSON findings to `ReviewFinding`;
5. sets `source=MIMO`;
6. sets `status=OPEN`;
7. uses deterministic issue keys;
8. returns successful provider result for valid result;
9. handles valid empty array according to chosen behavior.

### 11.3 XiaomiMiMoFindingParser Tests

Verify:

1. parses valid JSON array;
2. rejects malformed JSON safely;
3. rejects invalid severity safely;
4. rejects invalid category safely;
5. handles missing issueKey by generating one;
6. handles blank optional fields safely;
7. does not allow invalid records to be persisted;
8. does not expose raw output through public API.

### 11.4 Failure / Fallback Tests

Verify:

1. missing `MIMO_API_KEY` does not fail default test suite;
2. MiMo client exception triggers fallback;
3. non-2xx response triggers fallback if covered at client level;
4. parser exception triggers fallback;
5. fallback returns deterministic mock findings;
6. task creation still succeeds under fallback;
7. persisted issues remain valid;
8. summary/risk remain correct;
9. no key or raw response appears in API output.

### 11.5 Existing Behavior Preservation Tests

Verify:

1. mock mode still returns exactly 3 issues;
2. mock mode severities remain HIGH/MEDIUM/LOW;
3. mock mode source remains `MOCK`;
4. mock mode ids remain `ISSUE-1/2/3`;
5. API wrapper unchanged;
6. get/list/detail unchanged;
7. `riskLevel == issueSummary.riskLevel`;
8. missing task behavior unchanged.

---

## 12. Frontend Validation Required

If frontend code changes are needed, run:

```bash
cd frontend
npm run typecheck
npm run build
npm test -- --run
```

Required if adding `MIMO` source:

1. `IssueSource` includes `MIMO`;
2. badge rendering supports `MIMO`;
3. existing `MOCK` badge still passes;
4. issue cards render both source values.

---

## 13. Runtime Verification Required

### 13.1 Mock Mode Runtime

Default startup:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run
```

Create task:

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

### 13.2 Xiaomi MiMo Mode Runtime

Use local environment variable only:

```bash
export MIMO_API_KEY="<local-secret-not-committed>"
```

Start backend in MiMo mode:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run \
  -Dspring-boot.run.arguments="--codereviewx.review.provider=mimo"
```

Create task:

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

Do not include the real key in handoff.

### 13.3 Missing Key Fallback Runtime

Start backend with MiMo mode but without key:

```bash
unset MIMO_API_KEY

cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run \
  -Dspring-boot.run.arguments="--codereviewx.review.provider=mimo"
```

Create task and confirm:

```text
task creation succeeds
fallback to MOCK occurs
response shape unchanged
no stack trace in API
no secret/config details in API
```

### 13.4 Restart Persistence

Create at least:

```text
1 mock mode task
1 mimo mode or mimo fallback task
```

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

### 13.5 Browser Smoke

If feasible:

```bash
cd frontend
npm run dev -- --host 127.0.0.1
```

Confirm:

1. backend status UP;
2. create task works;
3. list updates;
4. detail renders;
5. summary panel renders;
6. issue cards render;
7. `MOCK` source badge renders;
8. `MIMO` source badge renders if MiMo call succeeds or fixture exists;
9. no browser console errors.

---

## 14. Documentation Updates

Update root README and backend README.

Must document:

1. Round 09 introduces Xiaomi MiMo provider;
2. default mode remains mock;
3. MiMo mode is config-driven;
4. `MIMO_API_KEY` is read from environment;
5. API key must never be committed;
6. MiMo OpenAI-compatible client boundary;
7. fallback behavior;
8. public API remains unchanged;
9. findings still persist as `ReviewIssue`;
10. summary/risk still computed from persisted issues;
11. current limitation: no PR diff context yet;
12. current vs planned agent capability;
13. likely next direction: PR/diff context v1.

Use accurate wording:

```text
Round 09 introduces a Xiaomi MiMo provider behind the ReviewProvider interface.
Default local behavior remains mock mode.
MiMo mode is enabled through configuration and MIMO_API_KEY.
```

Do not claim:

```text
CodeReviewX now fully reviews real GitHub pull requests with production AI agents.
```

Correct positioning:

```text
CodeReviewX now has a configurable Xiaomi MiMo AI provider path.
Realistic PR/diff context enrichment is planned for a following round.
```

---

## 15. Validation Commands

Run backend tests:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
```

Run frontend validations if frontend changed:

```bash
cd frontend
npm run typecheck
npm run build
npm test -- --run
```

If frontend did not change, state that explicitly in handoff and still run existing frontend checks if time allows.

---

## 16. Acceptance Criteria

### 16.1 Xiaomi MiMo Provider Architecture

- [ ] `XiaomiMiMoReviewProvider` introduced;
- [ ] it implements existing `ReviewProvider`;
- [ ] `XiaomiMiMoClient` or equivalent introduced;
- [ ] `ReviewPromptBuilder` or equivalent introduced;
- [ ] `XiaomiMiMoFindingParser` or equivalent introduced;
- [ ] parser maps model output to `ReviewFinding`;
- [ ] provider selection is configuration-driven;
- [ ] default provider mode remains mock;
- [ ] `MIMO_API_KEY` is read from environment only;
- [ ] mock provider remains available;
- [ ] fallback behavior implemented and documented.

### 16.2 Agent Structure

- [ ] handoff includes `Agent Structure and Flow`;
- [ ] input/context/pipeline/provider/model/parser/persistence/presentation chain documented;
- [ ] current capability vs future capability boundary clear;
- [ ] no overclaiming of full PR review if no diff is available.

### 16.3 Failure Semantics

- [ ] missing key behavior defined and tested;
- [ ] MiMo call failure behavior defined and tested;
- [ ] parser failure behavior defined and tested;
- [ ] fallback-to-mock behavior defined and tested;
- [ ] public API does not expose stack traces;
- [ ] provider internals are not exposed;
- [ ] tests cover fallback path.

### 16.4 API Contract

- [ ] endpoints unchanged;
- [ ] request shape unchanged;
- [ ] response wrapper unchanged;
- [ ] `ReviewTaskResponse` fields preserved;
- [ ] `ReviewIssueResponse` fields preserved;
- [ ] `IssueSummaryResponse` fields preserved;
- [ ] `riskLevel == issueSummary.riskLevel` preserved;
- [ ] no raw prompt/model/provider details exposed.

### 16.5 Persistence

- [ ] `ReviewTaskEntity` persistence preserved;
- [ ] `ReviewIssueEntity` persistence preserved;
- [ ] MiMo/fallback findings persist as issues;
- [ ] summary computed from persisted issues;
- [ ] risk derived from summary;
- [ ] no provider result/raw output table;
- [ ] restart persistence preserved.

### 16.6 Tests

- [ ] backend tests pass;
- [ ] provider selection tests added;
- [ ] Xiaomi MiMo provider tests added;
- [ ] parser tests added;
- [ ] fallback tests added;
- [ ] existing mock behavior tests still pass;
- [ ] frontend typecheck passes if frontend changed;
- [ ] frontend build passes if frontend changed;
- [ ] frontend tests pass if frontend changed.

### 16.7 Runtime

- [ ] mock mode runtime works;
- [ ] MiMo mode runtime works or safely falls back;
- [ ] missing-key fallback runtime works;
- [ ] restart persistence works;
- [ ] browser smoke works if feasible.

### 16.8 Documentation

- [ ] README documents Round 09;
- [ ] backend README documents MiMo provider;
- [ ] environment variable setup documented;
- [ ] API key safety documented;
- [ ] fallback behavior documented;
- [ ] current vs planned capability clear;
- [ ] no overclaiming.

---

## 17. Required Handoff Format

After implementation, create:

```text
tasks/round-09/01-cursor-xiaomi-mimo-ai-review-provider-v1-handoff.md
```

The handoff must include:

```markdown
# Cursor Handoff: Xiaomi MiMo AI Review Provider v1

## 1. Summary

## 2. Files Changed

## 3. Agent Structure and Flow

## 4. Provider Configuration

## 5. Xiaomi MiMo Provider Implementation

## 6. Prompt and Parser Behavior

## 7. Fallback and Failure Semantics

## 8. API Contract Verification

## 9. Persistence Verification

## 10. Frontend Impact

## 11. Tests Added or Updated

## 12. Validation Commands and Results

## 13. Runtime Verification

## 14. Secret Handling Verification

## 15. Known Limitations

## 16. Recommendation for Codex Validation
```

### 17.1 Required Agent Structure and Flow Section

The handoff must include this chain:

```text
Input -> Context -> Pipeline -> Provider Selection -> Provider -> Prompt -> Model Client -> Parser -> Finding -> Persistence -> API DTO -> Frontend
```

For this round, describe:

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

### 17.2 Required Secret Handling Section

The handoff must explicitly state:

```text
No API key was committed.
No API key was written to README.
No API key was written to application config.
No API key was written to tests or fixtures.
No API key is returned through API response.
No API key is logged.
MIMO_API_KEY is read only from environment/config binding.
```

Do not include the actual key.

### 17.3 Required Validation Evidence

Include exact commands run and pass/fail result.

For example:

```text
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
Result: PASS

cd frontend
npm run typecheck
Result: PASS

cd frontend
npm run build
Result: PASS

cd frontend
npm test -- --run
Result: PASS
```

If a command fails, include:

1. command;
2. failure summary;
3. root cause;
4. whether fixed;
5. remaining risk.

Do not hide failures.

---

## 18. Final Implementation Instruction

Implement the smallest stable Xiaomi MiMo provider path behind the existing ReviewProvider boundary.

Priorities:

```text
1. preserve existing local mock stability
2. preserve API contract
3. preserve persistence and risk summary invariants
4. safely read MIMO_API_KEY from environment only
5. add MiMo provider/client/prompt/parser
6. implement fallback
7. add meaningful tests
8. update docs without overclaiming
9. produce detailed handoff
```

Do not over-engineer provider registry, execution trace storage, frontend provider UI, or full PR diff ingestion in this round.

Round 09 is successful when CodeReviewX can be accurately described as:

```text
A configurable Xiaomi MiMo-powered review agent prototype with safe mock fallback.
```

It must not yet be described as:

```text
A complete production GitHub pull request review platform.
```