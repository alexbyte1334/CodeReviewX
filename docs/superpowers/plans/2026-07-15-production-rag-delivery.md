# CodeReviewX Production RAG Delivery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将当前 changed-file context 拼接升级为可部署、可观测、可评测、可回滚的全仓库混合检索 RAG，并让每条 AI 审查结论携带可验证的代码证据。

**Architecture:** 保持 Spring Boot 单体交付，在 `backend-java` 内新增边界清晰的 `rag` 模块。生产路径使用 PostgreSQL 16 + pgvector 持久化仓库快照、代码块、向量、全文索引、索引作业和检索证据；索引在持久化作业中异步执行，审查请求使用 changed-file query expansion、向量召回、全文召回、RRF 融合、重排和预算裁剪生成 evidence bundle。现有 H2 模式继续作为不启用 RAG 的轻量开发模式，当前 GitHub Contents 上下文作为故障降级路径保留一个发布周期。

**Tech Stack:** Java 17, Spring Boot 3.2.5, Spring JDBC, Flyway, PostgreSQL 16, pgvector, JGit, OpenAI-compatible embedding/rerank HTTP APIs, React 18, TypeScript, Testcontainers, Vitest, GitHub Actions.

---

## 1. 交付边界与成功定义

### 1.1 当前基线

当前成功链路是：

```text
github.pr.metadata.load
  -> github.pr.diff.load
  -> repository.context.index
  -> static.analysis.findings
  -> mimo.ai1.plan
  -> mimo.ai2.execute
  -> mimo.ai1.gate
  -> issue.generate
  -> comment.preview.build
```

`RepositoryContextIndexService` 只读取最多 8 个 changed files，总上下文默认 48 KB，并由 `ReviewTaskService` 直接拼入 MiMo prompt。它没有仓库快照、持久化 chunk、embedding、向量库、混合召回、重排、证据引用、索引生命周期和 retrieval eval，因此不得在发布说明中称为完整 RAG。

### 1.2 本计划的交付口径

完成后，`GITHUB_PR` 审查链路为：

```text
github.pr.metadata.load
  -> github.pr.diff.load
  -> rag.index.ensure
  -> rag.query.build
  -> rag.retrieve.hybrid
  -> rag.rerank
  -> rag.context.assemble
  -> static.analysis.findings
  -> mimo.ai1.plan
  -> mimo.ai2.execute
  -> mimo.ai1.gate
  -> issue.generate
  -> evidence.validate
  -> comment.preview.build
```

交付必须同时满足：

- 仓库可按 commit SHA 克隆/更新、切块、嵌入并持久化。
- PR 审查只读取与 `headSha` 对应的索引快照，不混用其他分支版本。
- 召回包含向量和 PostgreSQL 全文两路，并使用 RRF 融合和独立重排。
- prompt 中每个 chunk 有稳定 `chunkId`、文件路径和行号；模型 finding 必须返回 `evidenceChunkIds`。
- 未通过证据校验的 finding 不进入自动评论预览。
- 索引、检索、重排、上下文预算和降级均有安全 trace，不记录 token、完整源码或原始 prompt。
- 有离线 retrieval benchmark、端到端 benchmark、延迟/成本阈值和 CI 门禁。
- Docker Compose 可启动 PostgreSQL/pgvector、后端和前端；外部 embedding/rerank 服务通过环境变量接入。

### 1.3 本轮非目标

- 多租户、计费、组织权限和 GitHub App 安装流程。
- 跨仓库知识图谱、自然语言问答产品、IDE 插件。
- 在请求线程执行全仓库克隆和全量 embedding。
- 自动发布未经人工确认的 GitHub 评论。
- 立即删除 `RepositoryContextIndexService`；先保留为受控 fallback，稳定一个版本后再移除。

## 2. 固化的设计决策

| 决策 | 选择 | 原因 |
|---|---|---|
| 部署形态 | Spring Boot 内模块化 RAG | 最小化新增运行单元，便于当前项目交付和演示 |
| 主数据库 | PostgreSQL 16 + pgvector | 同库提供事务、全文检索、向量检索和审计数据 |
| H2 | 仅 `rag.enabled=false` 的轻量模式 | H2 不承担 pgvector 行为模拟，避免假测试 |
| 仓库获取 | JGit shallow clone/fetch + detached checkout | 精确绑定 commit SHA，避免 shell 注入和工作区污染 |
| chunk | 语言无关行窗口 v1，保留 chunker SPI | 先覆盖 Java/TS/Python 等文本代码，后续可替换 AST chunker |
| embedding | OpenAI-compatible `/embeddings` | 供应商可替换，默认维度固定为 1024 |
| rerank | 可配置 `/rerank` cross-encoder | 与生成模型职责分离，输出稳定 relevance score |
| 融合 | Vector top 40 + FTS top 40 + RRF(k=60) | 可解释、实现简单、无需统一两类原始分数 |
| 最终上下文 | rerank top 12，最多 36,000 字符 | 给 diff、规划和模型输出保留 token 空间 |
| 索引一致性 | `(repository_id, commit_sha)` 不可变快照 | 审查证据可复现，杜绝分支漂移 |
| 降级策略 | RAG 不可用时回退 bounded context，并显式标记 | 保持审查可用，但不伪装为 RAG 成功 |

默认模型契约：

```yaml
embeddingModel: BAAI/bge-m3
embeddingDimensions: 1024
rerankerModel: BAAI/bge-reranker-v2-m3
```

变更 embedding 模型或维度必须创建新的 `index_version` 并重建索引，不允许把不同模型的向量写入同一索引版本。

## 3. 目标文件结构

```text
backend-java/src/main/java/com/codereviewx/backend/rag/
  config/       # RagProperties, 条件装配, async executor
  controller/   # repository index/status/rebuild API
  dto/          # API request/response
  embedding/    # embedding client 与 HTTP 实现
  indexing/     # checkout, file discovery, chunking, indexing jobs
  model/        # record/enums/value objects
  persistence/  # Spring JDBC repositories
  retrieval/    # query builder, hybrid search, RRF, rerank, budget assembly
  service/      # facade, lifecycle, orchestration

backend-java/src/main/resources/db/rag/postgresql/
  V4__rag_schema.sql

backend-java/src/test/java/com/codereviewx/backend/rag/
  ... unit tests
  PostgresRagIntegrationTest.java

frontend/src/components/
  RepositoryIndexStatus.tsx
  RetrievalEvidencePanel.tsx

evals/rag/
  corpus/       # 小型固定代码仓库语料
  cases/        # query、相关 chunk、预期 finding
  reports/      # 最新报告

scripts/
  run-rag-evals.mjs
  rag-smoke.sh
```

