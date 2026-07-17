package com.codereviewx.backend.review.service;

import com.codereviewx.backend.rag.persistence.ReviewIssueEvidenceStore;
import com.codereviewx.backend.rag.retrieval.RagEvidenceBundle;
import com.codereviewx.backend.rag.retrieval.RagRetrievalHealth;
import com.codereviewx.backend.rag.retrieval.RagRetrievalResult;
import com.codereviewx.backend.rag.retrieval.RagRetrievalService;
import com.codereviewx.backend.rag.retrieval.RagRetrievedChunk;
import com.codereviewx.backend.rag.retrieval.RerankClient;
import com.codereviewx.backend.rag.retrieval.RerankedChunk;
import com.codereviewx.backend.rag.service.RagIndexResolution;
import com.codereviewx.backend.rag.service.RagIndexService;
import com.codereviewx.backend.rag.service.RagManifestSnapshotReader;
import com.codereviewx.backend.review.dto.CreateReviewTaskRequest;
import com.codereviewx.backend.review.dto.ReviewIssueResponse;
import com.codereviewx.backend.review.dto.ReviewTaskResponse;
import com.codereviewx.backend.review.enums.IssueSource;
import com.codereviewx.backend.review.github.GithubPrDiff;
import com.codereviewx.backend.review.github.GithubPrDiffFile;
import com.codereviewx.backend.review.github.GithubPrDiffLoadResult;
import com.codereviewx.backend.review.github.GithubPrDiffLoader;
import com.codereviewx.backend.review.github.GithubPrMetadata;
import com.codereviewx.backend.review.github.GithubPrMetadataLoadResult;
import com.codereviewx.backend.review.github.GithubPrMetadataLoader;
import com.codereviewx.backend.review.pipeline.ReviewFinding;
import com.codereviewx.backend.review.pipeline.provider.mimo.ReviewPromptBuilder;
import com.codereviewx.backend.review.pipeline.provider.mimo.TestMiMoAgentResponses;
import com.codereviewx.backend.review.pipeline.provider.mimo.XiaomiMiMoClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

final class RagFindingProductionEvaluation {
    private static final String ENGINE = "java-production-review-pipeline";

    private final ReviewTaskService service;
    private final GithubPrMetadataLoader metadataLoader;
    private final GithubPrDiffLoader diffLoader;
    private final XiaomiMiMoClient mimoClient;
    private final RagIndexService indexService;
    private final RagManifestSnapshotReader manifestReader;
    private final RagRetrievalService retrievalService;
    private final RerankClient rerankClient;
    private final ReviewIssueEvidenceStore evidenceStore;
    private final ObjectMapper mapper;

    RagFindingProductionEvaluation(ReviewTaskService service, GithubPrMetadataLoader metadataLoader,
                                   GithubPrDiffLoader diffLoader, XiaomiMiMoClient mimoClient,
                                   RagIndexService indexService, RagManifestSnapshotReader manifestReader,
                                   RagRetrievalService retrievalService,
                                   RerankClient rerankClient, ReviewIssueEvidenceStore evidenceStore,
                                   ObjectMapper mapper) {
        this.service = service;
        this.metadataLoader = metadataLoader;
        this.diffLoader = diffLoader;
        this.mimoClient = mimoClient;
        this.indexService = indexService;
        this.manifestReader = manifestReader;
        this.retrievalService = retrievalService;
        this.rerankClient = rerankClient;
        this.evidenceStore = evidenceStore;
        this.mapper = mapper;
    }

