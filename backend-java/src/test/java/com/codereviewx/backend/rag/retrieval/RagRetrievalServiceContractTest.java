package com.codereviewx.backend.rag.retrieval;

import com.codereviewx.backend.rag.service.RagReviewContextFacade;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class RagRetrievalServiceContractTest {

    @Test
    void hybridRetrievalImplementsStableInterfaceUsedByReviewFacade() throws Exception {
        assertThat(RagRetrievalService.class).isAssignableFrom(HybridRagRetrievalService.class);

        Method retrieve = RagRetrievalService.class.getMethod("retrieve", RagRetrievalRequest.class);
        assertThat(retrieve.getReturnType()).isEqualTo(RagRetrievalResult.class);

        Field retrievalServices = RagReviewContextFacade.class.getDeclaredField("retrievalServices");
        assertThat(retrievalServices.getGenericType().getTypeName())
                .contains(RagRetrievalService.class.getName())
                .doesNotContain(HybridRagRetrievalService.class.getName());

        assertThat(publicTypeGraph(RagRetrievalService.class, RagRetrievalRequest.class, RagRetrievalResult.class))
                .noneMatch(type -> type.startsWith(HybridRagRetrievalService.class.getName())
                        || type.startsWith(PrRetrievalQueryBuilder.class.getName())
                        || type.startsWith(RagContextAssembler.class.getName()));
    }

    @Test
    void resultDefensivelyCopiesMatches() {
        List<RagRetrievedChunk> matches = new ArrayList<>();
        matches.add(chunk());

        RagRetrievalResult result = new RagRetrievalResult(RagRetrievalResult.Status.READY, 3L, 1, 1,
                matches, RagRetrievalHealth.HEALTHY);
        matches.clear();

        assertThat(result.matches()).containsExactly(chunk());
        assertThat(result.matches()).isUnmodifiable();
    }

    @Test
    void resultRejectsNullsAndNegativeCandidateCounts() {
        assertThatNullPointerException().isThrownBy(() -> new RagRetrievalResult(null, 3L, 0, 0,
                List.of(), RagRetrievalHealth.HEALTHY));
        assertThatNullPointerException().isThrownBy(() -> new RagRetrievalResult(RagRetrievalResult.Status.READY,
                3L, 0, 0, null, RagRetrievalHealth.HEALTHY));
        assertThatNullPointerException().isThrownBy(() -> new RagRetrievalResult(RagRetrievalResult.Status.READY,
                3L, 0, 0, List.of(), null));
        assertThatIllegalArgumentException().isThrownBy(() -> new RagRetrievalResult(RagRetrievalResult.Status.READY,
                3L, -1, 0, List.of(), RagRetrievalHealth.HEALTHY));
        assertThatIllegalArgumentException().isThrownBy(() -> new RagRetrievalResult(RagRetrievalResult.Status.READY,
                3L, 0, -1, List.of(), RagRetrievalHealth.HEALTHY));
    }

    @Test
    void requestAndQueryRejectInvalidValuesAndDefensivelyCopyLists() {
        List<String> paths = new ArrayList<>(List.of("src/A.java"));
        RagRetrievalQuery query = new RagRetrievalQuery(
                "title", paths, List.of(), List.of(), List.of());
        RagRetrievalRequest request = new RagRetrievalRequest(1L, "a".repeat(40), query);
        paths.clear();

        assertThat(request.query().changedPaths()).containsExactly("src/A.java");
        assertThat(request.query().changedPaths()).isUnmodifiable();
        assertThatIllegalArgumentException().isThrownBy(() -> new RagRetrievalRequest(0, "a".repeat(40), query));
        assertThatIllegalArgumentException().isThrownBy(() -> new RagRetrievalRequest(1, " ", query));
        assertThatNullPointerException().isThrownBy(() -> new RagRetrievalRequest(1, "a".repeat(40), null));
        assertThatNullPointerException().isThrownBy(() -> new RagRetrievalQuery(
                "title", null, List.of(), List.of(), List.of()));
    }

    private static Set<String> publicTypeGraph(Class<?>... roots) {
        Set<String> graph = new HashSet<>();
        Set<Class<?>> visited = new HashSet<>();
        Arrays.stream(roots).forEach(root -> visit(root, graph, visited));
        return graph;
    }

    private static void visit(Type type, Set<String> graph, Set<Class<?>> visited) {
        graph.add(type.getTypeName());
        if (type instanceof ParameterizedType parameterized) {
            visit(parameterized.getRawType(), graph, visited);
            Arrays.stream(parameterized.getActualTypeArguments()).forEach(argument -> visit(argument, graph, visited));
        } else if (type instanceof GenericArrayType array) {
            visit(array.getGenericComponentType(), graph, visited);
        } else if (type instanceof WildcardType wildcard) {
            Arrays.stream(wildcard.getUpperBounds()).forEach(bound -> visit(bound, graph, visited));
            Arrays.stream(wildcard.getLowerBounds()).forEach(bound -> visit(bound, graph, visited));
        } else if (type instanceof Class<?> typeClass && visited.add(typeClass)
                && typeClass.getPackageName().startsWith("com.codereviewx.backend.rag.retrieval")) {
            Arrays.stream(typeClass.getDeclaredMethods()).filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                    .forEach(method -> {
                        visit(method.getGenericReturnType(), graph, visited);
                        Arrays.stream(method.getGenericParameterTypes()).forEach(parameter -> visit(parameter, graph, visited));
                    });
            if (typeClass.isRecord()) {
                Arrays.stream(typeClass.getRecordComponents()).map(RecordComponent::getGenericType)
                        .forEach(component -> visit(component, graph, visited));
            }
        }
    }

    private static RagRetrievedChunk chunk() {
        return new RagRetrievedChunk(1, "src/A.java", "JAVA", "A", 1, 2,
                "hash", "content", 1.0, 0.5);
    }
}
