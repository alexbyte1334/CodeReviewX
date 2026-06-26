# Round 15 Start: Stage 2 Architecture Planning

## 1. Round Metadata

```text
Project: CodeReviewX
Round: Round 15
Theme: Stage 2 Architecture Planning — GitHub PR Ingestion and Agent v2 Design
Task Type: Architecture/design before implementation
Required Previous Stage:
  Stage 1.5 closed
```

## 2. Stage 2 Purpose

Stage 2 upgrades CodeReviewX from:

```text
Manual Diff-Grounded AI Code Review Agent MVP
```

to:

```text
Real Engineering Agent
```

Stage 2 must be designed before implementation.

## 3. Stage 2 Design Topics

Round 15 should produce architecture documents for:

```text
GitHub PR ingestion
GitHub token/security model
PR metadata loader
PR diff loader
Repository context loader
Project review policy
Tool/function abstraction
Provider trace and tool trace
Review run persistence
Comment preview before write-back
Stage 2 database changes
Frontend trace/result presentation
```

## 4. Stage 2 Sequencing Principles

```text
先 ingestion，再 RAG
先工具抽象，再 MCP
先 project rules，再 memory
先 comment preview，再真实 write-back
先 trace，再自动化
先单用户本地，再生产多租户
```

## 5. Non-Implementation Rule

Round 15 is planning only.

Do not implement:

```text
GitHub API client
database migrations
RAG
MCP
Memory
PR comment write-back
auth/team model
```

Implementation starts in later rounds only after the Stage 2 design is approved.