    Result run(Mutation mutation, boolean writeReport) throws Exception {
        reset(metadataLoader, diffLoader, mimoClient, indexService, manifestReader, retrievalService,
                rerankClient, evidenceStore);
        Path root = repositoryRoot();
        Path ragRoot = root.resolve("evals/rag");
        List<Path> caseFiles;
        try (var paths = Files.list(ragRoot.resolve("cases"))) {
            caseFiles = paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
        }
        List<RagFindingQualityMetrics.CaseResult> metricCases = new ArrayList<>();
        List<Map<String, Object>> reports = new ArrayList<>();
        int index = 0;
        for (Path caseFile : caseFiles) {
            EvalCase evalCase = readCase(caseFile, ragRoot.resolve("actual").resolve(caseFile.getFileName()));
            Mutation applied = index == 0 ? mutation : Mutation.NONE;
            CaseOutcome outcome = runCase(evalCase, applied, 700 + index);
            metricCases.add(outcome.metrics());
            reports.add(outcome.report());
            index++;
        }
        CaseOutcome dependency = runDependencyCase(799);
        metricCases.add(dependency.metrics());
        reports.add(dependency.report());
        RagFindingQualityMetrics metrics = RagFindingQualityMetrics.from(metricCases);
        Path jsonReport = ragRoot.resolve("reports/java-production-finding-latest.json");
        Path markdownReport = ragRoot.resolve("reports/java-production-finding-latest.md");
        if (writeReport) writeReports(jsonReport, markdownReport, metrics, reports);
        return new Result(metrics, jsonReport, markdownReport);
    }

