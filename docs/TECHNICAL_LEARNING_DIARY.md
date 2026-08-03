# CodeReviewX 技术学习日记

> 记录人：项目开发者
> 项目：CodeReviewX —— 面向 Pull Request 的证据约束型 AI Code Review Agent
> 记录日期：2026-08-03

## 一、我为什么做这个项目

我最初想做的是一个“能够自动审查 GitHub Pull Request 的 AI 工具”。真正开始实现后，我发现这不是简单地调用一次大模型接口，而是一条完整的工程链路：输入 PR、获取准确代码、理解仓库上下文、产生问题、证明问题确实存在、生成评论，最后还要让人确认后才能发布。

因此，CodeReviewX 的核心目标逐渐从“模型能不能找到问题”变成了三个问题：

1. 模型说的问题是否有真实代码证据？
2. 证据是否属于本次 PR 的正确提交版本？
3. 自动化是否可恢复、可审计，并且不会未经确认就修改 GitHub？

这个转变是我在项目中最重要的一次学习：AI 应用的难点不只是模型能力，更是边界、数据一致性、失败处理和可验证性。

## 二、项目整体技术栈

项目目前的运行结构是：

```text
React 18 + TypeScript + Vite
              ↓ HTTP / replayable SSE
Spring Boot 3 + Java 17
       ├── GitHub REST API
       ├── PostgreSQL 16 + pgvector
       ├── Embedding API
       ├── Rerank API
       └── OpenAI-compatible model provider
```

主要技术及其职责如下：

| 技术 | 在项目中的作用 | 我的学习重点 |
| --- | --- | --- |
| React / TypeScript | 创建评审任务、展示过程、查看证据、确认评论 | 状态管理、异步请求、错误与降级 UI |
| Spring Boot | 提供 API、编排评审流程、管理后台任务 | 分层设计、配置、异常、事务 |
| Java 17 / Maven | 实现稳定的后端业务和测试体系 | 面向对象、泛型、Stream、测试驱动 |
| PostgreSQL | 持久化任务、事件、代码快照和评审结果 | SQL、事务、索引、版本隔离 |
| pgvector | 保存代码向量并进行相似度检索 | Embedding、向量距离、维度一致性 |
| RAG | 为模型提供仓库级上下文 | 混合检索、RRF、重排、上下文预算 |
| GitHub REST API | 读取 PR 元数据、补丁和仓库文件 | 外部 API、令牌安全、输入限制 |
| SSE | 向前端实时推送评审状态 | 事件 ID、断线重放、Last-Event-ID |
| Model provider | 生成计划、执行代码审查、进行证据门禁 | 结构化 JSON、模型输出校验 |
| Flyway | 管理数据库 schema 迁移 | 可重复部署、版本演进 |
| Docker Compose | 本地启动 PostgreSQL、后端和前端 | 环境一致性和服务健康检查 |

## 三、我学到的真实业务流程

CodeReviewX 不是同步接口收到请求后立即返回结果，而是创建一个可恢复的 Review Task。流程大致如下：

```text
创建 Review Task
  ↓
记录 QUEUED 事件并返回 202 Accepted
  ↓
读取 GitHub PR 元数据和 PR head SHA
  ↓
读取 PR 文件补丁，并保存经过限制的输入快照
  ↓
确认该 commit 的完整仓库 RAG 索引
  ↓
构造检索问题，进行向量 + 全文混合检索
  ↓
重排并组装有限长度的 Evidence Bundle
  ↓
运行静态检查
  ↓
Structured model review → Evidence Gate
  ↓
校验证据、生成结构化 Issue
  ↓
生成本地评论预览
  ↓
用户选择评论并明确确认
  ↓
带幂等标记地发布到 GitHub
```

这里我学到一个很实用的后端设计：耗时任务应该返回 `202 Accepted`，而不是让 HTTP 请求一直等待。任务状态和事件写入数据库后，前端通过 `GET /api/reviews/{uuid}/events` 订阅 SSE。事件使用递增 ID，客户端断线重连时带上 `Last-Event-ID`，后端就能从上次的位置继续发送，而不是让用户重新开始评审。

