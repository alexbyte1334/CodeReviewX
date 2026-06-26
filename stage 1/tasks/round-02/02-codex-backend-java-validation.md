# tasks/round-02/02-codex-backend-java-validation.md

# Codex Task: Round 02 - backend-java Validation

## 1. Task Metadata

- Project: CodeReviewX
- Round: Round 02
- Task Name: backend-java Skeleton Validation
- Task File: `tasks/round-02/02-codex-backend-java-validation.md`
- Target Agent: Codex
- Target Agent Role: 仓库级验证、测试和最小修复 Agent
- Architect: ChatGPT
- Upstream Agent: Cursor
- Upstream Handoff File: `handoff/round-02/01-cursor-backend-java-skeleton-handoff.md`
- Required Handoff File: `handoff/round-02/02-codex-backend-java-validation-handoff.md`

---

## 2. Role Definition

你是 Codex，本轮 Round 02 的仓库级验证、测试和最小修复 Agent。

你的职责是：

1. 阅读 Cursor 的 Round 02 Handoff；
2. 配置或确认 Java 17 + Maven 环境；
3. 验证 `backend-java` Spring Boot skeleton 是否可编译；
4. 执行 `mvn test`；
5. 可选启动服务并验证 `GET /api/health`；
6. 检查 Cursor 是否违反 Round 02 范围；
7. 如发现小型编译错误或配置错误，只允许做最小修复；
8. 完成后创建 Markdown Handoff Report。

Agent 分工边界：

- Cursor：已完成 Round 02 主要编码执行；
- Codex：本轮只做验证、测试和最小修复；
- Qoder：后续只做独立审查，不直接编码；
- ChatGPT：项目总架构师，负责审查和阶段性裁决。

你不得扩大任务范围。  
你不得实现新业务功能。  
你不得进入 Qoder 阶段。  
你不得开始 Round 03。

---

## 3. Project Background

CodeReviewX 是一个面向 GitHub Pull Request 的智能代码审查与修复建议 Agent。

最终系统链路如下：

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

但 Round 02 只验证 `backend-java` 的最小 Spring Boot skeleton。

Round 02 不实现 ReviewTask 业务流程，不实现数据库持久化，不调用 `ai-service`，不集成 GitHub / Semgrep / LLM。

---

## 4. Upstream Cursor Handoff Summary

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

Cursor 未执行：

```bash
cd backend-java && mvn test
```

原因是 Cursor 执行环境中没有可用 Java 17 和 Maven。

因此 Codex 的核心任务是：

1. 配置或确认 Java 17 + Maven 环境；
2. 补跑验证命令；
3. 判断 Round 02 是否可进入 Qoder 独立审查；
4. 如有必要，只做最小修复。

---

## 5. Current Task Goal

本任务目标：

验证 Cursor 创建的 `backend-java` skeleton 是否符合 Round 02 要求。

必须验证：

1. Java 17 环境可用；
2. Maven 可用；
3. `backend-java` 可以执行 `mvn test`；
4. Spring context load test 通过；
5. `pom.xml` 只包含授权依赖；
6. `GET /api/health` 存在；
7. DTO 和枚举符合要求；
8. 未实现 ReviewTask 业务 API；
9. 未引入数据库持久化；
10. 未调用 `ai-service`；
11. 未实现 GitHub / Semgrep / LLM；
12. 未新增 frontend 或 ai-service 业务代码。

---

## 6. Allowed Scope

### 6.1 允许读取和验证的范围

Codex 可以读取整个仓库，用于验证范围控制。

重点检查：

```text
backend-java/
frontend/
ai-service/
docs/
README.md
docker-compose.yml
.github/
```

---

### 6.2 允许执行的命令范围

允许执行：

```bash
java -version
mvn -version
find backend-java -type f | sort
cd backend-java && mvn test
cd backend-java && mvn spring-boot:run
curl http://localhost:8080/api/health
find ai-service frontend -type f | sort
find . \( -name "package.json" -o -name "requirements.txt" -o -name "pyproject.toml" \)
```

可根据环境使用等价命令，但必须在 Handoff 中记录实际命令和结果。

---

### 6.3 允许的最小修复范围

如果发现以下问题，Codex 可以做最小修复：