    private CaseOutcome runCase(EvalCase evalCase, Mutation mutation, int prNumber) throws IOException {
        boolean positive = evalCase.expected().mustExist();
        String findingPath = positive ? evalCase.actual().finding().path() : evalCase.changedPaths().get(0);
        String commit = mutation == Mutation.WRONG_COMMIT ? "mutated-commit" : evalCase.targetCommit();
        GithubPrMetadata metadata = new GithubPrMetadata("evals", "sample-repo", prNumber, evalCase.query(),
                "fixture", "main", "eval", "base-commit", commit, "open", "fixed", "fixed", 1, 1, 0);
        GithubPrDiff diff = diff(findingPath);
        when(metadataLoader.load(anyString(), eq(prNumber))).thenReturn(GithubPrMetadataLoadResult.success(metadata));
        when(diffLoader.load(metadata)).thenReturn(GithubPrDiffLoadResult.success(diff));
        when(indexService.ensureIndexed(metadata)).thenReturn(new RagIndexResolution(
                1L, 2L, commit, RagIndexResolution.Status.READY));

        List<String> selectedKeys = new ArrayList<>(evalCase.actual().evidenceChunkKeys());
        if (positive && !selectedKeys.contains(findingPath)) selectedKeys.add(0, findingPath);
        selectedKeys.sort(Comparator.comparing((String key) -> !key.equals(findingPath)));
        if (mutation == Mutation.WRONG_CHUNK && !selectedKeys.isEmpty()) {
            int required = selectedKeys.indexOf(evalCase.expected().requiredEvidenceKeys().get(0));
            selectedKeys.set(required < 0 ? 0 : required, "src/validation.ts");
        }
        Map<Long, String> keyByChunkId = new LinkedHashMap<>();
        List<RagRetrievedChunk> chunks = new ArrayList<>();
        long chunkId = 10_000L + prNumber * 10L;
        for (String key : selectedKeys) {
            String path = stripChunkSuffix(key);
            String content = corpusContent(path);
            long id = chunkId++;
            keyByChunkId.put(id, key.contains("#") ? key : key + "#1");
            chunks.add(new RagRetrievedChunk(id, path, language(path), path, 1,
                    Math.max(1, (int) content.lines().count()), "hash-" + id, content, 1.0, 1.0));
        }
        when(retrievalService.retrieve(any())).thenReturn(new RagRetrievalResult(
                RagRetrievalResult.Status.READY, 3L, chunks.size(), chunks.size(), chunks,
                RagRetrievalHealth.HEALTHY));
        when(rerankClient.rerank(anyString(), anyList())).thenAnswer(invocation -> {
            List<com.codereviewx.backend.rag.retrieval.RerankCandidate> candidates = invocation.getArgument(1);
            List<RerankedChunk> ranked = new ArrayList<>();
            for (int position = 0; position < candidates.size(); position++) {
                ranked.add(new RerankedChunk(candidates.get(position), 1.0 - position * 0.01));
            }
            return ranked;
        });

        when(mimoClient.complete(eq(ReviewPromptBuilder.PLANNER_SYSTEM_PROMPT), anyString(), anyString()))
                .thenReturn(TestMiMoAgentResponses.taskPlanJson());
        when(mimoClient.complete(eq(ReviewPromptBuilder.EXECUTOR_SYSTEM_PROMPT), anyString(), anyString()))
                .thenReturn(executorOutput(evalCase, mutation, selectedKeys));
        when(mimoClient.complete(eq(ReviewPromptBuilder.GATEKEEPER_SYSTEM_PROMPT), anyString(), anyString()))
                .thenReturn(TestMiMoAgentResponses.approvedGateJson());

        List<PersistedEvidence> persisted = new ArrayList<>();
        AtomicReference<RagEvidenceBundle> persistedBundle = new AtomicReference<>();
        doAnswer(invocation -> {
            ReviewFinding finding = invocation.getArgument(1);
            RagEvidenceBundle bundle = invocation.getArgument(2);
            persisted.add(new PersistedEvidence(finding, bundle));
            persistedBundle.set(bundle);
            return null;
        }).when(evidenceStore).save(any(), any(), any());

        CreateReviewTaskRequest request = new CreateReviewTaskRequest();
        request.setRepoUrl("https://github.com/evals/sample-repo");
        request.setPrNumber(prNumber);
        ReviewTaskResponse response = service.createTask(request);
        List<ReviewIssueResponse> mimoIssues = response.getIssues() == null ? List.of() : response.getIssues().stream()
                .filter(issue -> issue.getSource() == IssueSource.MIMO).toList();

        boolean expectedFields = positive ? mimoIssues.size() == 1 && matches(mimoIssues.get(0), evalCase.expected())
                : mimoIssues.isEmpty();
        RagEvidenceBundle bundle = persistedBundle.get();
        List<String> persistedKeys = bundle == null ? List.of() : bundle.evidence().stream()
                .map(evidence -> keyByChunkId.get(evidence.sourceIdentity().chunkId())).toList();
        boolean requiredEvidence = !positive || persistedKeys.containsAll(evalCase.expected().requiredEvidenceKeys()
                .stream().map(key -> key.contains("#") ? key : key + "#1").toList());
        boolean commitGrounded = !positive || bundle != null && bundle.evidence().stream()
                .allMatch(evidence -> evidence.commitSha().equals(evalCase.targetCommit()));
        boolean evidenceValidated = !positive || persisted.size() == 1;
        boolean grounded = !positive || evidenceValidated && requiredEvidence && commitGrounded;
        boolean expectedPassed = expectedFields && grounded;
        int produced = positive ? 1 : 0;
        int groundedCount = positive && expectedPassed ? 1 : 0;
        RagFindingQualityMetrics.CaseResult metrics = new RagFindingQualityMetrics.CaseResult(
                evalCase.id(), positive, evidenceValidated, grounded, expectedPassed, produced, groundedCount);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("id", evalCase.id());
        report.put("mutation", mutation.name());
        report.put("expectedFinding", positive);
        report.put("producedMimoFindings", mimoIssues.size());
        report.put("persistedEvidenceAssociations", persisted.size());
        report.put("selectedChunkKeys", persistedKeys);
        report.put("evidenceValidated", evidenceValidated);
        report.put("grounded", grounded);
        report.put("expectedFindingPassed", expectedPassed);
        return new CaseOutcome(metrics, report);
    }

