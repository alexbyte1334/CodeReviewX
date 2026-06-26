# tasks/round-02/01-cursor-backend-java-skeleton.md

# Cursor Task: Round 02 - backend-java Skeleton v1

## 1. Task Metadata

- Project: CodeReviewX
- Round: Round 02
- Task Name: backend-java Skeleton v1
- Task File: `tasks/round-02/01-cursor-backend-java-skeleton.md`
- Target Agent: Cursor
- Target Agent Role: Round 02 主要编码执行 Agent
- Architect: ChatGPT
- Required Handoff File: `handoff/round-02/01-cursor-backend-java-skeleton-handoff.md`

---

## 2. Role Definition

你是 Cursor，本轮 Round 02 的主要编码执行 Agent。

你的职责是：

1. 在 `backend-java/` 下创建最小 Spring Boot 3 + Java 17 + Maven 项目骨架；
2. 实现基础健康检查接口；
3. 创建基础响应包装类、DTO 占位类和枚举；
4. 保证项目可以编译并通过最小测试；
5. 严格控制范围，不实现 ReviewTask 业务流程；
6. 严格控制范围，不实现数据库持久化；
7. 严格控制范围，不调用 `ai-service`；
8. 完成后创建 Markdown Handoff Report。

Agent 分工边界：

- Cursor：Round 02 主要编码执行 Agent；
- Codex：后续只负责仓库级验证、测试和最小修复；
- Qoder：后续只负责独立架构审查和代码审查；
- ChatGPT：项目总架构师，负责规划、审查和阶段性裁决。

禁止 Cursor 在本轮进入 Codex 或 Qoder 阶段。

---

## 3. Project Background

CodeReviewX 是一个面向 GitHub Pull Request 的智能代码审查与修复建议 Agent。

用户最终输入：

- GitHub 仓库地址；
- Pull Request 编号。

系统最终链路如下：

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

系统最终需要识别：

1. 潜在 Bug；
2. 安全风险；
3. 性能问题；
4. 测试缺失问题；
5. 代码风格问题。

但 Round 02 只做 `backend-java` 的最小工程骨架，不实现上述业务链路。

---

## 4. Current Round Goal

Round 02 名称：

```text
Round 02: backend-java Skeleton v1
```

本轮目标是创建 `backend-java` 的最小 Spring Boot 3 + Java 17 + Maven 项目骨架。

本轮必须完成：

1. 能编译；
2. 能启动；
3. 有基础健康检查接口；
4. 有基础 `ApiResponse`；
5. 有基础 DTO 占位类；
6. 有 Review 相关枚举；
7. 有基础配置文件；
8. 有 Spring context load 测试。

本轮明确不实现：

1. ReviewTask 业务 API；
2. 数据库持久化；
3. MyBatis-Plus；
4. MySQL；
5. `ai-service` 调用；
6. GitHub API；
7. Semgrep；
8. LLM；
9. frontend 业务代码；
10. ai-service 业务代码。

---

## 5. Allowed Scope

### 5.1 主要允许修改范围

Cursor 主要允许修改：

```text
backend-java/
```

允许在 `backend-java/` 下创建 Maven Spring Boot 项目骨架。

---

### 5.2 可选最小文档修改范围

如确有必要，可以最小修改以下文件：

```text
README.md
backend-java/README.md
docs/API.md
```

但只允许进行以下两类文档修正。

#### 5.2.1 Agent 分工描述对齐

在 `README.md` 和 `backend-java/README.md` 中统一说明：

1. Cursor 是 Round 02 主要编码执行 Agent；
2. Codex 后续只负责仓库级验证、测试和最小修复；
3. Qoder 后续只负责独立审查。

#### 5.2.2 API 说明补充

在 `docs/API.md` 中补充说明：

1. `GET /api/review-tasks/{id}` 的 `files` 项可以不返回 `patch`；
2. `patch` 主要由 `ai-service` 响应提供，并由 `backend-java` 保存到未来的 `review_file_change` 表；
3. 前端详情页默认展示文件路径、变更类型、增删行数和 issue 列表，不直接展示完整 patch。

注意：

- 以上文档修正是可选项；
- 如修改，必须在 Handoff 中说明原因；
- 不允许借文档修改扩大业务实现范围。

---

## 6. Forbidden Actions

Cursor 在 Round 02 中严禁执行以下行为。

