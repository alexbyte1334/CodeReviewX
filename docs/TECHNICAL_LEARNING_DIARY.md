# CodeReviewX 技术学习日记（压缩版）

> 项目：CodeReviewX —— 面向 Pull Request 的证据约束型 AI Code Review Agent
> 记录日期：2026-08-03

## 一、项目目标

我最初想做一个能够自动审查 GitHub Pull Request 的 AI 工具。真正实现后，我发现核心并不是“调用一次大模型”，而是建立一条完整、可靠的工程链路：

```text
PR 输入 → 代码上下文 → RAG 检索 → AI 评审 → 证据校验 → 评论预览 → 人工确认 → 发布
```

项目最终关注三个问题：

1. AI 发现的问题是否有真实代码证据？
2. 证据是否属于当前 PR 的正确 commit？
3. 系统是否可恢复、可审计，并且不会未经确认修改 GitHub？

## 二、技术架构

```text
React 18 + TypeScript + Vite
              ↓ HTTP / SSE
Spring Boot 3 + Java 17
       ├── GitHub REST API
       ├── PostgreSQL 16 + pgvector
       ├── Embedding API
       ├── Rerank API
       └── OpenAI-compatible model provider
```

各技术的职责：

| 技术 | 项目实现 |
| --- | --- |
| React / TypeScript | 创建任务、展示状态、查看 Issue 和 Evidence、确认发布 |
| Spring Boot | API、任务编排、异常处理、权限与发布控制 |
| PostgreSQL | 任务、事件、代码快照、索引、Issue 和预览持久化 |
| pgvector | 保存代码 Embedding 并进行向量相似度检索 |
| RAG | 为模型提供当前仓库的相关上下文 |
| SSE | 实时推送任务进度，并支持断线重放 |
| Model provider | 结构化计划、执行和证据门禁 |
| Flyway | 管理 H2 和 PostgreSQL 数据库迁移 |
| Docker Compose | 统一启动数据库、后端和前端 |

## 三、完整评审流程

1. 前端提交仓库地址、PR 编号和 `Idempotency-Key`。
2. 后端创建 Review Task，返回 `202 Accepted`，避免长时间阻塞 HTTP 请求。
3. 读取 GitHub PR 元数据、head SHA、文件列表和 patch。
4. 在 head SHA 上建立完整仓库索引，同时保留 changed files 作为优先上下文。
5. 查询当前 commit 的 RAG snapshot。
6. 进行向量检索和 PostgreSQL 全文检索，并用 RRF 融合排名。
7. 使用 Rerank API 重新排序，组装有大小限制的 Evidence Bundle。
8. 执行静态检查，再运行模型的结构化评审阶段和证据门禁。
9. 校验 finding 的路径、行号、commit 和 evidence labels。
10. 生成评论预览，由用户选择并确认后才发布到 GitHub。

## 四、RAG 的具体实现

### 4.1 索引

仓库文件经过代码分块后，每个 chunk 保存：

- 文件路径、语言和符号名称；
- 起止行号和代码内容；
- 内容 hash；
- Embedding 向量；
- 所属 repository、commit snapshot 和 index version。

索引任务使用 job 状态、租约和 heartbeat，支持失败重试和服务重启恢复。内容 hash 没有变化的 chunk 可以复用 Embedding，避免每次都重新处理完整仓库。

### 4.2 混合检索

向量检索适合语义相似内容，全文检索适合函数名、变量名和错误关键词。两条路线同时执行：

```text
查询 → Embedding → Vector Search
查询 → PostgreSQL FTS → Lexical Search
                         ↓
                    RRF 融合排名
                         ↓
                      Rerank
```

查询严格匹配：

```text
repository_id + commit_sha + embedding_model + dimensions + index_version
```

这样可以避免旧 commit 的代码污染当前评审结果。

### 4.3 上下文预算

`RagContextAssembler` 对模型上下文设置硬限制：

- 最多 30 个 rerank candidates；
- 最多 12 个最终 evidence chunks；
- 每个文件最多 3 个 chunk；
- 上下文最多约 36000 个字符；
- 相邻重复代码去重；
- 优先保留当前 changed file 的准确代码。

Rerank 服务失败时可以使用融合排名，但系统必须记录降级状态。我的体会是：RAG 不是“给模型更多代码”，而是给模型有限、相关、可追溯的上下文。

### 4.4 证据门禁

每条证据生成稳定的 `C1`、`C2` 等标签。模型 finding 必须引用这些标签，后端再检查：

1. label 是否存在且没有重复；
2. evidence 路径是否与 finding 路径一致；
3. finding 行号是否在 evidence 范围内；
4. 行号是否属于本次 diff 的新增侧；
5. commit 是否与当前评审一致。

任何一项失败，finding 都不能生成可发布评论。模型负责提出候选结论，程序负责决定结论是否满足发布条件。

## 五、结构化模型评审

### 计划阶段

根据 PR、diff 和 Evidence Bundle 生成评审计划，例如检查空指针、权限、配置泄漏、异常处理和测试影响。

### 执行阶段

按照计划输出结构化 finding，包含严重级别、标题、文件、行号、描述、修复建议和 evidence chunk IDs。

### 证据门禁阶段

复核候选结果，检查是否越界、是否有证据、是否违反评审规则。通过后才转换成内部 Issue。