## 4. 数据模型和稳定契约

### 4.1 PostgreSQL 表

`V4__rag_schema.sql` 创建：

```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE rag_repository (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    owner_name VARCHAR(255) NOT NULL,
    repository_name VARCHAR(255) NOT NULL,
    clone_url VARCHAR(1000) NOT NULL,
    default_branch VARCHAR(255),
    active_commit_sha VARCHAR(64),
    index_status VARCHAR(32) NOT NULL,
    index_version INTEGER NOT NULL DEFAULT 1,
    embedding_model VARCHAR(255) NOT NULL,
    embedding_dimensions INTEGER NOT NULL,
    last_indexed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_rag_repository UNIQUE (provider, owner_name, repository_name),
    CONSTRAINT ck_rag_embedding_dimensions CHECK (embedding_dimensions = 1024)
);

CREATE TABLE rag_index_job (
    id BIGSERIAL PRIMARY KEY,
    repository_id BIGINT NOT NULL REFERENCES rag_repository(id),
    requested_ref VARCHAR(255) NOT NULL,
    resolved_commit_sha VARCHAR(64),
    trigger_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    discovered_file_count INTEGER NOT NULL DEFAULT 0,
    indexed_file_count INTEGER NOT NULL DEFAULT 0,
    indexed_chunk_count INTEGER NOT NULL DEFAULT 0,
    skipped_file_count INTEGER NOT NULL DEFAULT 0,
    error_code VARCHAR(64),
    error_message VARCHAR(1000),
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE rag_document (
    id BIGSERIAL PRIMARY KEY,
    repository_id BIGINT NOT NULL REFERENCES rag_repository(id),
    commit_sha VARCHAR(64) NOT NULL,
    path VARCHAR(1000) NOT NULL,
    language VARCHAR(64) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    byte_size BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_rag_document UNIQUE (repository_id, commit_sha, path)
);

CREATE TABLE rag_chunk (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES rag_document(id) ON DELETE CASCADE,
    repository_id BIGINT NOT NULL REFERENCES rag_repository(id),
    commit_sha VARCHAR(64) NOT NULL,
    chunk_key VARCHAR(96) NOT NULL,
    path VARCHAR(1000) NOT NULL,
    language VARCHAR(64) NOT NULL,
    symbol_name VARCHAR(500),
    start_line INTEGER NOT NULL,
    end_line INTEGER NOT NULL,
    content TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    token_count INTEGER NOT NULL,
    search_vector TSVECTOR GENERATED ALWAYS AS
      (to_tsvector('simple', coalesce(path, '') || ' ' || coalesce(symbol_name, '') || ' ' || content)) STORED,
    embedding VECTOR(1024) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_rag_chunk UNIQUE (repository_id, commit_sha, chunk_key)
);

CREATE INDEX idx_rag_chunk_vector
  ON rag_chunk USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_rag_chunk_search
  ON rag_chunk USING gin (search_vector);
CREATE INDEX idx_rag_chunk_snapshot
  ON rag_chunk(repository_id, commit_sha, path);

CREATE TABLE rag_retrieval_trace (
    id BIGSERIAL PRIMARY KEY,
    review_run_id BIGINT NOT NULL REFERENCES review_run(id),
    repository_id BIGINT NOT NULL REFERENCES rag_repository(id),
    commit_sha VARCHAR(64) NOT NULL,
    query_hash VARCHAR(64) NOT NULL,
    vector_candidate_count INTEGER NOT NULL,
    lexical_candidate_count INTEGER NOT NULL,
    reranked_count INTEGER NOT NULL,
    selected_count INTEGER NOT NULL,
    context_char_count INTEGER NOT NULL,
    degraded BOOLEAN NOT NULL,
    latency_ms BIGINT NOT NULL,
    result_summary_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE review_issue_evidence (
    id BIGSERIAL PRIMARY KEY,
    review_issue_id BIGINT NOT NULL REFERENCES review_issue(id) ON DELETE CASCADE,
    rag_chunk_id BIGINT REFERENCES rag_chunk(id) ON DELETE SET NULL,
    citation_label VARCHAR(32) NOT NULL,
    path VARCHAR(1000) NOT NULL,
    start_line INTEGER NOT NULL,
    end_line INTEGER NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    evidence_excerpt VARCHAR(2000) NOT NULL,
    retrieval_rank INTEGER NOT NULL,
    retrieval_score DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_review_issue_evidence UNIQUE (review_issue_id, citation_label)
);
```

### 4.2 Java 核心接口

后续任务必须保持以下边界，禁止把 HTTP、SQL、prompt 组装重新塞回 `ReviewTaskService`：

```java
public interface RepositoryCheckoutService {
    CheckedOutRepository checkout(GithubPrMetadata metadata);
}

public interface CodeChunker {
    List<CodeChunk> chunk(RepositoryFile file);
}

public interface EmbeddingClient {
    List<float[]> embed(List<String> inputs);
}

public interface RerankClient {
    List<RerankedChunk> rerank(String query, List<RetrievedChunk> candidates);
}

public interface RagIndexService {
    RagIndexResolution ensureIndexed(GithubPrMetadata metadata);
    RagIndexJob getJob(long jobId);
}

public interface RagRetrievalService {
    RagEvidenceBundle retrieve(RagRetrievalRequest request);
}
```

`RagEvidenceBundle` 必须只携带有界上下文：

```java
public record RagEvidenceBundle(
        String repositoryKey,
        String commitSha,
        String query,
        List<RagEvidence> evidence,
        int totalCharacters,
        boolean degraded,
        String degradationReason) {
}
```

### 4.3 API 契约

```text
POST /api/repositories/index
GET  /api/repositories/{owner}/{repo}/index-status?commitSha=...
POST /api/repositories/{owner}/{repo}/reindex
GET  /api/review-runs/{runId}/retrieval
GET  /api/review-tasks/{taskId}/issues/{issueKey}/evidence
```