## 四、GitHub PR 输入是如何实现的

### 4.1 为什么不能只接收 diff

只分析 PR diff 容易漏掉被修改代码调用的公共方法、配置项、类型定义和测试代码。因此项目在 PR head SHA 上建立完整仓库快照，同时保留变更文件作为优先上下文。

输入过程分成几步：

1. 读取仓库地址和 PR 编号。
2. 请求 GitHub PR 元数据，得到 base SHA、head SHA、作者和文件列表。
3. 请求 PR 文件 patch。
4. 以 head SHA 为固定版本，读取仓库文件并建立索引。
5. 将变更路径、行号和补丁保存为受限快照。

为了避免大 PR、恶意内容或异常仓库拖垮服务，代码中设置了边界：默认最多读取 50 个 changed files，总 diff 约 512000 bytes，单文件 patch 约 20000 bytes，上下文文件最多 8 个。这个设计让我认识到，外部输入必须先限制大小、过滤控制字符，再进入后续流程。

### 4.2 提交版本隔离

RAG 查询不是只按 repository ID 查数据，而是同时使用：

```text
repository_id
commit_sha
embedding_model
embedding_dimensions
index_version
```

只有状态为 `READY` 且所有条件都匹配的 snapshot 才允许检索。这样可以避免把旧提交中的代码当成当前 PR 的证据，也避免 Embedding 模型或向量维度变化后继续使用旧索引。

这是项目中非常关键的“数据正确性”设计。AI 输出再好，如果引用了错误 commit 的代码，结论仍然是不可信的。

## 五、RAG 的详细实现过程

### 5.1 索引阶段：把代码变成可检索数据

仓库文件会先经过代码分块器处理。每个 chunk 保存文件路径、语言、符号名称、起止行、内容、内容 hash 和 embedding。数据库中还保存 repository、commit snapshot、index job 等信息。

索引任务采用可恢复设计：

- job 有 `QUEUED`、运行中、成功和失败等状态；
- worker 使用租约和 heartbeat，避免多个 worker 重复执行；
- 内容 hash 相同的 chunk 可以复用已有 embedding；
- 新 commit 只重新计算变化的文件；
- 失败后保留错误信息，方便重试和排查。

性能测试中，1000 个文件、10000 个 chunk 的快照在 20 个文件变化时只重新 embedding 20 个 chunk，复用了 9980 个 chunk。这让我理解了增量索引的价值：不能每次 PR 都从零处理整个仓库。

### 5.2 向量检索和全文检索

单独使用向量检索会遇到关键词不准确的问题，单独使用全文检索又可能无法理解语义。因此项目采用两条检索路线：

1. **Vector route**：把查询转成 embedding，在 pgvector 中查相似代码。
2. **Lexical route**：使用 PostgreSQL 全文检索匹配函数名、变量名、错误关键词和文件内容。

两条路线返回候选结果后，用 Reciprocal Rank Fusion（RRF）融合排名。简单理解，某个 chunk 在两条路线中都排名靠前，它的综合可信度就更高。

查询还会对 changed paths 做限制和加权，优先保留本次 PR 修改的文件，但不会完全丢弃相关的上下游文件。

### 5.3 Rerank 和上下文预算

混合检索得到的候选不会全部发给模型。项目先限制最多 30 个 rerank candidates，再调用 Rerank API 重新排序。Rerank 返回结果必须满足：数量正确、候选 ID 合法、没有重复、每个候选都能映射回原始 chunk；否则整个结果视为不可用。

随后由 `RagContextAssembler` 组装 Evidence Bundle。当前设计包含几个硬约束：

- 最终最多 12 个 evidence chunks；
- 每个文件最多 3 个 chunk；
- 上下文最多约 36000 个字符；
- 相邻且高度重复的 chunk 会去重；
- 如果候选中存在准确的 changed-file chunk，会尽量保护它不被预算淘汰；
- Rerank 失败时可以使用融合排名，但会记录降级状态。