1. Java package 声明与目录不一致；
2. 缺少 import；
3. 测试类缺少 JUnit import；
4. DTO 缺少 getter / setter 导致 Jackson 或测试问题；
5. `pom.xml` 中 Spring Boot / Java 配置小错误；
6. `application.yml` YAML 缩进错误；
7. `HealthController` 返回结构与要求轻微不符；
8. 编译错误级别的小型 typo；
9. `README.md` 中明显错误的 Round 02 状态描述。

最小修复原则：

- 只修复导致编译、测试或基础验证失败的问题；
- 不做重构；
- 不引入新设计；
- 不添加未授权依赖；
- 不实现业务功能。

---

## 7. Forbidden Actions

Codex 严禁执行以下行为。

### 7.1 禁止实现 ReviewTask 业务 API

不得实现：

```text
POST /api/review-tasks
GET /api/review-tasks
GET /api/review-tasks/{id}
```

本轮唯一允许存在的接口是：

```text
GET /api/health
```

---

### 7.2 禁止创建持久化层

不得创建：

1. ReviewTask Entity；
2. ReviewFileChange Entity；
3. ReviewIssue Entity；
4. MyBatis Mapper；
5. Repository；
6. DAO；
7. 数据库迁移文件；
8. SQL schema 文件。

---

### 7.3 禁止引入数据库依赖

不得添加：

1. MyBatis-Plus；
2. MyBatis；
3. MySQL Driver；
4. PostgreSQL Driver；
5. JPA；
6. Hibernate；
7. Flyway；
8. Liquibase。

---

### 7.4 禁止调用外部服务

不得实现：

1. `ai-service` 调用；
2. GitHub API 调用；
3. Semgrep 执行；
4. LLM 调用；
5. HTTP client 调用 review 服务；
6. Mock AI review 业务流程。

---

### 7.5 禁止修改 frontend / ai-service 业务代码

不得创建或修改以下目录下的业务代码：

```text
frontend/
ai-service/
```

如果发现 Cursor 已经越界创建了业务代码，Codex 只记录问题，不要扩大实现。是否回滚由 ChatGPT Architect 裁决。

---

### 7.6 禁止引入额外基础设施

不得添加：

1. Redis；
2. MQ；
3. Kafka；
4. RabbitMQ；
5. RAG；
6. Vector DB；
7. Kubernetes；
8. Spring Security；
9. Swagger / OpenAPI；
10. Lombok；
11. Docker 插件；
12. 代码生成插件；
13. 外部 SDK；
14. 真实 docker-compose 服务；
15. 真实 GitHub Actions 构建流程。

---

### 7.7 禁止仓库操作

不得执行：

1. 初始化 Git；
2. 创建分支；
3. 提交 commit；
4. 进入 Qoder 阶段；
5. 开始 Round 03；
6. 擅自扩大任务范围。

---

## 8. Environment Setup Requirements

Codex 必须首先检查环境。

### 8.1 检查 Java

执行：

```bash
java -version
```

要求：

```text
Java 17
```

如果未安装 Java 17：

1. 优先使用当前环境支持的标准方式安装或切换到 Java 17；
2. 不要修改项目代码来适配低版本 Java；
3. 不要把项目降级到 Java 8 / Java 11；
4. 在 Handoff 中记录环境安装或切换方式。

### 8.2 检查 Maven

执行：

```bash
mvn -version
```

要求：

```text
Maven 3.8+ recommended
```

如果未安装 Maven：

1. 优先使用当前环境支持的标准方式安装 Maven；
2. 不要改用 Gradle；
3. 不要改写项目构建系统；
4. 在 Handoff 中记录安装或配置方式。

### 8.3 环境约束

不得因为环境缺失而修改：

1. Java version；
2. Spring Boot version；
3. Maven project structure；
4. dependency scope；
5. source code package structure。

正确做法是配置环境，而不是降低项目要求。

---

## 9. Required Validation Steps

### 9.1 阅读 Cursor Handoff

先阅读：

```text
handoff/round-02/01-cursor-backend-java-skeleton-handoff.md
```

确认 Cursor 声称完成内容和未执行检查。

---

### 9.2 文件结构检查

执行：

```bash
find backend-java -type f | sort
```

期望至少包含：

