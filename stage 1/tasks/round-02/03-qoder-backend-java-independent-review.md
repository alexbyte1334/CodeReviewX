# tasks/round-02/03-qoder-backend-java-independent-review.md

# Qoder Task: Round 02 - backend-java Independent Review

## 1. Task Metadata

- Project: CodeReviewX
- Round: Round 02
- Task Name: backend-java Independent Review
- Task File: `tasks/round-02/03-qoder-backend-java-independent-review.md`
- Target Agent: Qoder
- Target Agent Role: 独立架构审查与代码审查 Agent
- Architect: ChatGPT
- Upstream Implementation Agent: Cursor
- Upstream Implementation Handoff: `handoff/round-02/01-cursor-backend-java-skeleton-handoff.md`
- Upstream Validation Agent: Codex
- Upstream Validation Handoff: `handoff/round-02/02-codex-backend-java-validation-handoff.md`
- Required Review Report: `handoff/round-02/03-qoder-backend-java-independent-review.md`

---

## 2. Role Definition

你是 Qoder，本轮 Round 02 的独立架构审查与代码审查 Agent。

你的职责是：

1. 独立审查 Cursor 创建的 `backend-java` skeleton；
2. 独立审查 Codex 的验证结果；
3. 判断实现是否符合 Round 02 的目标、边界和架构约束；
4. 检查是否存在隐藏的范围扩大、依赖污染、提前业务实现或架构漂移；
5. 给出审查结论、风险评级和改进建议；
6. 生成 Markdown Review Report。

你不得修改代码。  
你不得修复代码。  
你不得执行编码任务。  
你不得进入 Round 03。  
你不得擅自扩大审查范围到新功能设计。

Agent 分工边界：

- Cursor：Round 02 主要编码执行 Agent；
- Codex：Round 02 仓库级验证、测试和最小修复 Agent；
- Qoder：Round 02 独立审查 Agent，只审查，不编码；
- ChatGPT：项目总架构师，负责最终裁决。

---

## 3. Project Background

CodeReviewX 是一个面向 GitHub Pull Request 的智能代码审查与修复建议 Agent。

最终系统目标：

用户输入：

- GitHub 仓库地址；
- Pull Request 编号。

系统最终链路：

```text
用户输入 repoUrl + prNumber
    ↓
backend-java 创建 ReviewTask
    ↓
backend-java 调用 ai-service
    ↓
ai-service 拉取 GitHub PR diff
    ↓
ai-service 解析文件变更
    ↓
ai-service 执行 Semgrep
    ↓
ai-service 调用 mock / real LLM
    ↓
ai-service 返回结构化 Review JSON
    ↓
backend-java 保存结果
    ↓
frontend 展示 Review 报告
```

最终系统会识别：

1. 潜在 Bug；
2. 安全风险；
3. 性能问题；
4. 测试缺失问题；
5. 代码风格问题。

但 Round 02 只应完成并审查 `backend-java` 的最小 Spring Boot skeleton。

Round 02 不应实现 ReviewTask 业务流程，不应实现数据库持久化，不应调用 `ai-service`，不应集成 GitHub / Semgrep / LLM。

---

## 4. Upstream Status

### 4.1 Cursor Implementation Summary

Cursor 已完成 `backend-java` skeleton，并提交：

```text
handoff/round-02/01-cursor-backend-java-skeleton-handoff.md
```

Cursor 声称已创建：

```text
backend-java/pom.xml
backend-java/src/main/java/com/codereviewx/backend/CodeReviewXBackendApplication.java
backend-java/src/main/java/com/codereviewx/backend/common/ApiResponse.java
backend-java/src/main/java/com/codereviewx/backend/controller/HealthController.java
backend-java/src/main/java/com/codereviewx/backend/config/WebConfig.java
backend-java/src/main/java/com/codereviewx/backend/review/dto/CreateReviewTaskRequest.java
backend-java/src/main/java/com/codereviewx/backend/review/dto/ReviewTaskResponse.java
backend-java/src/main/java/com/codereviewx/backend/review/dto/ReviewIssueResponse.java
backend-java/src/main/java/com/codereviewx/backend/review/enums/ReviewTaskStatus.java
backend-java/src/main/java/com/codereviewx/backend/review/enums/RiskLevel.java
backend-java/src/main/java/com/codereviewx/backend/review/enums/IssueType.java
backend-java/src/main/java/com/codereviewx/backend/review/enums/IssueSeverity.java
backend-java/src/main/java/com/codereviewx/backend/review/enums/IssueSource.java
backend-java/src/main/resources/application.yml
backend-java/src/main/resources/application-local.yml
backend-java/src/test/java/com/codereviewx/backend/CodeReviewXBackendApplicationTests.java
```