`POST /api/repositories/index`：

```json
{
  "repoUrl": "https://github.com/example/repo",
  "ref": "main"
}
```

响应使用 `202 Accepted`：

```json
{
  "jobId": 42,
  "status": "QUEUED",
  "repository": "example/repo",
  "requestedRef": "main"
}
```

## 5. 分阶段执行计划

### Task 1: 建立 PostgreSQL/pgvector 运行基线

**Files:**
- Modify: `docker-compose.yml`
- Modify: `.env.example`
- Create: `backend-java/src/main/resources/application-postgres.yml`
- Create: `backend-java/src/main/resources/db/rag/postgresql/V4__rag_schema.sql`
- Modify: `backend-java/pom.xml`
- Create: `backend-java/src/test/java/com/codereviewx/backend/rag/PostgresRagMigrationTest.java`

- [ ] **Step 1: 写 PostgreSQL migration 失败测试**

使用 Testcontainers 启动 `pgvector/pgvector:pg16`，配置 Flyway locations 为 `db/migration` 和 `db/rag/postgresql`，断言 6 张 RAG 表、`vector` 扩展、HNSW 和 GIN 索引存在。

- [ ] **Step 2: 运行测试确认失败**

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn -Dtest=PostgresRagMigrationTest test
```

Expected: FAIL，原因是 Testcontainers 依赖、PostgreSQL profile 或 V4 migration 尚不存在。

- [ ] **Step 3: 增加数据库依赖和 profile**

`pom.xml` 增加 PostgreSQL runtime driver、`org.testcontainers:postgresql` 和 `org.testcontainers:junit-jupiter` test dependency。`application-postgres.yml` 设置 PostgreSQL datasource、关闭 H2 console、启用额外 Flyway location，并设置 `codereviewx.rag.enabled=true`。

- [ ] **Step 4: 实现 migration 和 Compose 服务**

`docker-compose.yml` 至少包含 `postgres` 健康检查、持久化 volume、`backend` 和 `frontend`；数据库镜像固定为 `pgvector/pgvector:pg16`。`.env.example` 改为 `POSTGRES_*`，移除误导性的 MySQL 主路径。

- [ ] **Step 5: 验证 migration 和兼容基线**

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn -Dtest=PostgresRagMigrationTest test
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
```

Expected: PostgreSQL migration PASS；现有 H2 测试继续 PASS，且 H2 不加载 PostgreSQL RAG migration。

- [ ] **Step 6: Commit**

```bash
git add docker-compose.yml .env.example backend-java/pom.xml backend-java/src/main/resources/application-postgres.yml backend-java/src/main/resources/db/rag/postgresql/V4__rag_schema.sql backend-java/src/test/java/com/codereviewx/backend/rag/PostgresRagMigrationTest.java
git commit -m "feat: add postgres pgvector rag foundation"
```

### Task 2: 建立 RAG 配置和外部模型客户端

**Files:**
- Create: `backend-java/src/main/java/com/codereviewx/backend/rag/config/RagProperties.java`
- Create: `backend-java/src/main/java/com/codereviewx/backend/rag/config/RagConfiguration.java`
- Create: `backend-java/src/main/java/com/codereviewx/backend/rag/embedding/EmbeddingClient.java`
- Create: `backend-java/src/main/java/com/codereviewx/backend/rag/embedding/OpenAiEmbeddingClient.java`
- Create: `backend-java/src/main/java/com/codereviewx/backend/rag/retrieval/RerankClient.java`
- Create: `backend-java/src/main/java/com/codereviewx/backend/rag/retrieval/HttpRerankClient.java`
- Test: `backend-java/src/test/java/com/codereviewx/backend/rag/embedding/OpenAiEmbeddingClientTest.java`
- Test: `backend-java/src/test/java/com/codereviewx/backend/rag/retrieval/HttpRerankClientTest.java`

- [ ] **Step 1: 写客户端契约测试**

用本地 mock HTTP server 断言：批量 embedding 请求、1024 维校验、429/5xx 指数退避、超时、响应数量不匹配、API key 脱敏；rerank 只接受候选 chunk id 和文本，并按服务返回 score 排序。

- [ ] **Step 2: 运行测试确认失败**

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn -Dtest=OpenAiEmbeddingClientTest,HttpRerankClientTest test
```

Expected: FAIL，相关接口和实现不存在。

- [ ] **Step 3: 实现配置属性**

配置项必须包含：

```yaml
codereviewx:
  rag:
    enabled: ${RAG_ENABLED:false}
    embedding-base-url: ${RAG_EMBEDDING_BASE_URL:}
    embedding-api-key: ${RAG_EMBEDDING_API_KEY:}
    embedding-model: ${RAG_EMBEDDING_MODEL:BAAI/bge-m3}
    embedding-dimensions: ${RAG_EMBEDDING_DIMENSIONS:1024}
    embedding-batch-size: ${RAG_EMBEDDING_BATCH_SIZE:32}
    rerank-base-url: ${RAG_RERANK_BASE_URL:}
    rerank-api-key: ${RAG_RERANK_API_KEY:}
    rerank-model: ${RAG_RERANK_MODEL:BAAI/bge-reranker-v2-m3}
    timeout-seconds: ${RAG_MODEL_TIMEOUT_SECONDS:30}
    max-retries: ${RAG_MODEL_MAX_RETRIES:2}
```

`toString()` 不得输出任何 key；启用 RAG 且 endpoint/key 缺失时启动失败，不允许运行到首个请求才发现。V1 schema 固定 1024 维，因此配置不是 1024 时也必须启动失败；模型维度升级走新 migration 和新 `index_version`。

- [ ] **Step 4: 实现 HTTP clients**

使用 JDK `HttpClient` 和 Jackson；每批 embedding 保持输入输出顺序；只重试 429、502、503、504；400/401/403 直接失败；日志只记录模型、批量大小、状态码、耗时和 request id。

- [ ] **Step 5: 验证测试**

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn -Dtest=OpenAiEmbeddingClientTest,HttpRerankClientTest test
```

Expected: PASS，测试输出和异常消息不包含测试 API key。

- [ ] **Step 6: Commit**

```bash
git add backend-java/src/main/java/com/codereviewx/backend/rag backend-java/src/test/java/com/codereviewx/backend/rag backend-java/src/main/resources/application-postgres.yml .env.example
git commit -m "feat: add configurable rag model clients"
```