    private CaseOutcome runDependencyCase(int prNumber) {
        String path = "package.json";
        String commit = "target-commit";
        GithubPrMetadata metadata = new GithubPrMetadata("evals", "sample-repo", prNumber,
                "dependency hygiene", "fixture", "main", "eval", "base-commit", commit,
                "open", "fixed", "fixed", 1, 1, 0);
        String diffText = "diff --git a/package.json b/package.json\n--- a/package.json\n+++ b/package.json\n"
                + "@@ -5 +5 @@\n+  \"name\": \"changed-package\"\n";
        GithubPrDiff diff = new GithubPrDiff(diffText, 1, diffText.length(), false,
                List.of(new GithubPrDiffFile(path, "modified", 1, 0, 1, diffText.length(), false)));
        when(metadataLoader.load(anyString(), eq(prNumber))).thenReturn(GithubPrMetadataLoadResult.success(metadata));
        when(diffLoader.load(metadata)).thenReturn(GithubPrDiffLoadResult.success(diff));
        when(indexService.ensureIndexed(metadata)).thenReturn(new RagIndexResolution(
                1L, 2L, commit, RagIndexResolution.Status.READY));
        String manifest = "{\n  \"dependencies\": {\n    \"unsafe-package\": \"latest\"\n  },\n"
                + "  \"name\": \"changed-package\"\n}";
        when(manifestReader.read(1L, 2L, commit, List.of(path))).thenReturn(
                new RepositoryContextIndexResult(List.of(new RepositoryContextFile(
                        path, "JSON", manifest.length(), false, manifest)), 1, manifest.length(), false, ""));
        RagRetrievedChunk chunk = new RagRetrievedChunk(99_999L, path, "JSON", path, 1, 1,
                "dependency-hash", "{\"dependencies\":{\"unsafe-package\": \"latest\"}}", 1.0, 1.0);
        when(retrievalService.retrieve(any())).thenReturn(new RagRetrievalResult(
                RagRetrievalResult.Status.READY, 3L, 1, 1, List.of(chunk), RagRetrievalHealth.HEALTHY));
        when(rerankClient.rerank(anyString(), anyList())).thenAnswer(invocation -> {
            List<com.codereviewx.backend.rag.retrieval.RerankCandidate> candidates = invocation.getArgument(1);
            return List.of(new RerankedChunk(candidates.get(0), 1.0));
        });
        when(mimoClient.complete(eq(ReviewPromptBuilder.PLANNER_SYSTEM_PROMPT), anyString(), anyString()))
                .thenReturn(TestMiMoAgentResponses.taskPlanJson());
        when(mimoClient.complete(eq(ReviewPromptBuilder.EXECUTOR_SYSTEM_PROMPT), anyString(), anyString()))
                .thenReturn("{\"summary\":\"no AI finding\",\"findings\":[]}");
        when(mimoClient.complete(eq(ReviewPromptBuilder.GATEKEEPER_SYSTEM_PROMPT), anyString(), anyString()))
                .thenReturn(TestMiMoAgentResponses.approvedGateJson());

        CreateReviewTaskRequest request = new CreateReviewTaskRequest();
        request.setRepoUrl("https://github.com/evals/sample-repo");
        request.setPrNumber(prNumber);
        ReviewTaskResponse response = service.createTask(request);
        List<ReviewIssueResponse> dependencyIssues = response.getIssues().stream()
                .filter(issue -> issue.getSource() == IssueSource.DEPENDENCY).toList();
        boolean passed = dependencyIssues.size() == 1
                && dependencyIssues.get(0).getFilePath().equals(path)
                && dependencyIssues.get(0).getTitle().contains("Unpinned npm dependency");
        RagFindingQualityMetrics.CaseResult metrics = new RagFindingQualityMetrics.CaseResult(
                "rag-005-rag-on-dependency-hygiene", false, true, true, passed, 0, 0);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("id", "rag-005-rag-on-dependency-hygiene");
        report.put("mutation", Mutation.NONE.name());
        report.put("expectedFinding", "DEPENDENCY");
        report.put("producedDependencyFindings", dependencyIssues.size());
        report.put("expectedFindingPassed", passed);
        return new CaseOutcome(metrics, report);
    }