---

### 6.1 禁止实现 ReviewTask 业务 API

不得实现以下接口：

```text
POST /api/review-tasks
GET /api/review-tasks
GET /api/review-tasks/{id}
```

本轮唯一允许新增的接口是：

```text
GET /api/health
```

`GET /api/health` 是基础设施健康检查接口，不属于 ReviewTask 业务 API。

---

### 6.2 禁止创建持久化层

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

### 6.3 禁止引入数据库相关依赖

不得添加：

1. MyBatis-Plus；
2. MyBatis；
3. MySQL Driver；
4. JPA；
5. Hibernate；
6. Flyway；
7. Liquibase。

---

### 6.4 禁止调用外部服务

不得实现：

1. `ai-service` 调用；
2. GitHub API 调用；
3. Semgrep 执行；
4. LLM 调用；
5. HTTP client 调用外部 review 服务；
6. Mock AI review 业务流程。

---

### 6.5 禁止修改 frontend / ai-service 业务代码

不得创建或修改以下目录下的业务代码：

```text
frontend/
ai-service/
```

除非只是 README 级别说明调整，否则不要触碰这两个模块。

---

### 6.6 禁止引入额外基础设施

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

### 6.7 禁止仓库操作

不得执行：

1. 初始化 Git；
2. 创建分支；
3. 提交 commit；
4. 进入 Codex 阶段；
5. 进入 Qoder 阶段；
6. 擅自扩大任务范围。

---

## 7. Required Files or Required Changes

请创建或更新以下目标结构：

```text
backend-java/
├── pom.xml
├── README.md
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── codereviewx/
│   │   │           └── backend/
│   │   │               ├── CodeReviewXBackendApplication.java
│   │   │               ├── common/
│   │   │               │   └── ApiResponse.java
│   │   │               ├── controller/
│   │   │               │   └── HealthController.java
│   │   │               ├── review/
│   │   │               │   ├── dto/
│   │   │               │   │   ├── CreateReviewTaskRequest.java
│   │   │               │   │   ├── ReviewTaskResponse.java
│   │   │               │   │   └── ReviewIssueResponse.java
│   │   │               │   └── enums/
│   │   │               │       ├── ReviewTaskStatus.java
│   │   │               │       ├── RiskLevel.java
│   │   │               │       ├── IssueType.java
│   │   │               │       ├── IssueSeverity.java
│   │   │               │       └── IssueSource.java
│   │   │               └── config/
│   │   │                   └── WebConfig.java
│   │   └── resources/
│   │       ├── application.yml
│   │       └── application-local.yml
│   └── test/
│       └── java/
│           └── com/
│               └── codereviewx/
│                   └── backend/
│                       └── CodeReviewXBackendApplicationTests.java
```

---

## 8. Maven Project Requirements

创建：

```text
backend-java/pom.xml
```

要求：

1. 使用 Maven；
2. 使用 Spring Boot 3；
3. 使用 Java 17；
4. 添加 `spring-boot-starter-web`；
5. 添加 `spring-boot-starter-validation`；
6. 添加 `spring-boot-starter-test`。

推荐 Maven 坐标：

```xml
<groupId>com.codereviewx</groupId>
<artifactId>backend-java</artifactId>
<version>0.0.1-SNAPSHOT</version>
<name>backend-java</name>
<description>CodeReviewX backend service</description>
```

禁止添加：

1. MyBatis-Plus；
2. MySQL Driver；
3. Lombok；
4. Swagger / OpenAPI；
5. Spring Security；
6. Redis；
7. MQ；
8. Docker 插件；
9. 代码生成插件；
10. 外部 SDK。

---

## 9. Required Implementation Details

### 9.1 Spring Boot 入口类

创建文件：

```text
backend-java/src/main/java/com/codereviewx/backend/CodeReviewXBackendApplication.java
```

要求：

1. 使用标准 Spring Boot application entry point；
2. 包名必须为：

```java
package com.codereviewx.backend;
```

示意：

```java
@SpringBootApplication
public class CodeReviewXBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeReviewXBackendApplication.class, args);
    }
}
```

---

### 9.2 ApiResponse

创建文件：

```text
backend-java/src/main/java/com/codereviewx/backend/common/ApiResponse.java
```

字段：

```text
success
message
data
```

静态方法：

```text
success(data)
success(message, data)
failure(message)
```

