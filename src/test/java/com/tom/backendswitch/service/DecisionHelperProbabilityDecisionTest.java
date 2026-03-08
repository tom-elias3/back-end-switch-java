package com.tom.backendswitch.service;

import com.tom.backendswitch.model.Pattern;
import com.tom.backendswitch.model.ResolutionType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DecisionHelperProbabilityDecisionTest {

    private static final String DESTINATION = "https://dest.example.com";

    @Test
    void probability100AlwaysReturnsDestination() {
        assertThat(DecisionHelper.probabilityDecision(pattern("RANDOM:100"))).isEqualTo(DESTINATION);
    }

    @Test
    void probability0AlwaysReturnsNull() {
        assertThat(DecisionHelper.probabilityDecision(pattern("RANDOM:0"))).isNull();
    }

    @Test
    void probabilityAbove100ThrowsRuntimeException() {
        assertThatThrownBy(() -> DecisionHelper.probabilityDecision(pattern("RANDOM:101")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("101");
    }

    @Test
    void probabilityBelowZeroThrowsRuntimeException() {
        assertThatThrownBy(() -> DecisionHelper.probabilityDecision(pattern("RANDOM:-1")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("-1");
    }

    @Test
    void intermediateProbabilityReturnsOnlyValidOutcomes() {
        Set<String> outcomes = IntStream.range(0, 200)
                .mapToObj(i -> DecisionHelper.probabilityDecision(pattern("RANDOM:50")))
                .collect(Collectors.toSet());

        assertThat(outcomes).allSatisfy(o -> assertThat(o).isIn(DESTINATION, null));
    }

    @Test
    void intermediateProbabilityReturnsBothOutcomes() {
        // With 200 runs at 50%, the chance of seeing only one outcome is negligible
        Set<String> outcomes = IntStream.range(0, 200)
                .mapToObj(i -> DecisionHelper.probabilityDecision(pattern("RANDOM:50")))
                .collect(Collectors.toSet());

        assertThat(outcomes).contains(DESTINATION);
        assertThat(outcomes).containsNull();
    }

    // --- helper ---

    private Pattern pattern(String logic) {
        return new Pattern(1, HttpMethod.GET, "https://example.com", logic, DESTINATION, ResolutionType.REDIRECT, null, null);
    }
}
