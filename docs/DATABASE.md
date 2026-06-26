# CodeReviewX Database Design v1.0

> 本文档定义 CodeReviewX MVP 阶段的数据库表结构、字段类型、索引和枚举设计。
> 数据库：MySQL 8，字符集：utf8mb4。
> 当前状态：Round 01 仅为逻辑 schema 设计；仓库中未创建 SQL migration，本文中的 SQL 片段仅作后续实现参考。

---

## 1. 数据库信息

| 项目 | 值 |
|---|---|
| 数据库名 | `codereviewx` |
| 字符集 | `utf8mb4` |
| 排序规则 | `utf8mb4_unicode_ci` |
| 引擎 | InnoDB |

---

## 2. 表结构

### 2.1 review_task

ReviewTask 任务主表，保存任务元信息、状态和 Review 结果摘要。

```sql
CREATE TABLE review_task (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '任务 ID',
    repo_url    VARCHAR(500) NOT NULL                COMMENT 'GitHub 仓库地址',
    pr_number   INT          NOT NULL                COMMENT 'Pull Request 编号',
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '任务状态: PENDING/RUNNING/SUCCESS/FAILED',
    summary     TEXT                                 COMMENT 'Review 总结',
    risk_level  VARCHAR(10)                          COMMENT '风险等级: LOW/MEDIUM/HIGH',
    error_message TEXT                               COMMENT '失败原因',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Review 任务表';
```

**字段说明：**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | BIGINT AUTO_INCREMENT | 是 | 主键 |
| `repo_url` | VARCHAR(500) | 是 | GitHub 仓库地址 |
| `pr_number` | INT | 是 | PR 编号 |
| `status` | VARCHAR(20) | 是 | 任务状态，见枚举 |
| `summary` | TEXT | 否 | Review 总结，任务成功后填充 |
| `risk_level` | VARCHAR(10) | 否 | 风险等级，任务成功后填充 |
| `error_message` | TEXT | 否 | 失败原因，FAILED 状态时填充 |
| `created_at` | DATETIME | 是 | 创建时间，自动填充 |
| `updated_at` | DATETIME | 是 | 更新时间，自动维护 |

---

### 2.2 review_file_change

PR 变更文件表，保存每个任务涉及的文件变更信息。

```sql
CREATE TABLE review_file_change (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '文件变更 ID',
    task_id     BIGINT       NOT NULL                COMMENT '关联任务 ID',
    file_path   VARCHAR(500) NOT NULL                COMMENT '文件路径',
    change_type VARCHAR(20)  NOT NULL                COMMENT '变更类型: added/modified/deleted',
    additions   INT          NOT NULL DEFAULT 0      COMMENT '新增行数',
    deletions   INT          NOT NULL DEFAULT 0      COMMENT '删除行数',
    patch       TEXT                                 COMMENT 'diff 片段',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_task_id (task_id),
    CONSTRAINT fk_file_change_task FOREIGN KEY (task_id) REFERENCES review_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PR 文件变更表';
```

**字段说明：**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | BIGINT AUTO_INCREMENT | 是 | 主键 |
| `task_id` | BIGINT | 是 | 关联 review_task.id |
| `file_path` | VARCHAR(500) | 是 | 文件路径 |
| `change_type` | VARCHAR(20) | 是 | `added` / `modified` / `deleted` |
| `additions` | INT | 是 | 新增行数 |
| `deletions` | INT | 是 | 删除行数 |
| `patch` | TEXT | 否 | diff 片段，MVP 阶段使用 TEXT |
| `created_at` | DATETIME | 是 | 创建时间 |

---

### 2.3 review_issue

Review 问题表，保存 LLM 和 Semgrep 分析出的所有问题。

```sql
CREATE TABLE review_issue (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'issue ID',
    task_id     BIGINT       NOT NULL                COMMENT '关联任务 ID',
    file_path   VARCHAR(500) NOT NULL                COMMENT '问题所在文件路径',
    line_number INT                                  COMMENT '问题行号',
    type        VARCHAR(20)  NOT NULL                COMMENT '问题类型: BUG/SECURITY/PERFORMANCE/TEST/STYLE',
    severity    VARCHAR(10)  NOT NULL                COMMENT '严重程度: LOW/MEDIUM/HIGH',
    title       VARCHAR(255) NOT NULL                COMMENT '问题标题',
    description TEXT         NOT NULL                COMMENT '问题描述',
    suggestion  TEXT                                 COMMENT '修复建议',
    source      VARCHAR(20)  NOT NULL                COMMENT '来源: LLM/SEMGREP',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_task_id (task_id),
    INDEX idx_severity (severity),
    INDEX idx_type (type),
    CONSTRAINT fk_issue_task FOREIGN KEY (task_id) REFERENCES review_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Review 问题表';
```

