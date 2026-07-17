package com.codereviewx.backend.rag.model;

import com.codereviewx.backend.rag.indexing.CheckedOutRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class CheckedOutRepositoryApiTest {

    @Test
    void exposesNoPublicConstructorOrManagedFactory() {
        assertThat(CheckedOutRepository.class.getConstructors()).isEmpty();
        assertThat(Arrays.stream(CheckedOutRepository.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> Modifier.isStatic(method.getModifiers())))
                .isEmpty();
        assertThat(Arrays.stream(CheckedOutRepository.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(method -> method.getName()))
                .containsExactlyInAnyOrder("path", "commitSha", "close");
    }
}