    private String executorOutput(EvalCase evalCase, Mutation mutation, List<String> selectedKeys) throws IOException {
        if (!evalCase.expected().mustExist()) return "{\"summary\":\"negative control\",\"findings\":[]}";
        ActualFinding finding = evalCase.actual().finding();
        String path = mutation == Mutation.WRONG_PATH ? "src/wrong-path.ts" : finding.path();
        int line = mutation == Mutation.UNGROUNDED_FINDING ? 2 : 1;
        List<String> labels = mutation == Mutation.MISSING_EVIDENCE ? List.of() : List.of("C1");
        Map<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("severity", finding.severity());
        candidate.put("category", finding.category().equalsIgnoreCase("correctness") ? "BUG" : finding.category());
        candidate.put("filePath", path);
        candidate.put("startLine", line);
        candidate.put("endLine", line);
        candidate.put("title", finding.title());
        candidate.put("description", finding.description());
        candidate.put("recommendation", finding.recommendation());
        candidate.put("evidenceChunkIds", labels);
        return mapper.writeValueAsString(Map.of("summary", "deterministic fixture", "findings", List.of(candidate)));
    }

    private GithubPrDiff diff(String path) {
        String text = "diff --git a/" + path + " b/" + path + "\n--- a/" + path + "\n+++ b/" + path
                + "\n@@ -1 +1 @@\n+fixture changed line\n";
        return new GithubPrDiff(text, 1, text.length(), false,
                List.of(new GithubPrDiffFile(path, "modified", 1, 0, 1, text.length(), false)));
    }

    private boolean matches(ReviewIssueResponse issue, ExpectedFinding expected) {
        String category = expected.category().equalsIgnoreCase("correctness") ? "BUG" : expected.category();
        String searchable = (issue.getTitle() + " " + issue.getDescription()).toLowerCase(Locale.ROOT);
        return issue.getSeverity().name().equalsIgnoreCase(expected.severity())
                && issue.getCategory().name().equalsIgnoreCase(category)
                && issue.getFilePath().equals(expected.file())
                && expected.keywords().stream().allMatch(keyword -> searchable.contains(keyword.toLowerCase(Locale.ROOT)));
    }

    private EvalCase readCase(Path casePath, Path actualPath) throws IOException {
        JsonNode expected = mapper.readTree(casePath.toFile());
        JsonNode actual = mapper.readTree(actualPath.toFile());
        JsonNode expectedFinding = expected.path("expectedFinding");
        JsonNode actualFinding = actual.path("finding");
        return new EvalCase(expected.path("id").asText(), expected.path("targetCommit").asText(),
                expected.path("query").asText(), strings(expected.path("changedPaths")),
                new ExpectedFinding(expectedFinding.path("mustExist").asBoolean(false),
                        expectedFinding.path("mustBeEmpty").asBoolean(false),
                        expectedFinding.path("severity").asText(), expectedFinding.path("category").asText(),
                        expectedFinding.path("file").asText(), strings(expectedFinding.path("keywords")),
                        strings(expectedFinding.path("requiredEvidenceKeys"))),
                new Actual(actualFinding.isMissingNode() || actualFinding.isNull() ? null : new ActualFinding(
                        actualFinding.path("severity").asText(), actualFinding.path("category").asText(),
                        actualFinding.path("file").asText(), actualFinding.path("title").asText(),
                        actualFinding.path("description").asText(), actualFinding.path("recommendation").asText()),
                        strings(actual.path("evidenceChunkKeys"))));
    }

    private List<String> strings(JsonNode node) {
        List<String> values = new ArrayList<>();
        node.forEach(value -> values.add(value.asText()));
        return values;
    }