这里我学到“上下文工程”比“把更多代码塞给模型”更重要。上下文过长会增加成本，也会降低模型注意力；上下文不受控还可能让模型引用与本次修改无关的代码。

### 5.4 Evidence 标签和证据门禁

每条证据会生成类似 `C1`、`C2` 的稳定标签，并记录路径、行号、commit 和代码片段。模型生成 finding 时必须引用这些标签。

`ReviewEvidenceValidator` 会检查：

1. finding 是否提供了非空、无重复的 evidence labels；
2. label 是否存在于本次 Evidence Bundle；
3. evidence 路径是否与 finding 文件路径一致；
4. finding 行号是否落在 evidence 行号范围内；
5. 行号是否确实属于本次 diff 的新增侧。

只要任何一项失败，finding 就不会进入可发布评论。这是我对 AI 项目“确定性校验”的一次重要实践：模型负责提出候选结论，程序负责决定结论是否满足发布条件。

## 六、结构化模型评审流程

项目不是直接把一段 prompt 发给模型，而是拆成三个角色：

### 6.1 Planner

Planner 根据 PR、diff 和 Evidence Bundle 生成评审计划，例如检查空指针、权限、配置泄漏、异常处理和测试影响。计划使用结构化 JSON 表达，后端不会只依赖自然语言解析。

### 6.2 Executor

Executor 根据计划逐项检查代码，输出候选 findings。每个 finding 需要包含严重级别、标题、文件、行号、描述、修复建议和 evidence chunk IDs。

### 6.3 Gatekeeper

Evidence Gate 对计划和候选结果进行复核，检查是否越界、是否有证据、是否违反评审规则。通过后才转换为内部 issue。

在 Java 中，`ReviewPipelineService` 负责调用配置好的 `ReviewProvider` 并检查返回值是否为空或格式非法；模型 provider 负责 prompt 构造、HTTP 调用、JSON 解析和结果转换。这样做的好处是业务流程不绑定具体模型，未来可以替换 provider。

### 6.4 我遇到的模型接口问题

我曾经假设模型的原生多轮 tool-call 格式可以直接兼容，但实际遇到过 `400 Param Incorrect`。后来改为先使用结构化 JSON 的 Planner/Executor/Gatekeeper 协议，并对每次返回做 schema 和 evidence 校验。

这次问题让我明白：模型厂商宣称“兼容 OpenAI 格式”，不代表每个多轮工具调用细节都完全兼容。真正接入前必须做最小 API contract test，验证请求字段、响应字段、错误码和重试行为。

## 七、前端如何展示一个真实、可操作的评审过程

前端使用 React + TypeScript，主要页面状态包括：

- 创建 Review Task；
- 查看任务状态和事件时间线；
- 展示风险摘要和 issue 列表；
- 点击 issue 查看对应 Evidence；
- 查看 comment preview；
- 选择要发布的评论；
- 在后端再次验证后执行发布。

前端 API 层统一处理 JSON 响应和错误。`ReviewRunWorkspace` 不仅显示“模型说了什么”，还显示“为什么可信”：文件路径、起止行、相关代码片段、相关性分数和证据状态。

发布抽屉中明确要求用户选择评论并点击确认。前端的选择只是用户意图，后端仍然会重新检查 preview 所属任务、输入快照、证据门禁和目标仓库，避免只相信浏览器传来的 ID。

## 八、持久化、幂等和恢复设计

### 8.1 为什么需要幂等

网络重试、用户重复点击或浏览器刷新都可能重复创建任务。如果每次都创建新任务，系统就会产生重复执行。因此 `POST /api/reviews` 要求 `Idempotency-Key`，同一个 key 重复提交会返回同一个 Review UUID。

GitHub 评论发布也使用稳定 marker，避免同一条评论因重试被发布多次。

### 8.2 为什么事件必须持久化

如果事件只存在内存中，后端重启后前端将不知道任务进行到哪一步。项目把状态、事件、trace、provider trace、issue 和 preview 都写入数据库。任务 worker 重启后会恢复排队任务或识别 abandoned run，并将错误转成明确的失败状态。

