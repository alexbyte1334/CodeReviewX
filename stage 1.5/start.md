# CodeReviewX 项目阶段汇总与下一阶段优化路线

## 1. 文档目的

本文档用于总结 CodeReviewX 从项目启动到 Stage 1 MVP 完成的整体进展，并明确下一阶段的产品样式升级、真实 MiMo 接入、Git/GitHub 同步，以及 Stage 2 工程 Agent 能力建设方向。

当前项目阶段：

```text
Stage 1 MVP: 已完成
Final Verdict: QODER_ROUND_12_APPROVED_FINAL_MVP_DELIVERY
Current Product Name: Manual Diff-Grounded AI Code Review Agent MVP
```

下一步目标不是继续补 MVP，而是进入两个连续阶段：

```text
Stage 1.5:
  产品级 UI/UX 风格升级 + 可视化增强 + 真实 MiMo 可用性验证 + Git/GitHub 同步

Stage 2:
  从“可 demo 的 agent MVP”升级为“真正有用的工程 Agent”
```

---

## 2. 项目定位

CodeReviewX 当前定位为：

```text
一个本地可运行的 AI-assisted code review agent prototype。
```

它支持用户手动创建代码审查任务：

```text
输入:
  repoUrl + prNumber + optional diffText

输出:
  结构化代码审查结果，包括风险等级、问题摘要、issue cards、建议说明
```

当前 MVP 的正确名称：

```text
Manual Diff-Grounded AI Code Review Agent MVP
```

当前它不是生产级平台，也不是完整自动化 GitHub Agent。它是一个已完成的本地可运行 Agent MVP，具备继续产品化和工程化增强的基础。

---

## 3. 从开始到现在的项目建设过程

### 3.1 Stage 1 的核心目标

Stage 1 的目标不是一次性做出完整工程 Agent，而是先构建一个可信、可运行、可演示、可扩展的 MVP。

核心交付目标：

```text
1. 后端服务可运行
2. 前端界面可使用
3. 可以创建 review task
4. 可以接收 repoUrl、prNumber、可选 diffText
5. 可以通过 Mock Provider 生成稳定结构化审查结果
6. 可以配置 Xiaomi MiMo Provider
7. 可以将任务和 issues 持久化
8. 可以在前端展示 review summary 和 issue cards
9. 文档、demo script、final checklist 完整
10. 不过度声明尚未实现的 Stage 2 能力
```

### 3.2 当前已完成的系统能力

当前 CodeReviewX 已具备：

```text
Backend:
  Spring Boot backend-java
  ReviewTask API
  Health API
  Review pipeline orchestration
  Mock Provider
  Xiaomi MiMo Provider path
  Provider fallback handling
  H2 persistence
  ReviewTaskEntity + ReviewIssueEntity
  ReviewTaskResponse DTO

Frontend:
  React + Vite frontend
  Review task create form
  Optional diff input
  Diff size validation
  Whitespace-only diff handling
  Backend status indicator
  Review history
  Review task detail
  Review summary
  Issue cards
  Provider labels
  Basic responsive layout

AI Review Flow:
  Manual input
  ReviewContext
  ReviewPipelineService
  ConfigurableReviewProvider
  MockReviewProvider or XiaomiMiMoReviewProvider
  ReviewPromptBuilder
  XiaomiMiMoClient
  XiaomiMiMoFindingParser
  ReviewFinding[]
  Persistence
  Frontend presentation
```

### 3.3 当前 Agent 结构和运行模式

当前 CodeReviewX 的 Agent 运行结构是：

```text
Input:
  repoUrl + prNumber + optional diffText

Context:
  ReviewContext with optional diffText

Orchestrator:
  ReviewPipelineService

Provider Selection:
  ConfigurableReviewProvider

Provider:
  MockReviewProvider or XiaomiMiMoReviewProvider

Prompt:
  ReviewPromptBuilder includes diff when available

Model:
  Xiaomi MiMo API through XiaomiMiMoClient

Parser:
  XiaomiMiMoFindingParser

Finding:
  ReviewFinding[]

Persistence:
  ReviewTaskEntity + ReviewIssueEntity

Presentation:
  ReviewTaskResponse + polished frontend summary and issue cards
```

这说明当前已经不是单纯“把 GitHub 链接丢给 AI”，而是形成了初步工程化 Agent pipeline：

```text
输入契约 → 上下文构建 → Provider 选择 → Prompt 构造 → 模型调用 → 结果解析 → 数据持久化 → API 输出 → 前端展示
```

但它还不是完整工程 Agent，因为尚未具备：