### Task 3: 安全 checkout、文件发现与代码切块

**Files:**
- Create: `backend-java/src/main/java/com/codereviewx/backend/rag/indexing/JGitRepositoryCheckoutService.java`
- Create: `backend-java/src/main/java/com/codereviewx/backend/rag/indexing/RepositoryFileDiscovery.java`
- Create: `backend-java/src/main/java/com/codereviewx/backend/rag/indexing/CodeChunker.java`
- Create: `backend-java/src/main/java/com/codereviewx/backend/rag/indexing/LineWindowCodeChunker.java`
- Create: `backend-java/src/main/java/com/codereviewx/backend/rag/model/RepositoryFile.java`
- Create: `backend-java/src/main/java/com/codereviewx/backend/rag/model/CodeChunk.java`
- Test: `backend-java/src/test/java/com/codereviewx/backend/rag/indexing/JGitRepositoryCheckoutServiceTest.java`
- Test: `backend-java/src/test/java/com/codereviewx/backend/rag/indexing/RepositoryFileDiscoveryTest.java`
- Test: `backend-java/src/test/java/com/codereviewx/backend/rag/indexing/LineWindowCodeChunkerTest.java`

- [ ] **Step 1: 写安全边界测试**

覆盖 detached checkout 精确 SHA、私有 token 不进入 URL/日志、临时目录关闭后删除、symlink 跳出仓库被拒绝、binary/超大文件/生成目录跳过、`.gitignore` 生效、UTF-8 非法输入安全跳过。

- [ ] **Step 2: 写 chunk 行为测试**

固定算法：80 行窗口、20 行 overlap、单 chunk 最大 8,000 字符；`chunk_key=sha256(path:startLine:endLine:contentHash)`。断言行号、overlap、空文件、超长单行和 CRLF 归一化。

- [ ] **Step 3: 运行测试确认失败**

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn -Dtest=JGitRepositoryCheckoutServiceTest,RepositoryFileDiscoveryTest,LineWindowCodeChunkerTest test
```

Expected: FAIL，checkout/discovery/chunker 尚不存在。

- [ ] **Step 4: 实现 JGit checkout**

只允许 `https://github.com/{owner}/{repo}`；工作目录必须位于配置的 `RAG_WORK_ROOT`；fetch 深度默认 50，若目标 SHA 不在 shallow history 中再按 SHA 精确 fetch；每次 checkout 结束执行递归清理。

- [ ] **Step 5: 实现文件发现和切块**

默认跳过 `.git`, `node_modules`, `dist`, `build`, `target`, `vendor`, lockfiles、minified 文件和大于 1 MB 的文件；默认最多 5,000 文件、仓库文本总量 100 MB。语言由扩展名映射，未知文本标记为 `TEXT`。

- [ ] **Step 6: 验证测试**

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn -Dtest=JGitRepositoryCheckoutServiceTest,RepositoryFileDiscoveryTest,LineWindowCodeChunkerTest test
```

Expected: PASS，临时目录无残留，测试日志无 token。

- [ ] **Step 7: Commit**

```bash
git add backend-java/pom.xml backend-java/src/main/java/com/codereviewx/backend/rag backend-java/src/test/java/com/codereviewx/backend/rag
git commit -m "feat: add secure repository chunking pipeline"
```

### Task 4: 实现持久化索引作业和增量写入

**Files:**
- Create: `backend-java/src/main/java/com/codereviewx/backend/rag/persistence/RagRepositoryStore.java`
- Create: `backend-java/src/main/java/com/codereviewx/backend/rag/persistence/RagIndexJobStore.java`
- Create: `backend-java/src/main/java/com/codereviewx/backend/rag/persistence/RagDocumentStore.java`
- Create: `backend-java/src/main/java/com/codereviewx/backend/rag/persistence/RagChunkStore.java`
- Create: `backend-java/src/main/java/com/codereviewx/backend/rag/indexing/RagIndexWorker.java`
- Create: `backend-java/src/main/java/com/codereviewx/backend/rag/service/RagIndexService.java`
- Test: `backend-java/src/test/java/com/codereviewx/backend/rag/indexing/RagIndexWorkerIntegrationTest.java`

- [ ] **Step 1: 写索引状态机测试**

状态仅允许 `QUEUED -> RUNNING -> READY|FAILED`；相同 repo/commit/model/version 的重复请求返回已有 READY 快照；同一仓库同时只能有一个 RUNNING job；失败保留上一 READY 快照。

- [ ] **Step 2: 写增量索引测试**

第一次写入全部文件；第二次 commit 复用 content hash 未变化文件的 chunk/embedding，仅嵌入新增或变化 chunk；删除文件不会出现在新 commit 快照；写入失败不得把 repository 标为 READY。

- [ ] **Step 3: 运行测试确认失败**

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn -Dtest=RagIndexWorkerIntegrationTest test
```

Expected: FAIL，store 和 worker 尚不存在。

- [ ] **Step 4: 实现 JDBC stores**

所有向量写入使用参数化 SQL 和 pgvector JDBC 类型；每批最多 100 chunks；repository active SHA 只在事务末尾更新；`result_summary_json` 只存 chunk id/path/line/score，不存源码。

- [ ] **Step 5: 实现异步 worker**

使用专用 `ThreadPoolTaskExecutor`，默认 core=1/max=2/queue=20；job 创建先提交事务，再异步领取。领取 SQL 使用 `FOR UPDATE SKIP LOCKED`；调度器每 5 秒领取 QUEUED job；应用重启时把超过 15 分钟的 RUNNING job 重新置为 QUEUED，达到 3 次尝试后才标记 FAILED。

