# CodeReviewX PRD v1.0

> 项目定位：面向 GitHub Pull Request 的智能代码审查与修复建议 Agent
> 当前阶段：MVP 需求定义阶段
> 文件用途：Agent 间流通、仓库归档、后续开发任务输入
> 流通格式：Markdown，UTF-8 编码
> Agent 间流通请使用本文件路径：`docs/PRD.md`

---

## 1. 文档信息

| 项目 | 内容 |
|---|---|
| 项目名称 | CodeReviewX |
| 项目定位 | 面向 GitHub Pull Request 的智能代码审查与修复建议 Agent |
| 当前版本 | PRD v1.0 |
| 阶段目标 | 6 周内完成可运行、可展示、可写入简历的 MVP |
| 项目负责人 | Alex |
| 架构负责人 | ChatGPT 作为项目总架构师，负责需求边界、架构规则与交接标准；Qoder 审查与测试；Codex 运行与维护；Cursor 模块补全 |
| 当前状态 | 工程骨架初始化完成 |
| Agent 流通格式 | Markdown |

---

## 2. 项目背景

CodeReviewX 的目标是实现一个聚焦 GitHub PR 场景的智能代码审查系统。用户输入 GitHub 仓库地址和 PR 编号后，系统自动获取 PR diff，结合静态分析工具和 LLM 生成结构化 Review 报告，帮助开发者发现潜在 Bug、安全风险、性能问题和测试缺失问题，并给出修复建议。

---

## 3. 核心工作流

```text
用户输入 repoUrl + prNumber
        ↓
backend-java 创建 ReviewTask
        ↓
backend-java 调用 ai-service
        ↓
ai-service 拉取 GitHub PR diff
        ↓
ai-service 解析 PR 文件变更
        ↓
ai-service 调用 Semgrep 进行静态分析
        ↓
ai-service 调用 mock LLM 或真实 LLM 生成 Review 结果
        ↓
backend-java 保存 Review 结果
        ↓
用户在 frontend 查看报告
```

---

## 4. MVP 功能范围

| Feature | 说明 |
|---|---|
| F1 | ReviewTask 创建 |
| F2 | ReviewTask 查询 |
| F3 | GitHub PR diff 拉取 |
| F4 | PR 文件变更保存 |
| F5 | ai-service mock review result |
| F6 | Semgrep 静态分析集成 |
| F7 | LLM 结构化 Review JSON |
| F8 | 前端 Review 报告展示 |
| F9 | Docker Compose 本地启动 |
| F10 | GitHub Actions 基础 CI |
| F11 | 文档与演示材料 |

---

## 5. 目标用户

### MVP 阶段目标用户

1. **项目展示者**：计算机专业学生，用于作品集、简历、面试和技术讲解。
2. **开发者用户**：输入 GitHub 仓库地址和 PR 编号，查看系统生成的代码审查报告。

### MVP 阶段非目标用户

以下用户群体不在 MVP 范围内：

- 企业管理员
- 多团队组织
- 需要复杂权限管理的企业用户
- 需要自动修复合并能力的高级用户

---

## 6. MVP 问题陈述

当前开发者在代码评审阶段面临以下问题：

1. 手动 Code Review 耗时，且容易遗漏潜在 Bug 和安全风险；
2. 缺乏系统化的静态分析工具与 LLM 结合的自动化审查流程；
3. PR diff 信息量大，人工逐行分析效率低下。

CodeReviewX MVP 针对以下核心问题：**如何在用户提交 GitHub PR 编号后，自动生成结构化的代码审查报告，并清晰呈现风险等级和具体问题列表。**

---

## 7. Review 问题类别

ai-service 返回的 ReviewIssue 必须使用以下枚举类别：

| 类别 | 说明 |
|---|---|
| `BUG` | 潜在逻辑错误或运行时异常 |
| `SECURITY` | 安全风险，如注入、密钥泄露、未授权访问 |
| `PERFORMANCE` | 性能问题，如 N+1 查询、不必要的循环、内存泄露风险 |
| `TEST` | 测试缺失或测试覆盖不足 |
| `STYLE` | 代码风格或可读性问题 |

同时，每个 ReviewIssue 的来源 (`source`) 必须为以下之一：

| 来源 | 说明 |
|---|---|
| `LLM` | 由 LLM 分析生成 |
| `SEMGREP` | 由 Semgrep 静态分析生成 |

---

## 8. 数据实体概览

### ReviewTask

| 字段 | 类型 | 含义 |
|---|---|---|
| id | BIGINT | 任务 ID |
| repo_url | VARCHAR(500) | GitHub 仓库地址 |
| pr_number | INT | PR 编号 |
| status | ENUM | PENDING / RUNNING / SUCCESS / FAILED |
| summary | TEXT | Review 总结 |
| risk_level | ENUM | LOW / MEDIUM / HIGH |
| error_message | TEXT | 失败原因 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### ReviewFileChange

| 字段 | 类型 | 含义 |
|---|---|---|
| id | BIGINT | 文件变更 ID |
| task_id | BIGINT | 关联任务 ID |
| file_path | VARCHAR(500) | 文件路径 |
| change_type | VARCHAR(20) | added / modified / deleted |
| additions | INT | 新增行数 |
| deletions | INT | 删除行数 |
| patch | TEXT | diff 片段 |
| created_at | DATETIME | 创建时间 |

### ReviewIssue

| 字段 | 类型 | 含义 |
|---|---|---|
| id | BIGINT | issue ID |
| task_id | BIGINT | 关联任务 ID |
| file_path | VARCHAR(500) | 文件路径 |
| line_number | INT | 行号 |
| type | ENUM | BUG / SECURITY / PERFORMANCE / TEST / STYLE |
| severity | ENUM | LOW / MEDIUM / HIGH |
| title | VARCHAR(255) | 问题标题 |
| description | TEXT | 问题描述 |
| suggestion | TEXT | 修复建议 |
| source | ENUM | LLM / SEMGREP |
| created_at | DATETIME | 创建时间 |

---

## 9. 任务状态流转

```text
PENDING -> RUNNING -> SUCCESS
PENDING -> RUNNING -> FAILED
```

---

## 10. 第一阶段明确不做

- 登录注册 / 多租户 / 用户权限
- Kubernetes / 分布式调度
- 自动创建修复 PR / 自动 commit
- 消息队列 / Redis / 向量数据库
- 复杂规则配置 / 多模型路由
- 计费系统 / 团队协作功能

---

## 11. MVP 成功标准

MVP 完成时，必须满足以下全部标准：

1. 用户可以通过前端或 REST API 创建 Review 任务；
2. 系统可以处理至少一个公开 GitHub PR；
3. 系统可以展示结构化 Review 报告；
4. Review 报告包含 `summary`、`riskLevel` 和 `issues`；
5. `issues` 至少包含来自 mock LLM 或真实 LLM 的一条问题；
6. Semgrep 至少可以完成一次分析并返回结果；
7. `backend-java`、`ai-service`、`frontend` 可以通过 Docker Compose 本地启动；
8. GitHub Actions CI 可以执行基础验证；
9. `README.md` 和核心文档完整；
10. 项目可以支撑简历描述和面试讲解。

---

## 12. 变更管理

需求变更必须经过以下流程：

```text
提出变更 -> ChatGPT 架构师评估 -> 更新 PRD -> 再进入编码
```

未经 PRD 更新的需求，不允许进入编码阶段。