```text
backend-java/README.md
backend-java/pom.xml
backend-java/src/main/java/com/codereviewx/backend/CodeReviewXBackendApplication.java
backend-java/src/main/java/com/codereviewx/backend/common/ApiResponse.java
backend-java/src/main/java/com/codereviewx/backend/config/WebConfig.java
backend-java/src/main/java/com/codereviewx/backend/controller/HealthController.java
backend-java/src/main/java/com/codereviewx/backend/review/dto/CreateReviewTaskRequest.java
backend-java/src/main/java/com/codereviewx/backend/review/dto/ReviewIssueResponse.java
backend-java/src/main/java/com/codereviewx/backend/review/dto/ReviewTaskResponse.java
backend-java/src/main/java/com/codereviewx/backend/review/enums/IssueSeverity.java
backend-java/src/main/java/com/codereviewx/backend/review/enums/IssueSource.java
backend-java/src/main/java/com/codereviewx/backend/review/enums/IssueType.java
backend-java/src/main/java/com/codereviewx/backend/review/enums/ReviewTaskStatus.java
backend-java/src/main/java/com/codereviewx/backend/review/enums/RiskLevel.java
backend-java/src/main/resources/application-local.yml
backend-java/src/main/resources/application.yml
backend-java/src/test/java/com/codereviewx/backend/CodeReviewXBackendApplicationTests.java
```

---

### 9.3 Maven 测试

执行：

```bash
cd backend-java
mvn test
```

要求：

1. Maven dependency resolve 成功；
2. 编译成功；
3. Spring context load test 通过；
4. 无数据库、Docker、GitHub、ai-service、Semgrep、LLM 依赖。

如果失败：

1. 判断是否是环境问题；
2. 判断是否是项目配置问题；
3. 判断是否可以最小修复；
4. 若可最小修复，修复后重新执行 `mvn test`；
5. 若不可修复或超出范围，停止修改并在 Handoff 中说明。

---

### 9.4 可选启动验证

如环境允许，执行：

```bash
cd backend-java
mvn spring-boot:run
```

然后访问：

```bash
curl http://localhost:8080/api/health
```

期望响应结构：

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "status": "UP",
    "service": "backend-java"
  }
}
```

说明：

1. 如果执行启动验证，必须在 Handoff 中记录响应；
2. 如果未执行启动验证，必须说明原因；
3. 启动验证不是替代 `mvn test` 的步骤；
4. 不要为启动验证添加业务代码或额外依赖。

---

### 9.5 依赖检查

检查 `backend-java/pom.xml`。

允许依赖仅限：

```text
spring-boot-starter-web
spring-boot-starter-validation
spring-boot-starter-test
```

允许 Spring Boot Maven Plugin。

不得出现：

```text
mybatis
mybatis-plus
mysql
postgresql
spring-boot-starter-data-jpa
hibernate
flyway
liquibase
lombok
springdoc
swagger
spring-security
redis
kafka
rabbitmq
openai
langchain
github api sdk
semgrep sdk
```

如发现未授权依赖：

1. 如果明显是 Cursor 误加，Codex 可以删除；
2. 删除后必须重新执行 `mvn test`；
3. 在 Handoff 中记录修复。

---

### 9.6 API 范围检查

搜索是否存在未授权 ReviewTask API。

建议执行：

```bash
grep -R "review-tasks" -n backend-java/src || true
grep -R "@PostMapping" -n backend-java/src || true
grep -R "@GetMapping" -n backend-java/src || true
grep -R "@RequestMapping" -n backend-java/src || true
```

允许存在：

```text
/api/health
```

不得存在：

```text
/api/review-tasks
POST /api/review-tasks
GET /api/review-tasks
GET /api/review-tasks/{id}
```

---

### 9.7 持久化范围检查

建议执行：

```bash
grep -R "Entity" -n backend-java/src || true
grep -R "Mapper" -n backend-java/src || true
grep -R "Repository" -n backend-java/src || true
grep -R "DataSource" -n backend-java/src backend-java/pom.xml || true
grep -R "mybatis" -ni backend-java || true
grep -R "mysql" -ni backend-java || true
grep -R "jpa" -ni backend-java || true
```

不得出现实际持久化实现。

注意：

- README 中出现“not implemented”等说明性文字可以接受；
- Java 代码中不得出现 Entity / Mapper / Repository 业务实现。

---

### 9.8 外部集成范围检查

建议执行：

```bash
grep -R "ai-service" -ni backend-java/src backend-java/pom.xml backend-java/src/main/resources || true
grep -R "github" -ni backend-java/src backend-java/pom.xml backend-java/src/main/resources || true
grep -R "semgrep" -ni backend-java/src backend-java/pom.xml backend-java/src/main/resources || true
grep -R "llm" -ni backend-java/src backend-java/pom.xml backend-java/src/main/resources || true
grep -R "openai" -ni backend-java/src backend-java/pom.xml backend-java/src/main/resources || true
```

不得出现实际调用或配置。

说明性 README 内容可以接受。

---

### 9.9 frontend / ai-service 范围检查

执行：

```bash
find ai-service frontend -type f | sort
```

期望：

```text
ai-service/README.md
frontend/README.md
```

如发现新增业务文件，必须记录。

---

### 9.10 依赖污染检查

执行：

```bash
find . \( -name "package.json" -o -name "requirements.txt" -o -name "pyproject.toml" \)
```

Round 02 不应新增：

1. frontend 依赖文件；
2. Python 依赖文件；
3. Node.js 项目文件。

---

## 10. Required Minimal Fix Policy

Codex 可以做最小修复，但必须满足以下条件：

1. 修复范围仅限 `backend-java/`；
2. 修复目标仅限让 skeleton 编译、测试通过；
3. 不引入任何未授权依赖；
4. 不实现任何业务 API；
5. 不创建任何持久化层；
6. 不调用任何外部服务；
7. 不修改架构方向；
8. 修复后必须重新执行相关验证命令；
9. 所有修复必须在 Handoff 中列出。

允许的最小修复示例：

```text
修正 import
修正 package 声明
补充 getter/setter
修正 YAML 缩进
修正 Maven parent / plugin 配置
修正测试类注解或 import
修正健康检查返回 message 字段
```

禁止的修复示例：

```text
新增 ReviewTaskController
新增 Service
新增 Entity
新增 Mapper
新增数据库配置
新增 ai-service client
新增 GitHub client
新增 Semgrep runner
新增 LLM client
新增 Swagger
新增 Security
新增 Lombok
重构项目结构
```

---

## 11. Acceptance Criteria

### 11.1 Environment

- [ ] `java -version` 显示 Java 17；
- [ ] `mvn -version` 可用；
- [ ] 如安装或切换环境，已在 Handoff 中说明。

### 11.2 Build and Test

- [ ] `cd backend-java && mvn test` 执行成功；
- [ ] Spring context load test 通过；
- [ ] Maven 依赖解析成功；
- [ ] 无数据库依赖要求；
- [ ] 无外部服务依赖要求。

### 11.3 Optional Runtime Check

- [ ] 如执行 `mvn spring-boot:run`，服务可启动；
- [ ] 如执行 `curl http://localhost:8080/api/health`，返回结构符合预期；
- [ ] 如未执行运行时检查，已说明原因。