- [ ] **Step 6: 验证测试**

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn -Dtest=RagIndexWorkerIntegrationTest test
```

Expected: PASS，重复索引不新增 embedding 请求，失败任务不破坏已有快照。

- [ ] **Step 7: Commit**

```bash
git add backend-java/src/main/java/com/codereviewx/backend/rag backend-java/src/test/java/com/codereviewx/backend/rag
git commit -m "feat: add persistent incremental rag indexing"
```

### Task 5: 实现 PR 查询构建和混合召回

**Files:**
- Create: `backend-java/src/main/java/com/codereviewx/backend/rag/retrieval/PrRetrievalQueryBuilder.java`
- Create: `backend-java/src/main/java/com/codereviewx/backend/rag/retrieval/VectorRetriever.java`
- Create: `backend-java/src/main/java/com/codereviewx/backend/rag/retrieval/LexicalRetriever.java`
- Create: `backend-java/src/main/java/com/codereviewx/backend/rag/retrieval/ReciprocalRankFusion.java`
- Create: `backend-java/src/main/java/com/codereviewx/backend/rag/retrieval/HybridRagRetrievalService.java`
- Test: `backend-java/src/test/java/com/codereviewx/backend/rag/retrieval/PrRetrievalQueryBuilderTest.java`
- Test: `backend-java/src/test/java/com/codereviewx/backend/rag/retrieval/ReciprocalRankFusionTest.java`
- Test: `backend-java/src/test/java/com/codereviewx/backend/rag/retrieval/HybridRagRetrievalServiceIntegrationTest.java`

- [ ] **Step 1: 写 query builder 测试**

query 只使用 PR title、changed paths、diff hunk headers、变更符号和受限 changed lines；移除 patch 标记和重复行；最终最多 8,000 字符。敏感高熵字符串替换为 `[REDACTED]`。

- [ ] **Step 2: 写 RRF 确定性测试**

使用 `score += 1 / (60 + rank)`；相同 score 按 changed-file boost、path、chunk id 排序；同一 chunk 跨两路去重。

- [ ] **Step 3: 写 PostgreSQL 检索测试**

固定语料验证 vector top 40、lexical top 40 都限定 `repository_id + commit_sha`；不存在目标快照时返回 `INDEX_NOT_READY`，不能偷用 active branch 的其他 SHA。

- [ ] **Step 4: 运行测试确认失败**

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn -Dtest=PrRetrievalQueryBuilderTest,ReciprocalRankFusionTest,HybridRagRetrievalServiceIntegrationTest test
```

Expected: FAIL，检索组件尚不存在。

- [ ] **Step 5: 实现两路召回和融合**

向量 SQL 使用 cosine distance；全文 SQL 使用 `websearch_to_tsquery('simple', :query)` 和 `ts_rank_cd`。changed path 完全匹配乘 1.25 boost，同目录乘 1.10 boost，最终交给 RRF，不直接比较 cosine 与 ts_rank 原始值。

- [ ] **Step 6: 验证测试**

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn -Dtest=PrRetrievalQueryBuilderTest,ReciprocalRankFusionTest,HybridRagRetrievalServiceIntegrationTest test
```

Expected: PASS，跨 commit 污染用例为 0。

- [ ] **Step 7: Commit**

```bash
git add backend-java/src/main/java/com/codereviewx/backend/rag backend-java/src/test/java/com/codereviewx/backend/rag
git commit -m "feat: add hybrid repository retrieval"
```

### Task 6: 实现重排、去冗余和上下文预算

**Files:**
- Create: `backend-java/src/main/java/com/codereviewx/backend/rag/retrieval/RagContextAssembler.java`
- Create: `backend-java/src/main/java/com/codereviewx/backend/rag/retrieval/RagEvidence.java`
- Create: `backend-java/src/main/java/com/codereviewx/backend/rag/retrieval/RagEvidenceBundle.java`
- Test: `backend-java/src/test/java/com/codereviewx/backend/rag/retrieval/RagContextAssemblerTest.java`

- [ ] **Step 1: 写预算和去冗余测试**

重排输入最多 30 chunks，输出最多 12；Jaccard token overlap 大于 0.85 的相邻 chunk 只保留高分项；每文件最多 3 chunks；总字符不超过 36,000；至少保留一个 changed-file chunk（若候选中存在）。

- [ ] **Step 2: 写 rerank 故障测试**

rerank timeout/5xx 时回退 RRF 顺序并设置 `degraded=true, reason=RERANK_UNAVAILABLE`；embedding 或两路检索全失败才触发旧 context fallback。

- [ ] **Step 3: 运行测试确认失败**

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn -Dtest=RagContextAssemblerTest test
```

Expected: FAIL，assembler 尚不存在。

- [ ] **Step 4: 实现 evidence 格式**

进入 prompt 的文本必须是：

```text
[EVIDENCE C1]
path: src/main/java/example/AuthService.java
lines: 42-78
commit: <head-sha>
content:
<bounded code>
[/EVIDENCE C1]
```

label 按最终顺序生成 `C1..C12`，不把数据库自增 id 暴露给模型。

- [ ] **Step 5: 验证测试**

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn -Dtest=RagContextAssemblerTest test
```

Expected: PASS，所有超预算和降级用例结果稳定。

- [ ] **Step 6: Commit**

```bash
git add backend-java/src/main/java/com/codereviewx/backend/rag backend-java/src/test/java/com/codereviewx/backend/rag
git commit -m "feat: add bounded reranked rag evidence"
```

### Task 7: 接入审查主链路并强制证据引用

**Files:**
- Modify: `backend-java/src/main/java/com/codereviewx/backend/review/service/ReviewTaskService.java`
- Modify: `backend-java/src/main/java/com/codereviewx/backend/review/pipeline/ReviewContext.java`
- Modify: `backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/mimo/ReviewPromptBuilder.java`
- Modify: `backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/mimo/CandidateReview.java`
- Modify: `backend-java/src/main/java/com/codereviewx/backend/review/pipeline/ReviewFinding.java`
- Modify: `backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/mimo/MiMoIssueGenerator.java`
- Modify: `backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/mimo/XiaomiMiMoFindingParser.java`
- Create: `backend-java/src/main/java/com/codereviewx/backend/review/service/ReviewEvidenceValidator.java`
- Create: `backend-java/src/main/java/com/codereviewx/backend/rag/service/RagReviewContextFacade.java`
- Create: `backend-java/src/main/java/com/codereviewx/backend/rag/persistence/ReviewIssueEvidenceStore.java`
- Test: `backend-java/src/test/java/com/codereviewx/backend/review/service/ReviewTaskServiceRagIntegrationTest.java`
- Modify: `backend-java/src/test/java/com/codereviewx/backend/review/pipeline/provider/mimo/ReviewPromptBuilderTest.java`

- [ ] **Step 1: 写完整 trace 顺序测试**

RAG READY 时断言新增 trace 顺序与第 1.2 节一致；`repository.context.index` 不再出现在成功 RAG 路径。RAG disabled 时现有测试链路不变。

- [ ] **Step 2: 写证据引用失败测试**

Candidate finding schema 增加：

```json
{
  "evidenceChunkIds": ["C2", "C5"]
}
```

不存在的 label、空证据、证据路径与 finding 路径冲突、行号不在 diff/evidence 可解释范围时，finding 被标记为 ungrounded 且不生成 comment preview。

- [ ] **Step 3: 运行测试确认失败**

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn -Dtest=ReviewTaskServiceRagIntegrationTest,ReviewPromptBuilderTest test
```