要求：

1. 使用泛型；
2. 不使用 Lombok；
3. 提供必要构造方法、getter、setter；
4. 不添加业务状态码设计；
5. 不引入复杂异常体系。

推荐结构：

```java
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "OK", data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    public static <T> ApiResponse<T> failure(String message) {
        return new ApiResponse<>(false, message, null);
    }
}
```

---

### 9.3 HealthController

创建文件：

```text
backend-java/src/main/java/com/codereviewx/backend/controller/HealthController.java
```

接口：

```text
GET /api/health
```

响应示例：

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

要求：

1. 使用 `@RestController`；
2. 使用 `@RequestMapping("/api")` 或等价写法；
3. 只实现 `/health`；
4. 返回 `ApiResponse`；
5. `data` 可使用 `Map<String, String>`；
6. 不添加其他接口；
7. 不实现 ReviewTask 业务逻辑。

---

### 9.4 Review 枚举

创建目录：

```text
backend-java/src/main/java/com/codereviewx/backend/review/enums/
```

创建以下枚举。

#### 9.4.1 ReviewTaskStatus

文件：

```text
ReviewTaskStatus.java
```

枚举值只能是：

```java
PENDING,
RUNNING,
SUCCESS,
FAILED
```

不得添加额外值。

---

#### 9.4.2 RiskLevel

文件：

```text
RiskLevel.java
```

枚举值只能是：

```java
LOW,
MEDIUM,
HIGH
```

不得添加额外值。

---

#### 9.4.3 IssueType

文件：

```text
IssueType.java
```

枚举值只能是：

```java
BUG,
SECURITY,
PERFORMANCE,
TEST,
STYLE
```

不得添加额外值。

---

#### 9.4.4 IssueSeverity

文件：

```text
IssueSeverity.java
```

枚举值只能是：

```java
LOW,
MEDIUM,
HIGH
```

不得添加额外值。

---

#### 9.4.5 IssueSource

文件：

```text
IssueSource.java
```

枚举值只能是：

```java
LLM,
SEMGREP
```

不得添加额外值。

---

### 9.5 DTO 占位类

创建目录：

```text
backend-java/src/main/java/com/codereviewx/backend/review/dto/
```

本轮 DTO 只是占位和契约准备，不允许绑定数据库，不允许实现业务流程。

---

#### 9.5.1 CreateReviewTaskRequest

创建文件：

```text
CreateReviewTaskRequest.java
```

字段：

```text
repoUrl
prNumber
```

Validation 要求：

```text
repoUrl: not blank
prNumber: positive integer
```

建议类型：

```java
private String repoUrl;
private Integer prNumber;
```

建议注解：

```java
@NotBlank
@Positive
```

要求：

1. 使用 `jakarta.validation`；
2. 不使用 Lombok；
3. 提供 getter / setter；
4. 不创建使用该 DTO 的 Controller；
5. 不实现 `POST /api/review-tasks`。

---

#### 9.5.2 ReviewTaskResponse

创建文件：

```text
ReviewTaskResponse.java
```

字段：

```text
id
repoUrl
prNumber
status
summary
riskLevel
errorMessage
createdAt
updatedAt
issues
```

建议类型：

```java
private Long id;
private String repoUrl;
private Integer prNumber;
private ReviewTaskStatus status;
private String summary;
private RiskLevel riskLevel;
private String errorMessage;
private LocalDateTime createdAt;
private LocalDateTime updatedAt;
private List<ReviewIssueResponse> issues;
```

要求：

1. 不使用 Lombok；
2. 不添加持久化注解；
3. 不添加数据库映射；
4. 不创建 Entity；
5. 不实现业务 API。

---

#### 9.5.3 ReviewIssueResponse

创建文件：

```text
ReviewIssueResponse.java
```

字段：

```text
id
filePath
lineNumber
type
severity
title
description
suggestion
source
```

建议类型：

```java
private Long id;
private String filePath;
private Integer lineNumber;
private IssueType type;
private IssueSeverity severity;
private String title;
private String description;
private String suggestion;
private IssueSource source;
```

要求：

1. 不使用 Lombok；
2. 不添加持久化注解；
3. 不添加数据库映射；
4. 不创建 Entity；
5. 不实现业务 API。

---

### 9.6 WebConfig

创建文件：