    private String corpusContent(String path) throws IOException {
        Path file = repositoryRoot().resolve("evals/rag/corpus/sample-repo").resolve(path).normalize();
        return Files.isRegularFile(file) ? Files.readString(file) : "fixture evidence for " + path;
    }

    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("evals/rag"))) return current;
        if (Files.isDirectory(current.resolve("../evals/rag"))) return current.resolve("..").normalize();
        throw new IllegalStateException("Cannot locate evals/rag from " + current);
    }

    private String stripChunkSuffix(String key) {
        return key.replaceFirst("#\\d+$", "");
    }

    private String language(String path) {
        return path.endsWith(".ts") ? "TYPESCRIPT" : "TEXT";
    }

    private void writeReports(Path jsonPath, Path markdownPath, RagFindingQualityMetrics metrics,
                              List<Map<String, Object>> cases) throws IOException {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schemaVersion", 1);
        report.put("engine", ENGINE);
        report.put("status", metrics.failures().isEmpty() ? "PASS" : "FAIL");
        report.put("fixtures", linkedMap("mimo", "deterministic-json", "network", false));
        report.put("productionPath", List.of("ReviewTaskService", "XiaomiMiMoReviewProvider",
                "MiMoAgentJsonParser", "MiMoIssueGenerator", "ReviewEvidenceValidator",
                "ReviewIssueRepository", "ReviewIssueEvidencePersister"));
        report.put("metrics", linkedMap(
                "evidenceValidationPassRate", metrics.evidenceValidationPassRate(),
                "groundedFindingPrecision", metrics.groundedFindingPrecision(),
                "expectedFindingPassRate", metrics.expectedFindingPassRate()));
        report.put("thresholds", linkedMap(
                "evidenceValidationPassRate", RagFindingQualityMetrics.MIN_EVIDENCE_VALIDATION_PASS_RATE,
                "groundedFindingPrecisionBaseline", RagFindingQualityMetrics.MIN_GROUNDED_FINDING_PRECISION,
                "expectedFindingPassRate", 1.0));
        report.put("failures", metrics.failures());
        report.put("cases", cases);
        Files.createDirectories(jsonPath.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), report);
        Files.writeString(markdownPath, "# Java production finding and evidence eval\n\n"
                + "Result: " + (metrics.failures().isEmpty() ? "PASS" : "FAIL") + "\n\n"
                + "- evidenceValidationPassRate: " + format(metrics.evidenceValidationPassRate()) + "\n"
                + "- groundedFindingPrecision: " + format(metrics.groundedFindingPrecision()) + "\n"
                + "- expectedFindingPassRate: " + format(metrics.expectedFindingPassRate()) + "\n");
    }

    private String format(double value) { return String.format(Locale.ROOT, "%.3f", value); }

    private Map<String, Object> linkedMap(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put((String) values[index], values[index + 1]);
        }
        return result;
    }

    enum Mutation { NONE, MISSING_EVIDENCE, WRONG_CHUNK, WRONG_PATH, WRONG_COMMIT, UNGROUNDED_FINDING }

    record Result(RagFindingQualityMetrics metrics, Path jsonReport, Path markdownReport) {}
    private record CaseOutcome(RagFindingQualityMetrics.CaseResult metrics, Map<String, Object> report) {}
    private record PersistedEvidence(ReviewFinding finding, RagEvidenceBundle bundle) {}
    private record EvalCase(String id, String targetCommit, String query, List<String> changedPaths,
                            ExpectedFinding expected, Actual actual) {}
    private record ExpectedFinding(boolean mustExist, boolean mustBeEmpty, String severity, String category,
                                   String file, List<String> keywords, List<String> requiredEvidenceKeys) {}
    private record Actual(ActualFinding finding, List<String> evidenceChunkKeys) {}
    private record ActualFinding(String severity, String category, String path, String title,
                                 String description, String recommendation) {}
}