Expected: FAIL，当前 prompt 和 finding schema 不支持 evidence。

- [ ] **Step 4: 接入 facade**

`ReviewTaskService` 只调用 `RagReviewContextFacade.prepare(metadata, diff, runId)`；facade 负责 ensure-index、retrieve、trace 和 fallback。索引未 READY 时默认等待最多 20 秒轮询；超时回退 bounded context，不在请求中做全量索引。

- [ ] **Step 5: 修改 prompt 和 parser**

Executor 规则加入“每条 finding 至少引用一个 evidence label；不得引用未提供 label；引用只用于 grounding，不得把 evidence block 原样复制到 description”。Gatekeeper 同时验证 evidence labels。

- [ ] **Step 6: 持久化 evidence**

issue 保存后将已验证 label 写入 `review_issue_evidence`，excerpt 最多 2,000 字符；API 不返回完整 chunk content。

- [ ] **Step 7: 验证新旧路径**

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn -Dtest=ReviewTaskServiceRagIntegrationTest,ReviewTaskServiceGithubPrTest,ReviewPromptBuilderTest test
```

Expected: RAG、fallback、RAG disabled 三条路径 PASS；所有公开响应不包含 API key、完整 prompt 或完整仓库代码。

- [ ] **Step 8: Commit**

```bash
git add backend-java/src/main/java/com/codereviewx/backend/review backend-java/src/main/java/com/codereviewx/backend/rag backend-java/src/test/java/com/codereviewx/backend/review
git commit -m "feat: ground review findings with rag evidence"
```

### Task 8: 增加索引管理 API 和前端可见状态

**Files:**
- Create: `backend-java/src/main/java/com/codereviewx/backend/rag/controller/RepositoryIndexController.java`
- Create: `backend-java/src/main/java/com/codereviewx/backend/rag/controller/RetrievalEvidenceController.java`
- Create: `backend-java/src/main/java/com/codereviewx/backend/rag/dto/RepositoryIndexResponse.java`
- Create: `backend-java/src/main/java/com/codereviewx/backend/rag/dto/RetrievalTraceResponse.java`
- Test: `backend-java/src/test/java/com/codereviewx/backend/rag/controller/RepositoryIndexControllerTest.java`
- Modify: `frontend/src/types/reviewTask.ts`
- Modify: `frontend/src/api/reviewTaskApi.ts`
- Create: `frontend/src/components/RepositoryIndexStatus.tsx`
- Create: `frontend/src/components/RetrievalEvidencePanel.tsx`
- Modify: `frontend/src/components/ReviewTaskDetail.tsx`
- Test: `frontend/src/test/ReviewTaskDetail.test.tsx`

- [ ] **Step 1: 写 Controller contract 测试**

覆盖 202 建 job、200 READY status、404 repo、409 重复 RUNNING、503 RAG disabled；响应只返回状态、计数、模型名、SHA、耗时和安全错误。

- [ ] **Step 2: 写前端组件测试**

展示 `Not indexed / Queued / Indexing / Ready / Failed / Degraded`；issue 展开后显示证据 path、line、excerpt、rank；降级审查必须有明确 badge，不能显示为完整 RAG 命中。

- [ ] **Step 3: 运行测试确认失败**

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn -Dtest=RepositoryIndexControllerTest test
cd ../frontend
npm test -- --run ReviewTaskDetail.test.tsx
```

Expected: FAIL，API 和组件尚不存在。

- [ ] **Step 4: 实现 API 和组件**

索引按钮只发起异步 job；前端每 2 秒轮询，READY/FAILED 后停止；组件复用现有 panel 样式，不新增全局状态库。