模型响应不会直接信任。后端会校验 JSON 结构、字段完整性、证据引用和错误响应。

## 六、前端和后端如何配合

前端主要展示：

- 任务状态和事件时间线；
- 风险摘要和 Issue 列表；
- 每个 Issue 的代码证据；
- 评论预览和发布状态；
- RAG、GitHub、模型和数据库的 readiness 状态。

后端通过 `GET /api/reviews/{uuid}/events` 提供可重放 SSE。事件写入数据库，并使用递增事件 ID；浏览器断线后带上 `Last-Event-ID`，后端可以从断点继续发送。

前端勾选评论只是用户意图，后端发布前仍会重新校验任务归属、目标仓库、输入快照、证据门禁和授权状态。

## 七、遇到的问题和解决方式

| 问题 | 解决方式 | 学到的原则 |
| --- | --- | --- |
| 固定 Story Mode 看起来像真实 Agent | 改为绑定真实任务、事件、Issue 和 Evidence | UI 演示不能冒充运行时能力 |
| 只分析 diff，缺少上下游代码 | 在 PR head SHA 建立完整仓库索引 | Code Review 需要仓库级上下文 |
| 旧 commit 污染当前结果 | snapshot 强制匹配 commit、模型、维度和版本 | AI 数据必须有版本边界 |
| Rerank 返回格式异常 | 校验数量、ID、重复项和覆盖范围 | 外部响应必须在边界层验证 |
| 模型编造路径或行号 | 使用 evidence label、路径、行号和 diff 联合校验 | 生成式输出必须经过确定性门禁 |
| 重试造成重复任务或评论 | 使用 `Idempotency-Key` 和稳定发布 marker | 写操作必须支持幂等 |
| SSE 断线丢失进度 | 持久化事件并支持 `Last-Event-ID` | 实时系统也需要可靠存储 |
| 部分模型的原生 tool-call 不完全兼容 | 改用结构化 JSON 协议并增加 contract test | 不要未经验证依赖协议假设 |
| 真实模型评测受额度条件限制 | 用 Fake Provider 验证流程和安全边界，保留 live gate | Fake 测试不能冒充真实模型验收 |
| Railway smoke 缺少部署条件 | 记录 CLI、登录、项目和 secrets 阻塞项 | 外部验收结果必须如实报告 |

## 八、安全设计

- API key 和 GitHub token 只放在环境变量或本地忽略文件中；
- 公共 API 不返回 token、Authorization header、原始 prompt、原始模型输出和完整 raw diff；
- GitHub 使用最小权限；
- 评论必须经过选择、确认、服务端授权和证据复核；
- RAG 证据不可用时，模型评论不能直接发布；
- 使用 secret scan、dependency scan、static scan 和 Semgrep 做静态检查。

我最大的安全认识是：AI 发现错误只是一个候选结果，未经验证的自动发布本身也可能制造事故。

## 九、测试和验证

验证分为四类：

1. **后端测试**：任务、GitHub 输入、JSON 解析、证据校验、RAG、发布和失败状态。
2. **前端测试**：Vitest、TypeScript typecheck、Vite production build。
3. **RAG 质量测试**：Recall@10、MRR@10、nDCG@10、跨 commit 污染、forbidden hit、证据准确率和上下文预算。
4. **交付检查**：Docker Compose、PostgreSQL/pgvector、secret scan、dependency scan、static scan 和 `git diff --check`。

项目记录的验证结果包括：后端 395 个测试通过，前端 85/85 个 Vitest 测试通过；Java 生产 RAG 测试 Recall@10 为 1.000、MRR@10 为 0.833、nDCG@10 为 0.871，forbidden hit、跨 commit 污染和上下文预算违规均为 0。

这些结果只代表当前代码和确定性测试 fixture 的边界，不能直接等同于真实模型质量或生产部署已经完全验收。

## 十、关于动态 Agent 的反思

当前 Java 系统虽然包含结构化计划、执行和证据门禁阶段，但本质仍是确定性的多阶段工作流，不应该直接宣传成成熟动态 Agent。

更稳妥的路线是：

1. 先完成可信的 Live Demo；
2. 再建立隔离 Agent Spike，只开放搜索、读取文件和结束工具；
3. 使用同一批案例，对 Java Pipeline 和动态 Agent 各运行三次；
4. 比较 precision、recall、证据正确率、非法动作、p95 延迟和 token 成本；
5. 只有通过门禁，才考虑产品化。

这让我明白，学习新框架不是目的。能否在真实产品中证明价值、控制风险和承担运维成本，才是技术选型的依据。

## 十一、项目总结

完成 CodeReviewX 后，我掌握的不只是某个模型 API，而是一套 AI 工程方法：

- 用 commit snapshot 控制数据版本；
- 用混合检索、RRF、rerank 和上下文预算提高 RAG 质量；
- 用 evidence validator 限制模型幻觉；
- 用持久化事件、租约、重试和幂等保证任务可靠性；
- 用人工确认和服务端复核控制发布风险；
- 区分 Fake Provider、离线评测、真实模型评测和生产 smoke；
- 如实区分“已完成”“部分完成”和“待外部条件验证”。

这个项目让我从“调用 AI 做 Demo”，走到了“构建一个有证据、有边界、可恢复、可审计的 AI 应用系统”。