```text
自动 GitHub PR ingestion
仓库 clone
项目级上下文理解
RAG
MCP
Function Calling
Memory
PR comment write-back
任务队列
生产权限系统
```

这些将进入 Stage 2。

---

## 4. Round 进展概览

### Round 01

项目初始化和基础方向建立。

完成内容：

```text
明确 CodeReviewX 是 AI code review agent 产品
确定先做本地 MVP
建立 Cursor → Codex → Qoder 的多 Agent 协作流程
```

### Round 02

后端 Java skeleton 建立。

完成内容：

```text
backend-java 基础结构
Spring Boot 项目骨架
基础 API 能力
初步测试验证
```

### Round 03

后端 ReviewTask API 建立。

完成内容：

```text
ReviewTask create/list/detail API
基础 DTO/Service/Controller
后端 API 验证
```

### Round 04

前端 ReviewTask mock UI 建立。

完成内容：

```text
frontend 基础页面
创建 review task 表单
任务列表和详情雏形
前后端对接方向明确
```

### Round 05

前端可视化呈现推进。

完成内容：

```text
增强前端可运行性
改善 review task 展示
推进 demo 可见性
```

### Round 06

Review result contract hardening。

完成内容：

```text
Review result response contract
riskLevel
issueSummary
ReviewFinding[]
前后端契约稳定化
```

### Round 07

Database persistence v1。

完成内容：

```text
ReviewTaskEntity
ReviewIssueEntity
H2 persistence
任务和 issue 持久化
```

### Round 08

Review pipeline orchestrator skeleton。

完成内容：

```text
ReviewPipelineService
ReviewContext
Provider abstraction
Mock provider pipeline
为真实 AI provider 接入做准备
```

### Round 09

Xiaomi MiMo AI Review Provider v1。

完成内容：

```text
XiaomiMiMoClient
XiaomiMiMoReviewProvider
MiMo properties
MiMo finding parser
Prompt builder
Provider fallback path
```

### Round 10

PR diff context v1。

完成内容：

```text
支持 optional diffText
ReviewContext 带 diff
PromptBuilder 可包含 diff
API/Frontend 接受手动 pasted diff
```

### Round 11

Frontend Agent result presentation v1。

完成内容：

```text
Review summary
Issue cards
Provider labels
Risk/severity presentation
UI polish
Browser smoke
```

### Round 12

Final hardening + demo readiness。

完成内容：

```text
后端测试通过
前端 typecheck/build/tests 通过
后端 runtime smoke 通过
前端 browser smoke 通过
Provider timeout/failure handling 加固
README/demo script/final checklist 补齐
Qoder 最终批准 Stage 1 MVP 关闭
```

---

## 5. 当前最终验收状态

Stage 1 MVP 最终验收状态：

```text
Final Verdict:
  QODER_ROUND_12_APPROVED_FINAL_MVP_DELIVERY
```

已通过：

```text
Backend tests:
  84 passed

Frontend:
  typecheck pass
  build pass
  38 tests passed

Runtime smoke:
  backend health pass
  create task without diff pass
  create task with diff pass
  list/detail pass
  frontend page load pass
  create flow pass
  oversized diff guard pass
  responsive smoke pass

Provider:
  Mock provider default works
  Xiaomi MiMo provider path exists
  MiMo config is environment-based
  Provider fallback behavior safe
  Timeout configured

Security:
  No raw diffText exposed in public API response
  No raw prompt exposed
  No raw model output exposed
  No API key exposed
  No committed secret identified

Documentation:
  Root README ready
  Backend README ready
  Frontend README ready
  Demo script ready
  Final MVP checklist ready
```

非阻塞遗留项：

```text
1. Live MiMo verification 未执行，因为本地环境没有 MIMO_API_KEY
2. H2 file database 存在单实例锁限制
3. Qoder 未重新执行 browser smoke，而是基于 Codex browser smoke 证据判断
```

这些不阻塞 Stage 1 MVP 关闭。

---

## 6. 当前产品边界

### 6.1 当前已经具备

```text
本地运行
手动创建 review task
repoUrl + prNumber 输入
optional pasted diffText 输入
Mock provider 稳定输出
MiMo provider 配置路径
Provider fallback
任务持久化
Issue 持久化
Review summary 展示
Issue cards 展示
基本响应式前端
Demo script
Final checklist
```

### 6.2 当前明确不具备

```text
自动 GitHub PR fetching
GitHub App integration
GitHub OAuth
私有仓库访问
仓库 clone
全仓分析
PR comment write-back
视觉化 diff viewer
代码 syntax highlighting
RAG
MCP
Function Calling
Memory
多 Agent planner
任务队列
streaming
trace UI
生产级 auth/team model
CI/CD pipeline
生产部署
```

