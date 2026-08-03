# RAG Evaluation Contract

## JavaScript reference and mutation gate

`node scripts/run-rag-evals.mjs` runs a deterministic JavaScript reference
retriever and mutation gates. `--self-test` proves that the reference gate can
detect forbidden hits, cross-commit isolation, budget/latency failures,
dropped/removed/corrupted findings, missing evidence, wrong-file evidence, and
invalid rerank coverage. Missing evidence must fail the named
`evidenceValidationPassRate` gate; wrong-file evidence must fail the named
`groundedFindingPrecision` gate. Neither metric is copied from
`expectedFindingPass`.

The JavaScript runner implements its own reference vector, lexical, RRF and
selection logic. It is useful for deterministic mutation testing, external
HTTP fixture validation, and finding/evidence fixtures, but it does not prove
the production Java retrieval implementation. Reports are written to
`evals/rag/reports/latest.{json,md}`. Offline latency is fixed at zero so these
committed reference baselines are reproducible; live HTTP latency reports are
written under ignored `target/rag-reports/js-live-runtime.{json,md}` instead.
Consequently the committed offline p95 is deterministically zero; the slow
mutation, live HTTP mode, and Java performance acceptance cover latency
behavior. CI regenerates and diffs the committed files.

Required gates are Recall@10 >= 0.85, MRR@10 >= 0.70, nDCG@10 >= 0.75,
forbidden-hit and cross-commit contamination equal zero, evidence validation
pass rate >= 0.95, grounded precision at least the recorded baseline, p95
retrieval/rerank <= 3,000 ms, <=30 rerank candidates, <=12 final chunks and
<=36,000 context characters. Live mode additionally validates HTTP timeout,
embedding dimensions/count, and exact unique rerank coverage.

Evaluate changes to corpus, query, model, reranker, or selection budget as a
single report; compare against the committed baseline and record mutations that
must fail. An eval report is evidence, not a substitute for PostgreSQL compose
smoke or CI.

## Java production retrieval quality gate

`RagRetrievalQualityAcceptanceTest` starts `pgvector/pgvector:pg16`, runs all
Flyway migrations, loads `evals/rag/corpus/sample-repo` plus its manifest and
cases into target and old-commit snapshots, and then calls the production
`HybridRagRetrievalService.retrieve()` and `RagContextAssembler.assemble()`
paths:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn -q \
  -Dtest=RagRetrievalQualityAcceptanceTest test
```

Embedding and rerank are deterministic external-model fixtures. PostgreSQL
vector/FTS retrieval, exact snapshot filtering, RRF and context selection are
the production Java classes. Metrics use the persisted `rag_chunk.chunk_key`
from each evidence source identity, not file paths, so separate chunks from one
file cannot satisfy each other or inflate Recall/nDCG. Metrics come from the
final evidence ordering; positive cases contribute Recall@10, MRR@10 and
nDCG@10, while the negative control still contributes forbidden-hit,
cross-commit and context-budget checks. Every case must retrieve at least one
forbidden key from the exact snapshot while selecting none, so the forbidden
gate cannot pass vacuously. The committed report is
`evals/rag/reports/java-production-latest.{json,md}`.

This gate covers only retrieval quality: Recall@10 >= 0.85, MRR@10 >= 0.70,
nDCG@10 >= 0.75, forbidden hits = 0, cross-commit contamination = 0 and context
budget violations = 0. It deliberately excludes model finding generation,
finding quality, evidence-validation pass rate, grounded finding precision and
network model latency. A mutation test changes the relevant key and proves the
quality thresholds fail instead of producing an unconditional PASS.

## Java production finding and evidence gate

`RagFindingQualityAcceptanceTest` reuses the committed cases, corpus and
deterministic structured-model JSON fixtures without network calls. It invokes the
production review provider, structured-output parser, issue generator, evidence
repository and `ReviewIssueEvidencePersister` boundary. GitHub, retrieval,
rerank and model HTTP are deterministic external-boundary fixtures; finding
parsing, generation, validation, filtering and issue persistence are not
reimplemented by the evaluator.

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn -q \
  -Dtest=RagFindingQualityAcceptanceTest test
```

The stable report is
`evals/rag/reports/java-production-finding-latest.{json,md}`. Required gates
are evidence validation pass rate >= 0.95, grounded finding precision >= the
committed 1.0 baseline and expected finding pass rate = 1.0. Missing evidence,
wrong chunk identity, wrong path, wrong commit and a finding outside the diff
must each fail the production gate. The baseline also covers dependency hygiene
on a RAG-enabled changed `package.json`, where legacy repository context is
intentionally empty.

This gate excludes live model quality and network behavior, real GitHub loading,
and PostgreSQL retrieval ranking; those belong to live evaluation, smoke and
the Java retrieval/performance gates respectively. CI runs both Java gates,
regenerates their committed reports and rejects any baseline diff or untracked
replacement.

## PostgreSQL performance acceptance

The performance acceptance remains opt-in for local runs because it starts a real
`pgvector/pgvector:pg16` Testcontainer and inserts 1,000 documents / 10,000
chunks. It runs the production `HybridRagRetrievalService` and
`RagContextAssembler` paths with 3 warmups and 10 measured samples:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn -q \
  -Dtest=RagPerformanceAcceptanceTest \
  -Drag.performance.enabled=true test
```

The run writes volatile `target/rag-reports/performance-runtime.{json,md}` with
the timestamp, image, Java/OS environment, dataset size, sampling counts, raw
samples, p95 and gate limits. `target/` is ignored, so machine and wall-clock
changes never dirty the committed baselines. The reranker is a deterministic
in-process acceptance fixture, while PostgreSQL vector/FTS retrieval and
context assembly are production classes.

The same run indexes two immutable snapshots through `DefaultRagIndexService`,
the leased `RagIndexWorker`, `LineWindowCodeChunker`, deterministic embedding,
transactions and the production PostgreSQL stores. The baseline has 1,000 files
and 10,000 chunks; the second commit changes 20 files and must embed exactly 20
changed chunks while reusing 9,980. The incremental worker processing gate is
60 seconds.

Incremental timing deliberately excludes network JGit fetch and remote file
discovery: a controlled `RepositoryFile` provider supplies both commits. RAG
additional-context p95 includes query embedding fixture, PostgreSQL vector/FTS,
RRF, deterministic rerank and `RagContextAssembler`, but excludes model
generation and network model latency. The report status is `PASS` only when all
covered gates pass.

CI enables this test explicitly with `-Drag.performance.enabled=true` in the
`postgres-integration` job. The job runs migration, Java production retrieval
quality and standard performance acceptance together with a 30-minute timeout;
performance is therefore a required CI gate rather than an optional report.
CI diffs the stable Java quality baseline and uploads the volatile performance
runtime files as a 14-day artifact.
