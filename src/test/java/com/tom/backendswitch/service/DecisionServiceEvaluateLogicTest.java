package com.tom.backendswitch.service;

import com.tom.backendswitch.model.Pattern;
import com.tom.backendswitch.model.ResolutionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class DecisionServiceEvaluateLogicTest {

    @Mock Environment environment;

    private DecisionService service;
    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final String DESTINATION = "https://dest.example.com";

    @BeforeEach
    void setUp() {
        service = new DecisionService(environment);
    }

    // --- RANDOM logic ---

    @Test
    void random100ReturnsDestination() {
        String result = service.evaluateLogic(pattern("RANDOM:100"), Map.of(), Map.of(), null, null, REQUEST_ID);

        assertThat(result).isEqualTo(DESTINATION);
    }

    @Test
    void random0ReturnsNull() {
        String result = service.evaluateLogic(pattern("RANDOM:0"), Map.of(), Map.of(), null, null, REQUEST_ID);

        assertThat(result).isNull();
    }

    // --- expression: claims ---

    @Test
    void claimMatchReturnsDestination() {
        String result = service.evaluateLogic(
                pattern("({claim.role} == admin)"),
                Map.of("role", "admin"), Map.of(), null, null, REQUEST_ID);

        assertThat(result).isEqualTo(DESTINATION);
    }

    @Test
    void claimMismatchReturnsNull() {
        String result = service.evaluateLogic(
                pattern("({claim.role} == admin)"),
                Map.of("role", "user"), Map.of(), null, null, REQUEST_ID);

        assertThat(result).isNull();
    }

    // --- expression: params ---

    @Test
    void paramMatchReturnsDestination() {
        String result = service.evaluateLogic(
                pattern("({param.x} > 3)"),
                Map.of(), Map.of("x", "5"), null, null, REQUEST_ID);

        assertThat(result).isEqualTo(DESTINATION);
    }

    @Test
    void paramMismatchReturnsNull() {
        String result = service.evaluateLogic(
                pattern("({param.x} > 3)"),
                Map.of(), Map.of("x", "1"), null, null, REQUEST_ID);

        assertThat(result).isNull();
    }

    // --- expression: headers ---

    @Test
    void headerMatchReturnsDestination() {
        String result = service.evaluateLogic(
                pattern("({header.X-Tenant} == acme)"),
                Map.of(), Map.of(), Map.of("X-Tenant", "acme"), null, REQUEST_ID);

        assertThat(result).isEqualTo(DESTINATION);
    }

    @Test
    void nullHeadersDoNotThrow() {
        String result = service.evaluateLogic(
                pattern("RANDOM:100"),
                Map.of(), Map.of(), null, null, REQUEST_ID);

        assertThat(result).isEqualTo(DESTINATION);
    }

    // --- expression: payload ---

    @Test
    void payloadFieldMatchReturnsDestination() {
        String result = service.evaluateLogic(
                pattern("({payload.user.role} == admin)"),
                Map.of(), Map.of(), null, "{\"user\":{\"role\":\"admin\"}}", REQUEST_ID);

        assertThat(result).isEqualTo(DESTINATION);
    }

    @Test
    void payloadNotParsedWhenLogicDoesNotReferenceIt() {
        // jsonPayload is invalid JSON but logic doesn't reference payload — should not throw
        String result = service.evaluateLogic(
                pattern("RANDOM:100"),
                Map.of(), Map.of(), null, "not-json", REQUEST_ID);

        assertThat(result).isEqualTo(DESTINATION);
    }

    @Test
    void invalidJsonPayloadSilentlyIgnored() {
        // logic references payload but JSON is unparseable — key absent → exception caught → null
        String result = service.evaluateLogic(
                pattern("({payload.x} == value)"),
                Map.of(), Map.of(), null, "not-json", REQUEST_ID);

        assertThat(result).isNull();
    }

    // --- expression: combined ---

    @Test
    void combinedClaimAndParamBothMatchReturnsDestination() {
        String result = service.evaluateLogic(
                pattern("(({claim.iat} == 123) AND ({param.op} > 3))"),
                Map.of("iat", 123), Map.of("op", "5"), null, null, REQUEST_ID);

        assertThat(result).isEqualTo(DESTINATION);
    }

    @Test
    void combinedClaimAndParamOneFailsReturnsNull() {
        String result = service.evaluateLogic(
                pattern("(({claim.iat} == 123) AND ({param.op} > 3))"),
                Map.of("iat", 123), Map.of("op", "1"), null, null, REQUEST_ID);

        assertThat(result).isNull();
    }

    // --- exception handling ---

    @Test
    void missingContextKeyReturnsNull() {
        String result = service.evaluateLogic(
                pattern("({claim.missing} == value)"),
                Map.of(), Map.of(), null, null, REQUEST_ID);

        assertThat(result).isNull();
    }

    // --- helpers ---

    private Pattern pattern(String logic) {
        return new Pattern(1, HttpMethod.GET, "https://example.com", logic, DESTINATION, ResolutionType.REDIRECT, null, null);
    }
}