---

## 7. 下一阶段总体策略

用户当前期望的推进顺序是：

```text
第一步:
  产品样式风格修改，使其看起来像可以上线交付的完整产品

第二步:
  输入 MiMo API，使其真正可用

第三步:
  上传 Git 和 GitHub，完成项目同步

第四步:
  进入 Stage 2，将其从“可 demo 的 agent MVP”升级为“真正有用的工程 Agent”
```

建议拆分为两个阶段：

```text
Stage 1.5:
  Productization & Live Provider Enablement

Stage 2:
  Real Engineering Agent Capability Buildout
```

---

## 8. Stage 1.5：产品化与真实可用性增强

### 8.1 Stage 1.5 目标

Stage 1.5 的目标是让 CodeReviewX 从“功能已通的 MVP”变成“外观看起来可交付、体验上更像完整产品、真实模型可用、代码仓库可同步”的产品化版本。

Stage 1.5 不应立即引入 RAG/MCP/Memory 等深层 Agent 能力。

核心目标：

```text
1. UI 风格升级为类 macOS 26 的现代桌面级产品风格
2. 移除 demo 感过强的提示文案和占位样式
3. 增加可视化表达和动态交互效果
4. 保持现有功能稳定
5. 配置真实 MIMO_API_KEY 并完成 live MiMo verification
6. 建立 Git 本地版本管理
7. 同步到 GitHub remote repository
8. 为 Stage 2 做干净的工程起点
```

---

## 9. Stage 1.5 UI/UX 风格升级方向

### 9.1 视觉风格定位

目标风格：

```text
类 macOS 26
现代
半透明
层次清晰
高级灰白玻璃质感
低噪音
强留白
轻动态
可上线产品感
```

关键词：

```text
Liquid glass
Translucent panels
Soft blur
Subtle gradients
Rounded windows
Floating sidebar
Desktop app feeling
Mac-like command center
Polished dashboard
Professional SaaS
```

### 9.2 页面结构建议

建议将当前页面升级为类似桌面应用结构：

```text
App Shell
  ├── Top Traffic Bar / Product Header
  ├── Left Sidebar
  │     ├── Product logo
  │     ├── Review Agent
  │     ├── History
  │     ├── Provider Status
  │     └── Settings placeholder
  ├── Main Workspace
  │     ├── Create Review Panel
  │     ├── Review Result Panel
  │     └── Visualization Panel
  └── Right Inspector / Detail Drawer
        ├── Summary
        ├── Issue detail
        └── Provider metadata
```

### 9.3 需要删去或弱化的 demo 感内容

应删除、折叠或转为 secondary copy：

```text
过多解释“这是 MVP”的提示
大段 limitations 直接占据主界面
明显 mock/demo 的视觉提示
过度教学式说明
不必要的开发者提示
重复强调本地 demo 的文案
```

但注意：

```text
不能在文档中删除真实限制
不能在产品中虚假声明已实现 GitHub fetching
不能隐藏 MiMo 当前配置状态
不能把 Mock Provider 伪装成真实模型
```

建议做法：

```text
主界面:
  强调产品体验和任务流

设置页 / About / Footer:
  放置限制说明和 provider 状态

README:
  继续保留完整限制和真实能力边界
```

### 9.4 动态效果方向

增加轻量但克制的动态效果：

```text
页面进入淡入
卡片 hover lift
review task 创建时 loading shimmer
issue cards stagger reveal
risk level pulse / glow
provider status live indicator
summary number count-up
panel transition
toast feedback
button press micro-interaction
```

技术建议：

```text
优先使用 CSS transition / CSS animation
如需更强交互，可引入 framer-motion
但不要引入复杂动画系统
不要牺牲性能和可维护性
```

### 9.5 可视化增强方向

当前 review summary 可以进一步产品化：

```text
Risk score visual
Severity distribution
Category distribution
Provider status card
Review timeline
Issue density panel
Confidence / source indicator
```

建议第一阶段只做轻量可视化：

```text
1. Severity distribution bars
2. Risk level visual badge
3. Review status timeline
4. Provider health/status chip
5. Summary metric cards
```

不建议立即引入复杂 chart library，除非当前组件无法支撑。

---

## 10. Stage 1.5 Live MiMo 可用性增强

### 10.1 目标

将当前“MiMo provider path 已接入”推进为“真实 MiMo 可用”。

需要完成：