```text
backend-java/src/main/java/com/codereviewx/backend/config/WebConfig.java
```

要求：

1. 添加 `@Configuration`；
2. 保持空配置或占位配置；
3. 注释说明该类预留给后续 Web 层配置扩展；
4. 不添加 CORS；
5. 不添加拦截器；
6. 不添加认证逻辑。

---

### 9.7 配置文件

创建：

```text
backend-java/src/main/resources/application.yml
backend-java/src/main/resources/application-local.yml
```

`application.yml` 建议内容：

```yaml
spring:
  application:
    name: codereviewx-backend
  profiles:
    active: local

server:
  port: ${BACKEND_PORT:8080}
```

`application-local.yml` 建议内容：

```yaml
# Local development placeholder configuration.
# Do not configure database or external services in Round 02.
```

要求：

1. 不配置 MySQL；
2. 不配置数据库连接池；
3. 不配置 MyBatis；
4. 不配置 ai-service 地址；
5. 不配置 GitHub token；
6. 不配置 Semgrep；
7. 不配置 LLM API Key。

---

### 9.8 测试

创建文件：

```text
backend-java/src/test/java/com/codereviewx/backend/CodeReviewXBackendApplicationTests.java
```

要求：

1. 只验证 Spring context loads；
2. 不依赖数据库；
3. 不依赖 Docker；
4. 不依赖 GitHub；
5. 不依赖 ai-service；
6. 不依赖 Semgrep；
7. 不依赖 LLM。

示意：

```java
@SpringBootTest
class CodeReviewXBackendApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

---

### 9.9 backend-java README

更新文件：

```text
backend-java/README.md
```

必须说明：

1. 当前模块处于 Round 02 backend skeleton 状态；
2. 当前已包含：
   - Spring Boot 入口类；
   - 健康检查接口；
   - ApiResponse 响应包装；
   - DTO 占位类；
   - Review 枚举；
   - 本地配置文件；
   - Spring context load 测试；
3. 当前未实现：
   - ReviewTask 业务 API；
   - 数据库持久化；
   - MyBatis-Plus；
   - MySQL；
   - ai-service 调用；
   - GitHub 集成；
   - Semgrep 集成；
   - LLM 集成；
   - 认证；
   - 前端业务逻辑。

---

## 10. Acceptance Criteria

### 10.1 Backend Skeleton 验收标准

- [ ] `backend-java/pom.xml` 存在；
- [ ] 使用 Spring Boot 3；
- [ ] 使用 Java 17；
- [ ] 使用 Maven 标准项目结构；
- [ ] Spring Boot 入口类存在；
- [ ] `GET /api/health` 存在；
- [ ] `ApiResponse` 存在；
- [ ] Review 枚举存在；
- [ ] Review 枚举值严格符合要求，没有额外值；
- [ ] DTO 占位类存在；
- [ ] DTO 不包含持久化注解；
- [ ] `application.yml` 存在；
- [ ] `application-local.yml` 存在；
- [ ] Spring context load 测试存在；
- [ ] `mvn test` 通过，或失败原因在 Handoff 中清晰说明。

---

### 10.2 Scope Control 验收标准

- [ ] 未实现 `POST /api/review-tasks`；
- [ ] 未实现 `GET /api/review-tasks`；
- [ ] 未实现 `GET /api/review-tasks/{id}`；
- [ ] 未创建 ReviewTask Entity；
- [ ] 未创建 ReviewFileChange Entity；
- [ ] 未创建 ReviewIssue Entity；
- [ ] 未创建 MyBatis Mapper；
- [ ] 未创建数据库迁移；
- [ ] 未添加 MyBatis-Plus；
- [ ] 未添加 MySQL Driver；
- [ ] 未添加 JPA；
- [ ] 未调用 ai-service；
- [ ] 未实现 GitHub API；
- [ ] 未实现 Semgrep；
- [ ] 未实现 LLM；
- [ ] 未创建 frontend 业务代码；
- [ ] 未创建 ai-service 业务代码；
- [ ] 未添加未批准依赖；
- [ ] 未修改真实 CI 构建流程；
- [ ] 未修改 docker-compose 为真实服务编排。

---

### 10.3 Documentation 验收标准

- [ ] `backend-java/README.md` 已更新为 Round 02 skeleton 状态；
- [ ] 已明确列出当前已实现内容；
- [ ] 已明确列出当前未实现内容；
- [ ] 如修改 `README.md` 或 `docs/API.md`，修改内容是最小且符合允许范围；
- [ ] 所有可选文档修改已在 Handoff 中说明。

---

## 11. Suggested Checks

完成实现后，建议执行以下检查。

### 11.1 文件结构检查

```bash
find backend-java -type f | sort
```

确认文件结构符合本任务要求。

---

### 11.2 Maven 测试

```bash
cd backend-java
mvn test
```

要求：

1. 测试应通过；
2. 如失败，必须在 Handoff 中说明：
   - 失败命令；
   - 失败原因；
   - 是否与本轮实现有关；
   - 是否需要 Codex 后续最小修复。

---

### 11.3 可选启动检查

```bash
cd backend-java
mvn spring-boot:run
```

启动后访问：

```text
GET http://localhost:8080/api/health
```

期望响应：

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

如没有执行本地启动检查，需要在 Handoff 中说明未执行。

---

### 11.4 范围检查

```bash
find ai-service frontend -type f | sort
```

期望：

1. 不应新增 ai-service 业务代码；
2. 不应新增 frontend 业务代码；
3. 如仅存在 README 文件，符合预期。

---

### 11.5 依赖污染检查

```bash
find . \( -name "package.json" -o -name "requirements.txt" -o -name "pyproject.toml" \)
```

期望：

1. Round 02 不应新增 frontend 依赖文件；
2. Round 02 不应新增 Python 依赖文件；
3. 如发现新增文件，必须在 Handoff 中解释原因；未获授权时应删除。

---

## 12. Required Handoff Format

完成 Round 02 后，必须创建：

```text
handoff/round-02/01-cursor-backend-java-skeleton-handoff.md
```

Handoff Report 必须使用以下 Markdown 格式：

```markdown
# Handoff Report: backend-java Skeleton v1