### 11.4 Scope Control

- [ ] 未实现 `POST /api/review-tasks`；
- [ ] 未实现 `GET /api/review-tasks`；
- [ ] 未实现 `GET /api/review-tasks/{id}`；
- [ ] 未创建 Entity；
- [ ] 未创建 Mapper；
- [ ] 未创建 Repository；
- [ ] 未创建数据库迁移；
- [ ] 未添加 MyBatis-Plus；
- [ ] 未添加 MySQL Driver；
- [ ] 未添加 JPA；
- [ ] 未调用 `ai-service`；
- [ ] 未实现 GitHub API；
- [ ] 未实现 Semgrep；
- [ ] 未实现 LLM；
- [ ] 未创建 frontend 业务代码；
- [ ] 未创建 ai-service 业务代码；
- [ ] 未添加未批准依赖。

### 11.5 DTO and Enum Contract

- [ ] `ReviewTaskStatus` 仅包含 `PENDING`, `RUNNING`, `SUCCESS`, `FAILED`；
- [ ] `RiskLevel` 仅包含 `LOW`, `MEDIUM`, `HIGH`；
- [ ] `IssueType` 仅包含 `BUG`, `SECURITY`, `PERFORMANCE`, `TEST`, `STYLE`；
- [ ] `IssueSeverity` 仅包含 `LOW`, `MEDIUM`, `HIGH`；
- [ ] `IssueSource` 仅包含 `LLM`, `SEMGREP`；
- [ ] DTO 不包含持久化注解；
- [ ] `CreateReviewTaskRequest` 包含 `repoUrl` 和 `prNumber`；
- [ ] `CreateReviewTaskRequest.repoUrl` 使用 not blank validation；
- [ ] `CreateReviewTaskRequest.prNumber` 使用 positive validation；
- [ ] `ReviewTaskResponse` 字段完整；
- [ ] `ReviewIssueResponse` 字段完整。

### 11.6 Handoff