```text
1. 本地配置 MIMO_API_KEY
2. provider=mimo 启动
3. 创建 metadata-only review
4. 创建 diff-grounded review
5. 验证 MiMo 成功返回结构化 findings
6. 验证 zero findings 可处理
7. 验证失败 fallback 到 mock
8. 验证 API/UI 不暴露 raw prompt/model output/key
9. 验证 logs 不打印 key
10. 将 live verification 结果写入 handoff/checklist
```

### 10.2 安全要求

严格遵守：

```text
MIMO_API_KEY 只能来自环境变量
不得写入源码
不得提交到 Git
不得写入 README 示例真实值
不得写入 handoff
不得输出到控制台
不得暴露到前端
不得持久化 raw prompt 或 raw model output
```

建议添加：

```text
.env.example
.gitignore includes .env
README uses placeholder only
backend startup docs explain env usage
```

### 10.3 建议补充能力

为真实可用性增加：

```text
Provider status endpoint
Current provider label in UI
MiMo mode warning if key absent
Fallback reason internal log
Sanitized user-facing provider failure message
```

注意这些仍属于 Stage 1.5 产品化，不等同 Stage 2 Agent 能力。

---

## 11. Stage 1.5 Git/GitHub 同步

### 11.1 目标

建立稳定版本管理和远程同步。

需要完成：

```text
1. 初始化 Git repository
2. 配置 .gitignore
3. 确保无密钥、无 node_modules、无 target、无 build artifact
4. 首次 clean commit
5. 创建 GitHub repository
6. 添加 remote origin
7. push main
8. 使用 tag 标记 Stage 1 MVP
9. 创建后续开发分支
```

### 11.2 建议 Git 分支策略

```text
main:
  始终保持可交付版本

stage-1-mvp:
  可选，用于保留 MVP 快照

stage-1-5-productization:
  产品样式和真实 MiMo 验证

stage-2-agent:
  真正工程 Agent 能力开发
```

### 11.3 建议 tag

```text
v0.1.0-stage1-mvp
v0.2.0-productized-mvp
v0.3.0-live-mimo
v1.0.0-engineering-agent-beta
```

### 11.4 上传前必须检查

```text
No MIMO_API_KEY
No .env
No node_modules
No backend target
No frontend dist unless explicitly desired
No local H2 database file if not intended
No IDE/cache files
No raw prompt/model output logs
No copied secret in handoff/docs
```

---

## 12. 推荐 Round 13：产品级 UI 风格升级

### 12.1 Round 13 主题

```text
Round 13: Product UI Restyle — macOS-like polished delivery interface
```

### 12.2 Round 13 目标

```text
将当前 MVP 前端从 demo interface 升级为类 macOS 26 风格的产品化界面。
```

### 12.3 Round 13 范围

允许：

```text
重构前端视觉布局
升级 App shell
增加 glass/card 风格
增加轻量动效
增加 summary 可视化
弱化 demo 提示
保留真实 limitations 入口
保持 API contract 不变
保持现有测试通过
补充必要 UI tests
```

禁止：

```text
改后端核心架构
改 API contract
引入 GitHub ingestion
引入 RAG/MCP/Memory
引入 Function Calling
引入 auth/team model
引入大型 UI 框架
引入复杂 chart/dashboard 系统
```

### 12.4 Round 13 交付标准

```text
前端 typecheck pass
前端 build pass
前端 tests pass
后端 tests 不受影响
页面看起来像完整产品而非 demo
create review flow 保持可用
review result 可读性提升
移动端不崩
没有 console errors
Mock/MiMo provider labels 真实准确
```

---

## 13. 推荐 Round 14：Live MiMo + GitHub Sync

### 13.1 Round 14 主题

```text
Round 14: Live MiMo Verification + Git/GitHub Repository Sync
```

### 13.2 Round 14 目标

```text
让 MiMo provider 在真实 API key 下完成可用性验证，并将项目同步到 GitHub。
```

### 13.3 Round 14 范围

允许：

```text
配置 .env.example
完善 .gitignore
完善 MiMo 启动文档
执行 live MiMo verification
修复 provider 真实调用中的小问题
初始化 Git
创建 GitHub remote
push clean repository
打 v0.1.0 或 v0.2.0 tag
```

禁止：

```text
提交真实 API key
提交本地 .env
提交数据库文件
提交 node_modules/target/dist
在 UI/API 暴露 raw prompt/model output
开始 RAG/MCP/Memory
开始 GitHub PR ingestion 大功能
```

---

## 14. Stage 2：真正工程 Agent 能力升级方向

Stage 2 的目标是从“手动 diff-grounded MVP”升级为“真正有用的工程 Agent”。

### 14.1 Stage 2 核心目标