**字段说明：**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | BIGINT AUTO_INCREMENT | 是 | 主键 |
| `task_id` | BIGINT | 是 | 关联 review_task.id |
| `file_path` | VARCHAR(500) | 是 | 问题所在文件路径 |
| `line_number` | INT | 否 | 问题行号（Semgrep 通常有，LLM 可能没有） |
| `type` | VARCHAR(20) | 是 | 问题类型，见枚举 |
| `severity` | VARCHAR(10) | 是 | 严重程度，见枚举 |
| `title` | VARCHAR(255) | 是 | 问题标题 |
| `description` | TEXT | 是 | 问题描述 |
| `suggestion` | TEXT | 否 | 修复建议 |
| `source` | VARCHAR(20) | 是 | 来源：`LLM` / `SEMGREP` |
| `created_at` | DATETIME | 是 | 创建时间 |

---

## 3. 参考 SQL（非 Migration）

以下 SQL 仅用于表达计划中的表结构，不是 Round 01 migration，也不会在 Round 01 执行。

```sql
-- 创建数据库
CREATE DATABASE IF NOT EXISTS codereviewx
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE codereviewx;

-- review_task
CREATE TABLE IF NOT EXISTS review_task (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    repo_url      VARCHAR(500) NOT NULL,
    pr_number     INT          NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    summary       TEXT,
    risk_level    VARCHAR(10),
    error_message TEXT,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- review_file_change
CREATE TABLE IF NOT EXISTS review_file_change (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    task_id     BIGINT       NOT NULL,
    file_path   VARCHAR(500) NOT NULL,
    change_type VARCHAR(20)  NOT NULL,
    additions   INT          NOT NULL DEFAULT 0,
    deletions   INT          NOT NULL DEFAULT 0,
    patch       TEXT,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_task_id (task_id),
    CONSTRAINT fk_file_change_task FOREIGN KEY (task_id) REFERENCES review_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- review_issue
CREATE TABLE IF NOT EXISTS review_issue (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    task_id     BIGINT       NOT NULL,
    file_path   VARCHAR(500) NOT NULL,
    line_number INT,
    type        VARCHAR(20)  NOT NULL,
    severity    VARCHAR(10)  NOT NULL,
    title       VARCHAR(255) NOT NULL,
    description TEXT         NOT NULL,
    suggestion  TEXT,
    source      VARCHAR(20)  NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_task_id (task_id),
    INDEX idx_severity (severity),
    INDEX idx_type (type),
    CONSTRAINT fk_issue_task FOREIGN KEY (task_id) REFERENCES review_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 4. 枚举值约束

### TaskStatus

| 值 | 说明 |
|---|---|
| `PENDING` | 任务已创建，尚未执行 |
| `RUNNING` | 任务执行中 |
| `SUCCESS` | 任务执行成功 |
| `FAILED` | 任务执行失败 |

### RiskLevel

| 值 | 说明 |
|---|---|
| `LOW` | 低风险 |
| `MEDIUM` | 中风险 |
| `HIGH` | 高风险 |

### IssueType

| 值 | 说明 |
|---|---|
| `BUG` | 潜在 Bug |
| `SECURITY` | 安全风险 |
| `PERFORMANCE` | 性能问题 |
| `TEST` | 测试缺失 |
| `STYLE` | 代码风格 |

### IssueSeverity

| 值 | 说明 |
|---|---|
| `LOW` | 低严重程度 |
| `MEDIUM` | 中严重程度 |
| `HIGH` | 高严重程度 |

### ChangeType

| 值 | 说明 |
|---|---|
| `added` | 新增文件 |
| `modified` | 修改文件 |
| `deleted` | 删除文件 |

### IssueSource

| 值 | 说明 |
|---|---|
| `LLM` | 来自 LLM 分析 |
| `SEMGREP` | 来自 Semgrep 静态分析 |

---

## 5. MyBatis-Plus Entity 映射说明

backend-java 使用 MyBatis-Plus 作为 ORM。

**命名映射规则：**
- 数据库：`snake_case`（如 `task_id`）
- Java：`camelCase`（如 `taskId`）
- 使用 `@TableName`、`@TableId`、`@TableField` 注解显式映射

**实体类示例（ReviewTask）：**

```java
@TableName("review_task")
public class ReviewTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String repoUrl;
    private Integer prNumber;
    private String status;       // 使用 TaskStatus enum 转换
    private String summary;
    private String riskLevel;    // 使用 RiskLevel enum 转换
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

---

## 6. 注意事项

1. `patch` 字段 MVP 阶段使用 `TEXT`（最大 65535 字节），如遇超大 diff 需考虑截断或改用 `MEDIUMTEXT`。
2. 所有外键约束目前使用级联检查，MVP 阶段不启用 `ON DELETE CASCADE`，避免误删。
3. 时间字段统一使用数据库服务器时区（UTC 或本地时区需在 `docker-compose.yml` 中统一）。
4. MVP 阶段不需要分区表或分库分表。