- [ ] 创建 `handoff/round-02/02-codex-backend-java-validation-handoff.md`；
- [ ] 记录所有执行命令；
- [ ] 记录所有命令结果；
- [ ] 记录是否做过最小修复；
- [ ] 如做过修复，列出修改文件和原因；
- [ ] 给出是否建议进入 Qoder 独立审查的结论。

---

## 12. Required Handoff Format

完成验证后，必须创建：

```text
handoff/round-02/02-codex-backend-java-validation-handoff.md
```

Handoff Report 必须使用以下 Markdown 格式：

```markdown
# Handoff Report: backend-java Validation

## 1. Task Metadata

- Project:
- Round:
- Task:
- Target Agent:
- Execution Date:
- Repository Branch:
- Upstream Handoff:

## 2. Execution Summary

简要说明本轮完成了哪些验证，是否做过最小修复。

## 3. Environment Check

记录：

- java -version output
- mvn -version output
- 是否安装或切换环境
- 环境配置说明

## 4. Files Reviewed

列出重点检查过的文件和目录。

## 5. Files Modified

列出 Codex 修改过的文件。

如果没有修改，写：

None.

## 6. Minimal Fixes Applied

如有修复，逐项说明：

- Problem
- File
- Fix
- Reason
- Verification after fix

如果没有修复，写：

None.

## 7. Build and Test Results

必须记录：

- Command: cd backend-java && mvn test
- Result:
- Key output summary:

如失败，必须记录：

- Failure reason
- Whether it is environment-related or code-related
- Whether Codex attempted minimal fix
- Remaining blocker

## 8. Runtime Check Results

如果执行启动验证，记录：

- Command: cd backend-java && mvn spring-boot:run
- Command: curl http://localhost:8080/api/health
- Response:

如果没有执行，写：

- Not executed
- Reason

## 9. Scope Compliance Check

必须逐项确认：

- No ReviewTask business API implemented
- No database persistence implemented
- No Entity classes created
- No MyBatis Mapper created
- No Repository created
- No migration files created
- No MyBatis-Plus added
- No MySQL Driver added
- No JPA added
- No ai-service call implemented
- No GitHub API integration implemented
- No Semgrep integration implemented
- No LLM integration implemented
- No frontend business code created
- No ai-service business code created
- No unapproved dependency introduced

## 10. DTO and Enum Contract Check

逐项确认：

- ReviewTaskStatus values
- RiskLevel values
- IssueType values
- IssueSeverity values
- IssueSource values
- CreateReviewTaskRequest fields and validation
- ReviewTaskResponse fields
- ReviewIssueResponse fields

## 11. Commands Executed

列出所有实际执行的命令和结果摘要。

至少应包含：

- java -version
- mvn -version
- find backend-java -type f | sort
- cd backend-java && mvn test
- find ai-service frontend -type f | sort
- dependency pollution check

## 12. Known Issues or Limitations

列出已知问题。

如果没有，写：

None.

## 13. Deviations from Task

列出是否存在偏离任务要求的地方。

如果没有，写：

None.

## 14. Validation Decision

选择一个结论：

- Passed: Recommend proceeding to Qoder independent review.
- Passed with minimal fixes: Recommend proceeding to Qoder independent review.
- Blocked: Do not proceed to Qoder. Return to ChatGPT Architect for decision.

并说明理由。

## 15. Recommended Next Step

如果验证通过，写：

Return this handoff report to ChatGPT Architect. Recommend proceeding to Qoder independent review for Round 02.

如果验证阻塞，写：

Return this handoff report to ChatGPT Architect. Do not proceed to Qoder until the blocker is resolved.
```

---

## 13. Final Instruction

Codex 只执行 Round 02 验证。

请严格遵守以下最终约束：

1. 优先配置或确认 Java 17 + Maven 环境；
2. 必须执行 `cd backend-java && mvn test`；
3. 可选执行启动验证和 `/api/health` 验证；
4. 只允许最小修复；
5. 不实现 ReviewTask 业务 API；
6. 不实现数据库持久化；
7. 不引入 MyBatis-Plus；
8. 不引入 MySQL；
9. 不调用 `ai-service`；
10. 不实现 GitHub；
11. 不实现 Semgrep；
12. 不实现 LLM；
13. 不创建 frontend 业务代码；
14. 不创建 ai-service 业务代码；
15. 不进入 Qoder；
16. 不开始 Round 03；
17. 完成后只提交 Codex Handoff Report，等待 ChatGPT Architect 审查。