## 1. Task Metadata

- Project:
- Round:
- Task:
- Target Agent:
- Execution Date:
- Repository Branch:

## 2. Execution Summary

简要说明本轮完成了什么。

## 3. Files Created

列出所有新建文件。

## 4. Files Modified

列出所有修改文件。

## 5. Backend Skeleton Summary

说明以下内容：

- Maven / Spring Boot setup
- Java 17 setup
- Application entry class
- Health endpoint
- ApiResponse
- DTO placeholders
- Review enums
- Configuration files
- Context load test
- README update

## 6. Scope Compliance

必须逐项确认：

- No ReviewTask business API implemented
- No database persistence implemented
- No Entity classes created
- No MyBatis Mapper created
- No migration files created
- No MyBatis-Plus added
- No MySQL Driver added
- No ai-service call implemented
- No GitHub API integration implemented
- No Semgrep integration implemented
- No LLM integration implemented
- No frontend business code created
- No ai-service business code created
- No unapproved dependency introduced

## 7. Acceptance Criteria Checklist

使用 checklist 格式逐项列出验收标准完成情况。

## 8. Checks Performed

记录实际执行过的检查命令和结果，例如：

- find backend-java -type f | sort
- cd backend-java && mvn test
- cd backend-java && mvn spring-boot:run
- GET /api/health
- find ai-service frontend -type f | sort
- dependency pollution check

如某项未执行，必须写明：

- Not executed
- Reason

## 9. Known Issues or Limitations

列出已知问题或限制。

如果没有，写：

None.

## 10. Deviations from Task

列出是否存在偏离任务要求的地方。

如果没有，写：

None.

## 11. Recommended Next Step

Return this handoff report to ChatGPT Architect for review. Do not proceed to Codex unless ChatGPT Architect approves.
```

---

## 13. Final Instruction

Cursor 只执行 Round 02。

请严格遵守以下最终约束：

1. 只实现 `backend-java` skeleton；
2. 不实现 ReviewTask 业务 API；
3. 不实现数据库持久化；
4. 不引入 MyBatis-Plus；
5. 不引入 MySQL；
6. 不调用 ai-service；
7. 不实现 GitHub；
8. 不实现 Semgrep；
9. 不实现 LLM；
10. 不创建 frontend 业务代码；
11. 不创建 ai-service 业务代码；
12. 不进入 Codex；
13. 不进入 Qoder；
14. 完成后只提交 Cursor Handoff Report，等待 ChatGPT Architect 审查。