Cursor 未执行 `mvn test`，原因是当时执行环境缺少 Java 17 和 Maven。

### 4.2 Codex Validation Summary

Codex 已完成 Round 02 验证，并提交：

```text
handoff/round-02/02-codex-backend-java-validation-handoff.md
```

Codex 验证结论：

1. Java 17 和 Maven 已配置成功；
2. `mvn test` 已通过；
3. Spring context load test 已通过；
4. `GET /api/health` 运行时验证已通过；
5. `/api/health` 返回预期 JSON；
6. 未发现 ReviewTask 业务 API；
7. 未发现数据库持久化；
8. 未发现 MyBatis-Plus / MySQL / JPA；
9. 未发现 `ai-service` / GitHub / Semgrep / LLM 集成；
10. 未发现 frontend / ai-service 业务代码；
11. 未发现未授权依赖；
12. Codex 没有修改 backend 源码或配置。

---

## 5. Current Review Goal

Qoder 本轮目标是对 Round 02 做独立审查。

需要回答：

1. `backend-java` skeleton 是否符合 Round 02 目标？
2. Cursor 是否严格控制在允许范围内？
3. Codex 验证是否充分？
4. 是否存在隐藏的业务实现或范围扩大？
5. 是否存在依赖污染？
6. 是否存在架构漂移？
7. 是否存在阻塞 Round 02 验收的问题？
8. 是否建议 ChatGPT Architect 接受 Round 02？
9. 是否建议进入 Round 03？

---

## 6. Allowed Scope

Qoder 可以读取并审查以下范围：

```text
backend-java/
tasks/round-02/
handoff/round-02/
README.md
docs/
ai-service/
frontend/
docker-compose.yml
.github/
```

审查重点应放在：

```text
backend-java/
handoff/round-02/01-cursor-backend-java-skeleton-handoff.md
handoff/round-02/02-codex-backend-java-validation-handoff.md
tasks/round-02/01-cursor-backend-java-skeleton.md
tasks/round-02/02-codex-backend-java-validation.md
```

Qoder 可以执行只读检查命令，例如：

```bash
find backend-java -type f | sort
find ai-service frontend -type f | sort
grep -R "review-tasks" -n backend-java/src || true
grep -R "@PostMapping" -n backend-java/src || true
grep -R "@GetMapping" -n backend-java/src || true
grep -R "@RequestMapping" -n backend-java/src || true
grep -R "Entity\|Mapper\|Repository\|DataSource" -n backend-java/src backend-java/pom.xml || true
grep -R "mybatis\|mysql\|jpa\|hibernate\|flyway\|liquibase" -ni backend-java || true
grep -R "ai-service\|github\|semgrep\|llm\|openai\|WebClient\|RestTemplate\|HttpClient" -ni backend-java/src backend-java/pom.xml backend-java/src/main/resources || true
find . \( -name "package.json" -o -name "requirements.txt" -o -name "pyproject.toml" \) -not -path "./backend-java/target/*" -print
```

Qoder 可以根据需要执行：

```bash
cd backend-java && mvn test
```

但这不是必须项，因为 Codex 已验证通过。若 Qoder 执行该命令，应在 Review Report 中记录结果。

---

## 7. Forbidden Actions

Qoder 严禁执行以下行为。

### 7.1 禁止修改代码

不得修改任何源码、配置、文档或 Handoff 文件。

包括但不限于：

```text
backend-java/
ai-service/
frontend/
docs/
README.md
tasks/
handoff/
docker-compose.yml
.github/
```

Qoder 只能输出 Review Report。

---

### 7.2 禁止实现业务功能

不得实现：

```text
POST /api/review-tasks
GET /api/review-tasks
GET /api/review-tasks/{id}
```

不得创建：

1. Controller 业务接口；
2. Service；
3. Entity；
4. Mapper；
5. Repository；
6. DAO；
7. 数据库迁移；
8. ai-service client；
9. GitHub client；
10. Semgrep runner；
11. LLM client。

---

### 7.3 禁止引入依赖或基础设施

不得添加：

1. MyBatis-Plus；
2. MySQL Driver；
3. JPA；
4. Lombok；
5. Swagger / OpenAPI；
6. Spring Security；
7. Redis；
8. MQ；
9. Docker 插件；
10. GitHub Actions 构建；
11. Kubernetes；
12. RAG；
13. Vector DB；
14. 外部 SDK。