- [ ] **Step 5: 验证后端和前端**

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn -Dtest=RepositoryIndexControllerTest test
cd ../frontend
npm test -- --run ReviewTaskDetail.test.tsx
npm run typecheck
```

Expected: PASS，无 TypeScript 错误。

- [ ] **Step 6: Commit**

```bash
git add backend-java/src/main/java/com/codereviewx/backend/rag backend-java/src/test/java/com/codereviewx/backend/rag frontend/src
git commit -m "feat: expose rag index and evidence status"
```

### Task 9: 建立 retrieval 与端到端评测门禁

**Files:**
- Create: `evals/rag/corpus/sample-repo/*`
- Create: `evals/rag/cases/rag-001-cross-file-call.json`
- Create: `evals/rag/cases/rag-002-config-security.json`
- Create: `evals/rag/cases/rag-003-test-impact.json`
- Create: `evals/rag/cases/rag-004-negative-control.json`
- Create: `scripts/run-rag-evals.mjs`
- Modify: `evals/README.md`
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: 定义 case schema**

每个 case 包含 `query`、`changedPaths`、`relevantChunkKeys`、`forbiddenChunkKeys`、`expectedFinding`。runner 输出 Recall@5、Recall@10、MRR@10、nDCG@10、forbidden hit rate、平均 selected chunks 和 p95 latency。

- [ ] **Step 2: 先运行空实现确认失败**

```bash
node scripts/run-rag-evals.mjs
```

Expected: FAIL，runner/cases 尚不存在。

- [ ] **Step 3: 实现固定语料和 runner**

CI 使用 deterministic fake embeddings 验证融合/过滤/预算；单独 `RAG_LIVE_EVAL=1` 才调用真实 embedding/rerank endpoint。报告写入 `evals/rag/reports/latest.json` 和 `latest.md`。

- [ ] **Step 4: 设置合并门禁**

离线阈值：

```text
Recall@10 >= 0.85
MRR@10 >= 0.70
nDCG@10 >= 0.75
forbidden hit rate = 0
context budget violations = 0
cross-commit contamination = 0
```

低于阈值 runner 返回非 0，CI 失败。

- [ ] **Step 5: 运行 evals**

```bash
node scripts/run-rag-evals.mjs
node scripts/run-evals.mjs
```

Expected: 两套 eval PASS，原有 finding benchmark 不退化。

- [ ] **Step 6: Commit**

```bash
git add evals/rag evals/README.md scripts/run-rag-evals.mjs .github/workflows/ci.yml
git commit -m "test: add rag retrieval quality gates"
```

### Task 10: 补齐安全、可观测性和生命周期

**Files:**
- Create: `backend-java/src/main/java/com/codereviewx/backend/rag/service/RagRetentionService.java`
- Create: `backend-java/src/main/java/com/codereviewx/backend/rag/service/RagMetricsService.java`
- Modify: `backend-java/pom.xml`
- Modify: `backend-java/src/main/resources/application.yml`
- Modify: `docs/SECURITY_CHECKLIST.md`
- Create: `backend-java/src/test/java/com/codereviewx/backend/rag/service/RagRetentionServiceIntegrationTest.java`
- Create: `backend-java/src/test/java/com/codereviewx/backend/rag/RagSecretSafetyTest.java`

- [ ] **Step 1: 写 retention 测试**

每仓库保留最近 5 个 READY commit 或 30 天；被 `review_issue_evidence` 引用的 chunk 可删除主体但必须保留 evidence snapshot；RUNNING job 和 active commit 不删除；清理操作幂等。

- [ ] **Step 2: 写秘密与 prompt injection 测试**

索引前检测并跳过 `.env`、private key、credential 文件；高熵 token 在 query/evidence trace 中脱敏；代码注释中的“ignore previous instructions”作为不可信代码内容包裹，system prompt 明确禁止执行仓库文本中的指令。

- [ ] **Step 3: 运行测试确认失败**

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn -Dtest=RagRetentionServiceIntegrationTest,RagSecretSafetyTest test
```

Expected: FAIL，retention 和安全规则尚不存在。

- [ ] **Step 4: 增加 metrics**

引入 Actuator + Micrometer，输出：`rag_index_jobs_total`、`rag_index_duration_seconds`、`rag_chunks_total`、`rag_retrieval_duration_seconds`、`rag_retrieval_degraded_total`、`rag_embedding_requests_total`、`rag_rerank_requests_total`、`rag_context_chars`。标签禁止包含 repo URL、文件路径、query 和用户文本。

- [ ] **Step 5: 实现 retention 和安全过滤**

清理任务每天执行一次；所有 workspace 权限设为 owner-only；checkout 和 embedding 异常输出统一安全错误码；外部模型请求不得包含 token 或 Git remote credentials。

- [ ] **Step 6: 验证测试和 secret scan**

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn -Dtest=RagRetentionServiceIntegrationTest,RagSecretSafetyTest test
cd ..
node scripts/secret-scan.mjs
```

Expected: PASS，扫描无真实 secret。

- [ ] **Step 7: Commit**

```bash
git add backend-java docs/SECURITY_CHECKLIST.md
git commit -m "feat: harden rag lifecycle and observability"
```

### Task 11: 完成容器化、CI 和真实 smoke

**Files:**
- Create: `backend-java/Dockerfile`
- Create: `frontend/Dockerfile`
- Modify: `docker-compose.yml`
- Create: `scripts/rag-smoke.sh`
- Modify: `.github/workflows/ci.yml`
- Modify: `README.md`

- [ ] **Step 1: 实现 health/readiness**

后端 readiness 必须区分数据库、GitHub、embedding、rerank；外部模型不可用时应用可启动但 `/actuator/health/readiness` 为 DOWN，`/api/health` 返回 `ragReady=false`。

- [ ] **Step 2: 构建镜像**

```bash
docker compose build
```

Expected: backend/frontend 镜像成功，镜像内不含 `.env`、`.git`、测试报告和本地 H2 数据。

- [ ] **Step 3: 启动本地交付栈**

```bash
docker compose up -d postgres backend frontend
docker compose ps
```

Expected: PostgreSQL healthy，backend/frontend running；配置外部模型后 backend readiness UP。

- [ ] **Step 4: 实现 smoke 脚本**

`scripts/rag-smoke.sh` 执行：健康检查 -> 提交 index job -> 等待 READY（最多 5 分钟）-> 创建真实或 fixture PR review -> 断言 trace 包含 RAG 步骤 -> 断言 issue evidence 非空 -> 验证未确认时不发布评论。

- [ ] **Step 5: 运行 smoke**

```bash
bash scripts/rag-smoke.sh
```

Expected: 输出 `RAG_SMOKE_PASS`，同时打印 jobId、runId、selectedChunkCount、degraded=false；不打印 key 和完整源码。

- [ ] **Step 6: 增加 CI PostgreSQL job**

CI 至少执行 migration integration test、全部 backend tests、frontend tests/typecheck/build、RAG eval、原 eval、static scan、secret scan、Docker build。

- [ ] **Step 7: Commit**

```bash
git add backend-java/Dockerfile frontend/Dockerfile docker-compose.yml scripts/rag-smoke.sh .github/workflows/ci.yml README.md
git commit -m "build: package and verify rag delivery stack"
```

### Task 12: 文档、灰度、回滚和最终验收

**Files:**
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/API.md`
- Modify: `docs/DATABASE.md`
- Modify: `docs/PROJECT_SUMMARY_AND_NEXT_STEPS.md`
- Create: `docs/RAG_OPERATIONS.md`
- Create: `docs/RAG_EVALUATION.md`
- Modify: `README.md`

- [ ] **Step 1: 更新事实文档**

明确区分：索引、retrieval、rerank、generation、evidence validation、GitHub publish；记录所有环境变量、API、表、状态机、限制、错误码、数据保留和降级行为。

- [ ] **Step 2: 写运行手册**

`RAG_OPERATIONS.md` 必须包含首次建索引、强制 reindex、模型/维度升级、失败 job 恢复、磁盘增长、PostgreSQL 备份恢复、删除仓库数据、embedding/rerank 供应商切换和事故降级步骤。

- [ ] **Step 3: 写灰度开关**

```text
RAG_ENABLED=false                  # 总开关
RAG_REVIEW_PERCENTAGE=0            # 0/10/50/100 灰度
RAG_FALLBACK_ENABLED=true          # 旧 context fallback
RAG_REQUIRE_EVIDENCE=true          # 发布前证据门禁
```

灰度按 `reviewTaskId % 100` 稳定分桶。10% 阶段观察至少 20 次审查且无 P0/P1；50% 阶段观察至少 50 次；再升 100%。

- [ ] **Step 4: 固化回滚步骤**

应用回滚只需设置 `RAG_REVIEW_PERCENTAGE=0` 并重启，恢复旧 bounded context；数据库 V4 不做 down migration，不删除索引数据；若 PostgreSQL 不可用，停止新 RAG review 并保留已有 H2 demo profile。

- [ ] **Step 5: 运行最终验证矩阵**

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
cd ../frontend
npm ci
npm test -- --run
npm run typecheck
npm run build
cd ..
node scripts/run-evals.mjs
node scripts/run-rag-evals.mjs
node scripts/static-scan.mjs
node scripts/secret-scan.mjs
node scripts/dependency-scan.mjs
docker compose build
bash scripts/rag-smoke.sh
git diff --check
```

Expected: 全部退出码 0；smoke 输出 `RAG_SMOKE_PASS`；git diff 无 whitespace error。

- [ ] **Step 6: 记录交付证据**

在 `docs/PROJECT_SUMMARY_AND_NEXT_STEPS.md` 写入实际验证日期、commit SHA、CI run、eval 指标、smoke jobId/runId 和已知限制。未真实执行的命令不得写成已通过。

- [ ] **Step 7: Commit**

```bash
git add README.md docs
git commit -m "docs: complete rag delivery and operations guide"
```

## 6. 阶段门禁和排期建议

| 阶段 | Tasks | 建议工期 | 可演示产物 | 进入下一阶段条件 |
|---|---:|---:|---|---|
| A 数据底座 | 1-2 | 2-3 天 | pgvector migration、模型 client | PostgreSQL integration tests 全绿 |
| B 索引能力 | 3-4 | 3-4 天 | repo -> chunk -> embedding -> snapshot | 同 commit 幂等、增量索引有效 |
| C 检索能力 | 5-6 | 2-3 天 | hybrid + RRF + rerank evidence bundle | 固定语料 Recall@10 达标 |
| D 审查接入 | 7-8 | 3-4 天 | 带证据 issue、索引状态 UI | RAG/fallback/disabled 三路径全绿 |
| E 交付加固 | 9-12 | 3-5 天 | eval、metrics、Compose、runbook | 最终验证矩阵和真实 smoke 全绿 |

单人完整交付估算为 13-19 个工作日。若并行执行，只允许以下写集并行：

- Lane 1：Task 1-4，负责数据库和索引。
- Lane 2：Task 5-6，必须等 Task 2 和 schema contract 固化后开始。
- Lane 3：Task 8 前端部分，可在 API DTO 固化后开始。
- Lane 4：Task 9 eval corpus 可与 Task 3-6 并行，但 runner 接口最后对齐。
- Task 7、10-12 涉及集成和交付门禁，必须串行收口。

## 7. 发布 SLO 与验收阈值

### 7.1 功能验收

- 相同仓库相同 commit 重复索引不重复调用 embedding。
- changed file 能召回调用方、被调用方、配置和测试中的相关 chunk。
- 每条进入 comment preview 的 MiMo issue 至少有 1 条合法 evidence。
- evidence 的 commit、path、line、hash 与审查快照一致。
- RAG 降级时 UI、trace、API 都显示 degraded，不隐瞒 fallback。
- 用户未 `confirmed=true` 时，任何路径都不能发布 GitHub 评论。

### 7.2 质量验收

```text
Retrieval Recall@10 >= 0.85
MRR@10 >= 0.70
nDCG@10 >= 0.75
Forbidden hit rate = 0
Cross-commit contamination = 0
Evidence validation pass rate >= 0.95
Grounded finding precision >= current baseline
```

### 7.3 性能和成本验收

在 1,000 文件、10,000 chunks 的标准仓库上：

```text
增量索引（<= 20 changed files）p95 <= 60s
混合召回 + rerank p95 <= 3s
RAG 额外 review latency p95 <= 5s
单次上下文 <= 36,000 chars
单次 rerank candidates <= 30
单次最终 evidence chunks <= 12
```

超出阈值时先降低候选数和上下文预算，不通过增加 review 请求超时掩盖问题。

### 7.4 安全验收

- GitHub token、embedding key、rerank key、MiMo key 不进入数据库、URL、trace、异常或日志。
- checkout 不能访问工作目录外路径，不能执行仓库脚本。
- `.env`、private key、二进制和超大文件默认不索引。
- 仓库文本中的 prompt instruction 被视为不可信数据。
- API 默认不返回完整 chunk；evidence excerpt 上限 2,000 字符。

## 8. Definition of Done

只有以下全部成立，才能把 README 中的描述从 `changed-file context index` 改为 `full-repository hybrid RAG`：

- Tasks 1-12 均有独立 commit 和对应测试证据。
- PostgreSQL/pgvector 是 RAG profile 的唯一真实检索数据库。
- 真实仓库完成一次 index -> review -> evidence -> preview smoke。
- retrieval eval 达到第 7.2 节阈值，且报告已提交。
- CI 对 migration、backend、frontend、eval、security scan 和 Docker build 全部通过。
- 运维文档给出重建、升级、故障、数据清理和回滚操作。
- `docs/PROJECT_SUMMARY_AND_NEXT_STEPS.md` 按真实状态更新，不再保留与代码冲突的“无 RAG”描述，也不夸大尚未完成的生产能力。

## 9. 首个执行批次

实施时不要一次打开 12 个任务。首批只执行 Task 1-2，交付检查点为：

```text
1. PostgreSQL + pgvector migration 在 Testcontainers 中通过。
2. 现有 H2 测试无回归。
3. embedding/rerank clients 的成功、限流、鉴权失败、超时和脱敏测试通过。
4. docker compose postgres 健康。
5. 提交两个独立 commits，并记录实际命令输出。
```

检查点通过后再进入 repo checkout 和 indexing；如果 embedding 供应商或 1024 维模型需要变更，应在 Task 2 结束时决策，不能等已有生产向量后修改。