这使系统从“一个能跑通的脚本”变成了“可以被观察、重试和审计的服务”。

## 九、安全边界和我遇到的安全问题

项目中最重要的安全原则是：模型可以提出建议，但不能直接获得任意写权限。

具体实现包括：

- API key、GitHub token 只从环境变量或本地忽略文件读取；
- 公开 API 不返回 token、Authorization header、原始 prompt、原始模型输出和完整 raw diff；
- GitHub 读取和发布权限分开考虑，并尽量使用最小权限；
- 发布必须经过 selected preview、confirmed=true 和服务端授权；
- 发布前重新检查目标仓库、PR、commit 和 evidence；
- RAG 不可用时，模型 finding 默认不能直接发布；
- 使用 secret scan、dependency scan、static scan 和 Semgrep 规则做静态检查。

我一开始更关注模型能不能发现问题，后来才意识到“错误发布一条评论”本身就是产品事故。因此人审和发布门禁不是附加功能，而是核心功能。

## 十、测试和验证方法

我把验证分成四类，而不是只依赖一次手工 Demo：

### 10.1 单元和集成测试

后端使用 Maven 测试，覆盖任务创建、GitHub 输入、JSON 解析、证据校验、RAG、发布和错误状态。PostgreSQL 集成测试使用真实 PostgreSQL 16 + pgvector，并执行 Flyway migrations。

前端使用 Vitest，同时执行 TypeScript typecheck 和 Vite production build。

### 10.2 RAG 质量测试

测试指标包括 Recall@10、MRR@10、nDCG@10、forbidden-hit rate、cross-commit contamination、evidence validation 和 grounded precision。

其中我认为最有价值的是 negative control：即使检索候选中出现相关旧版本内容，最终选择也不能把它当成本次提交的证据。测试还会通过 mutation self-test 主动破坏 chunk identity、文件路径或证据，确认门禁确实会失败，而不是无条件显示 PASS。

### 10.3 性能测试

性能测试关注两个方面：增量索引是否复用 unchanged chunks，以及检索、RRF、rerank 和 context assembly 的 p95 是否符合预算。性能报告区分了本地确定性 fixture 的耗时和真实外部模型网络耗时，避免把测试 fixture 的结果误称为线上模型延迟。

### 10.4 交付检查

最终还执行了：

```bash
cd backend-java && JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
cd frontend && npm run typecheck && npm run build && npm test -- --run
node scripts/run-evals.mjs
node scripts/static-scan.mjs
node scripts/secret-scan.mjs
node scripts/dependency-scan.mjs
node scripts/run-rag-evals.mjs --self-test
git diff --check
```

项目文档记录的本地交付结果包括后端 395 个测试通过、前端 85/85 个 Vitest 测试通过，Java 生产 RAG 质量测试 Recall@10 为 1.000、MRR@10 为 0.833、nDCG@10 为 0.871，且 forbidden hit、跨 commit 污染和上下文预算违规为 0。这些结果证明的是当前代码和确定性测试边界内的质量，不等于真实模型质量或生产部署已经被完全证明。

## 十一、我遇到的主要问题和解决方式