---

### 7.4 禁止进入后续阶段

不得：

1. 进入 Round 03；
2. 生成 Round 03 实现代码；
3. 创建 Codex 修复任务；
4. 直接修改 Cursor 或 Codex 的产物；
5. 擅自改变架构路线。

如发现问题，只能在 Review Report 中提出并交由 ChatGPT Architect 裁决。

---

## 8. Review Checklist

Qoder 必须围绕以下检查项进行独立审查。

### 8.1 Backend Skeleton

- [ ] `backend-java/pom.xml` 是否存在；
- [ ] 是否使用 Spring Boot 3；
- [ ] 是否使用 Java 17；
- [ ] 是否使用 Maven 标准结构；
- [ ] Spring Boot 入口类是否存在；
- [ ] `GET /api/health` 是否存在；
- [ ] `ApiResponse` 是否存在；
- [ ] Review 枚举是否存在；
- [ ] Review 枚举值是否严格符合要求；
- [ ] DTO 占位类是否存在；
- [ ] DTO 是否没有持久化注解；
- [ ] `application.yml` 是否存在；
- [ ] `application-local.yml` 是否存在；
- [ ] Spring context load test 是否存在；
- [ ] Codex 是否已验证 `mvn test` 通过；
- [ ] Codex 是否已验证运行时 `/api/health`。

---

### 8.2 Scope Control

- [ ] 是否未实现 `POST /api/review-tasks`；
- [ ] 是否未实现 `GET /api/review-tasks`；
- [ ] 是否未实现 `GET /api/review-tasks/{id}`；
- [ ] 是否未创建 ReviewTask Entity；
- [ ] 是否未创建 ReviewFileChange Entity；
- [ ] 是否未创建 ReviewIssue Entity；
- [ ] 是否未创建 MyBatis Mapper；
- [ ] 是否未创建 Repository；
- [ ] 是否未创建数据库迁移；
- [ ] 是否未添加 MyBatis-Plus；
- [ ] 是否未添加 MySQL Driver；
- [ ] 是否未添加 JPA；
- [ ] 是否未调用 `ai-service`；
- [ ] 是否未实现 GitHub API；
- [ ] 是否未实现 Semgrep；
- [ ] 是否未实现 LLM；
- [ ] 是否未创建 frontend 业务代码；
- [ ] 是否未创建 ai-service 业务代码；
- [ ] 是否未添加未批准依赖。

---

### 8.3 Architecture Consistency

- [ ] `backend-java` 是否仍保持任务管理服务定位；
- [ ] 是否没有把 ai-service 职责提前放入 backend；
- [ ] 是否没有把 frontend 职责放入 backend；
- [ ] DTO 是否只是 API 契约占位，而不是持久化模型；
- [ ] 枚举是否与 Review JSON 标准结构一致；
- [ ] 健康检查接口是否保持基础设施接口定位；
- [ ] 配置文件是否没有提前引入数据库或外部服务配置；
- [ ] `WebConfig` 是否只是占位，没有提前加入 CORS / Security / Interceptor。

---

### 8.4 Code Quality Review

- [ ] 包名是否统一为 `com.codereviewx.backend`；
- [ ] 类名是否清晰；
- [ ] `ApiResponse<T>` 是否简单且足够；
- [ ] DTO 是否具备无参构造、getter、setter；
- [ ] DTO 字段类型是否合理；
- [ ] Validation 注解是否使用 `jakarta.validation`；
- [ ] `HealthController` 返回结构是否清晰；
- [ ] 测试是否没有外部依赖；
- [ ] README 是否准确说明当前已实现和未实现内容。

---

### 8.5 Validation Quality Review

- [ ] Cursor Handoff 是否完整；
- [ ] Codex Handoff 是否完整；
- [ ] Codex 是否补齐 Cursor 未执行的 `mvn test`；
- [ ] Codex 是否实际验证了 `/api/health`；
- [ ] Codex 是否执行了范围检查；
- [ ] Codex 是否没有做超出最小修复范围的改动；
- [ ] Codex 是否明确给出进入 Qoder 的建议。

---

## 9. Risk Rating Guidance

Qoder 需要给出风险评级：

```text
LOW
MEDIUM
HIGH
```

建议评级标准：

### LOW

满足以下条件时可评为 LOW：

1. `mvn test` 已通过；
2. `/api/health` 已验证；
3. 未发现范围越界；
4. 未发现未授权依赖；
5. 未发现架构职责错位；
6. 仅存在非阻塞性文档或风格建议。

### MEDIUM