```text
1. 自动获取 PR diff
2. 理解项目结构和代码上下文
3. 根据项目规则和 review policy 审查代码
4. 调用工具进行辅助分析
5. 形成稳定、可追踪、可解释的 review pipeline
6. 支持将结果回写到 PR
7. 支持持续优化和项目记忆
```

### 14.2 Stage 2 能力路线

建议按以下顺序推进：

```text
Stage 2.1:
  GitHub PR ingestion

Stage 2.2:
  Repository context loader

Stage 2.3:
  Review policy / project rules

Stage 2.4:
  Real tool-use / Function Calling

Stage 2.5:
  RAG / knowledge context

Stage 2.6:
  MCP integration

Stage 2.7:
  Memory system

Stage 2.8:
  PR comment workflow

Stage 2.9:
  Agent trace / observability

Stage 2.10:
  Production auth/team/project model
```

### 14.3 Stage 2 Agent 架构目标

目标架构应演进为：

```text
Input:
  GitHub repo + PR number / webhook event

Ingestion:
  GitHub API client
  PR metadata loader
  Diff loader
  Changed files loader

Context:
  ReviewContext
  RepoContext
  ProjectRules
  HistoricalFindings
  Optional RAG snippets

Planner:
  Review strategy planner

Tools:
  File fetch tool
  Diff parser tool
  Dependency inspection tool
  Test result inspection tool
  Static analysis tool
  Policy lookup tool

Model:
  MiMo or pluggable model provider

Parser:
  Structured finding parser
  Confidence/risk parser

Persistence:
  Review task
  Review run
  Review findings
  Provider trace
  Tool call trace
  Project memory

Presentation:
  Frontend dashboard
  PR-ready comment preview
  Review trace
  Actionable issue cards

Output:
  Human-readable review report
  Optional GitHub PR comments
```

### 14.4 Stage 2 技术原则

```text
先 ingestion，再 RAG
先工具抽象，再 MCP
先 project rules，再 memory
先 comment preview，再真实 write-back
先 trace，再自动化
先单用户本地，再生产多租户
```

---

## 15. 产品形态最终目标

CodeReviewX 的目标形态可以定义为：

```text
面向开发者和小型工程团队的 AI Code Review Agent。
```

目标用户：

```text
独立开发者
小型创业团队
代码审查负责人
工程管理者
AI-assisted development workflow 用户
```

核心价值：

```text
1. 快速发现 PR 风险
2. 用项目规则约束 AI review
3. 降低人工 review 成本
4. 提高 review 一致性
5. 将 AI review 从“聊天式问答”变成“工程化流程”
```

最终产品体验：

```text
连接 GitHub
选择项目
配置 review policy
自动分析 PR
展示结构化风险
允许人工确认
一键生成 PR comments
持续积累项目上下文和偏好
```

---

## 16. 下一步建议执行顺序

建议接下来按以下顺序推进：

```text
Round 13:
  Product UI Restyle
  类 macOS 26 产品风格
  可视化增强
  动态效果
  删除/弱化 demo 感提示
  保持 MVP 功能稳定

Round 14:
  Live MiMo Verification
  .env.example / .gitignore
  真实 API key 本地验证
  provider status polish
  Git 初始化
  GitHub remote 同步
  tag Stage 1/1.5 版本

Round 15:
  Stage 2 Planning
  GitHub PR ingestion 设计
  Agent architecture v2
  Tool/function boundary
  Data model extension
  Security model

Round 16:
  GitHub PR ingestion v1
  自动拉取 PR metadata 和 diff
  替代手动 pasted diff 的主流程

Round 17:
  Engineering Agent v1
  引入 project rules
  review policy
  tool abstraction
  traceable review run
```

---

## 17. 对当前决策的架构判断

优先做产品样式风格修改是合理的。

原因：

```text
1. Stage 1 功能链路已经稳定
2. 当前最大短板不是 API，而是产品完成度和真实可用感
3. UI 产品化能让后续 demo、融资、团队协作、外部反馈更有效
4. 在进入复杂 Stage 2 前，先固定一个高质量产品壳是正确节奏
5. Git/GitHub 同步应在 Stage 2 前完成，否则后续复杂开发难以管理
6. Live MiMo 验证应在 Stage 2 前完成，否则真实 provider 风险会后移
```

因此推荐路线：

```text
先产品化，再真实 provider，再版本管理，再 Stage 2 Agent 能力。
```

---

## 18. 当前项目状态一句话总结

```text
CodeReviewX 已完成 Stage 1 MVP，可以作为本地可运行的 Manual Diff-Grounded AI Code Review Agent MVP 交付；下一步应进入 Stage 1.5 产品化与真实可用性增强，然后再进入 Stage 2 构建真正工程 Agent。
```