| 遇到的问题 | 原因 | 解决方式 | 学到的原则 |
| --- | --- | --- | --- |
| 前端 Story 看起来像真实 Agent，但实际上是固定数据 | 步骤、代码行、证据和动画时长写死 | 改为展示真实 Review Task、事件、issue 和 evidence 状态 | UI 演示不能冒充运行时能力 |
| 只使用 diff 导致上下文不足 | PR 中的调用方和定义可能在未修改文件 | 在 head SHA 建立完整仓库索引 | 代码审查需要仓库级上下文 |
| 旧 commit 内容可能污染结果 | 查询只按仓库而未严格绑定版本 | snapshot 查询强制匹配 commit SHA、模型、维度和 index version | AI 数据必须有版本边界 |
| Rerank 返回数量或 ID 不合法 | 外部模型服务响应不可靠 | 校验数量、ID、重复项和覆盖范围 | 外部响应必须在边界层验证 |
| 模型 finding 没有可信证据 | 模型可能编造路径、行号或引用 | evidence label + 路径 + 行号 + diff 新增行联合校验 | 生成式输出必须经过确定性门禁 |
| 重复请求创建多个任务 | 网络重试和用户重复点击 | Idempotency-Key | 所有可重试写操作都要考虑幂等 |
| SSE 断线后进度丢失 | 事件只在内存中 | 数据库持久化事件并支持 Last-Event-ID 重放 | 实时体验也需要可靠存储 |
| 原生 tool-call 接口兼容性不足 | 厂商兼容声明不覆盖全部细节 | 使用结构化 JSON 协议并做 contract test | 不要未经验证依赖模型协议假设 |
| 真实模型评测遇到外部额度限制 | 外部额度或账户条件不足 | 使用 Fake Provider 验证 plumbing 和安全边界，保留 live gate 未完成状态 | Fake 测试不能冒充真实模型验收 |
| Railway 生产 smoke 无法完成 | 缺少 CLI、登录、项目或 secrets 条件 | 记录明确阻塞项，不宣称生产部署通过 | 外部验收必须如实报告 |

## 十二、关于“动态 Agent”的反思

项目过程中我曾经想把 Java Pipeline 直接替换成 Python LangGraph 动态 Agent。后来审查现有实现后，我认识到当前 Java 系统虽然有 Planner、Executor 和 Gatekeeper，但它本质上仍然是确定性的多阶段工作流，并不等同于一个已经成熟的动态 Agent。

更稳妥的路线是：

1. 先把当前 Live Demo 做到真实、可信、可观察；
2. 再建立隔离的 Agent Spike，只开放有限工具，例如 `search_repository`、`get_file_context` 和 `finish`；
3. 使用同一批 12 个案例，对 Java Pipeline 和动态 Agent 各运行三次；
4. 同时比较证据正确率、finding precision、recall、非法动作、p95 延迟和 token 成本；
5. 只有动态 Agent 通过门禁，才考虑产品化。

这个判断改变了我的技术学习方式：学习新框架不是目的，能否在真实产品中证明价值、控制风险和承担运维成本才是目的。

## 十三、项目完成后的能力总结

完成 CodeReviewX 后，我真正掌握的不是某一个 API，而是一套 AI 工程方法：

- 能够把产品需求拆成前端、后端、数据库、外部 provider 和验收门禁；
- 能够用 commit snapshot、Evidence Bundle 和 validator 控制模型幻觉；
- 能够设计异步任务、事件重放、租约、重试和幂等；
- 能够把向量检索、全文检索、RRF、rerank 和上下文预算组合成可解释的 RAG 流程；
- 能够区分确定性 fixture、Fake Provider、真实模型评测和生产 smoke；
- 能够在功能、成本、安全和可运维性之间做取舍；
- 能够诚实地写出“哪些已经验证，哪些仍然被额度、凭据或部署条件阻塞”。

## 十四、下一步学习计划

接下来我计划继续补强以下内容：

1. 深入学习 PostgreSQL 查询计划、pgvector 索引和混合检索调优。
2. 学习 Java 并发、任务租约、事务边界和 Spring Boot 运维指标。
3. 为真实模型增加稳定的 API contract test、重试策略和成本统计。
4. 使用同一评测语料对确定性 Pipeline 和动态 Agent 做公平对比。
5. 继续完善仓库级安全规则，特别是依赖漏洞、权限边界和 prompt injection 防护。
6. 通过真实部署环境验证端到端延迟、日志、告警和恢复能力。

## 结语

CodeReviewX 让我从“调用 AI 接口做一个 Demo”的思路，走到了“构建一个有证据、有边界、可恢复、可审计的 AI 工程系统”。项目仍然有外部模型额度和生产环境验收方面的限制，但这些限制也让我学会了如何区分完成、部分完成和待验证，而不是为了让项目看起来完整而夸大结论。

这份日记对我来说，不只是项目总结，也是一次学习记录：我逐渐理解了，真正可靠的 AI 产品，核心不是让模型拥有无限权限，而是让模型在清晰的证据、工具和验证规则内工作。