存在以下问题时应评为 MEDIUM：

1. 测试通过但存在轻微结构问题；
2. 文档和实现有轻微不一致；
3. DTO 或配置存在非阻塞改进空间；
4. Codex 验证存在部分遗漏但不影响当前轮次；
5. 存在可能影响后续 Round 03 的设计风险。

### HIGH

存在以下问题时应评为 HIGH：

1. `mvn test` 未通过；
2. `/api/health` 无法启动或返回错误；
3. 存在 ReviewTask 业务 API；
4. 存在数据库持久化实现；
5. 存在 MyBatis-Plus / MySQL / JPA；
6. 存在 ai-service / GitHub / Semgrep / LLM 调用；
7. 存在 frontend / ai-service 业务代码越界；
8. 存在明显架构漂移；
9. 存在未授权依赖。

---

## 10. Expected Review Output

Qoder 必须创建：

```text
handoff/round-02/03-qoder-backend-java-independent-review.md
```

该文件必须是 Markdown Review Report，包含以下结构。

---

## 11. Required Review Report Format

```markdown
# Qoder Independent Review: backend-java Skeleton v1

## 1. Review Metadata

- Project:
- Round:
- Review Target:
- Review Agent:
- Review Date:
- Upstream Cursor Handoff:
- Upstream Codex Handoff:

## 2. Executive Summary

简要说明审查结论。

## 3. Review Scope

说明本次审查了哪些文件、目录和 Handoff。

## 4. Methodology

说明采用的审查方式，例如：

- Read task documents
- Read Cursor handoff
- Read Codex handoff
- Inspect backend-java source files
- Inspect Maven dependencies
- Inspect DTO and enum contracts
- Inspect API scope
- Inspect persistence scope
- Inspect external integration scope
- Inspect frontend / ai-service scope

## 5. Findings

### 5.1 Positive Findings

列出符合要求的地方。

### 5.2 Issues Found

如有问题，按以下格式列出：

#### Issue 1: Title

- Severity: LOW / MEDIUM / HIGH
- Category: Scope / Architecture / Dependency / Code Quality / Documentation / Validation
- Description:
- Evidence:
- Impact:
- Recommendation:

如果没有问题，写：

No blocking issues found.

### 5.3 Non-blocking Suggestions

列出非阻塞建议。

如果没有，写：

None.

## 6. Scope Compliance Assessment

逐项说明：

- ReviewTask business API:
- Persistence layer:
- Database dependency:
- External service integration:
- Frontend scope:
- ai-service scope:
- Dependency scope:
- Configuration scope:

## 7. Architecture Consistency Assessment

说明：

- backend-java responsibility alignment
- ai-service responsibility separation
- frontend responsibility separation
- DTO vs Entity separation
- Health endpoint positioning
- Configuration minimalism
- Readiness for Round 03

## 8. Code Quality Assessment

说明：

- Package structure
- Naming
- ApiResponse design
- DTO design
- Enum design
- Validation annotations
- Test design
- README accuracy

## 9. Validation Assessment

说明 Codex 验证是否充分：

- Java 17 / Maven environment
- mvn test
- Runtime health check
- Dependency check
- Scope check
- External integration check
- frontend / ai-service check

## 10. Risk Rating

- Overall Risk: LOW / MEDIUM / HIGH

说明评级理由。

## 11. Acceptance Recommendation

选择以下之一：

- Recommend accepting Round 02.
- Recommend accepting Round 02 with non-blocking suggestions.
- Do not recommend accepting Round 02 until blocking issues are resolved.

并说明理由。

## 12. Recommended Next Step

如果建议接受，写：

Return this review report to ChatGPT Architect. Recommend final Round 02 acceptance and preparation for Round 03 planning.

如果不建议接受，写：

Return this review report to ChatGPT Architect. Do not proceed to Round 03 until blocking issues are resolved.
```

---

## 12. Final Instruction

Qoder 只执行 Round 02 独立审查。

请严格遵守以下最终约束：

1. 只审查，不修改代码；
2. 不做最小修复；
3. 不实现 ReviewTask 业务 API；
4. 不实现数据库持久化；
5. 不引入 MyBatis-Plus；
6. 不引入 MySQL；
7. 不调用 `ai-service`；
8. 不实现 GitHub；
9. 不实现 Semgrep；
10. 不实现 LLM；
11. 不创建 frontend 业务代码；
12. 不创建 ai-service 业务代码；
13. 不进入 Round 03；
14. 完成后只提交 Qoder Review Report；
15. 等待 ChatGPT Architect 最终裁决。