package com.tom.backendswitch.service;

import com.tom.backendswitch.model.OriginalRequest;
import com.tom.backendswitch.model.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DecisionServicePatternsTest {

    @Mock Environment environment;
    @TempDir Path tempDir;

    // --- getPatterns ---

    @Test
    void getPatternsReturnsEmptyMapWhenNoneLoaded() throws Exception {
        DecisionService service = serviceWith("");

        assertThat(service.getPatterns()).isEmpty();
    }

    @Test
    void getPatternsReturnsLoadedPatterns() throws Exception {
        DecisionService service = serviceWith(twoPatterns());

        assertThat(service.getPatterns()).hasSize(2).containsKeys(1, 2);
    }

    @Test
    void getPatternsIsOrderedById() throws Exception {
        DecisionService service = serviceWith(twoPatterns());

        assertThat(service.getPatterns().keySet()).containsExactly(1, 2);
    }

    @Test
    void getPatternsIsUnmodifiable() throws Exception {
        DecisionService service = serviceWith(twoPatterns());
        Map<Integer, Pattern> patterns = service.getPatterns();

        assertThatThrownBy(() -> patterns.put(99, null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // --- matchPattern ---

    @Test
    void matchPatternReturnsNullWhenNoPatternsLoaded() throws Exception {
        DecisionService service = serviceWith("");
        OriginalRequest request = new OriginalRequest(HttpMethod.GET, "https://example.com/api", null, null);

        assertThat(service.matchPattern(request)).isNull();
    }

    @Test
    void matchPatternReturnsNullWhenNothingMatches() throws Exception {
        DecisionService service = serviceWith("""
                pattern.1.method=POST
                pattern.1.url=https://other.example.com/api
                pattern.1.logic=RANDOM:100
                pattern.1.destination=https://dest.example.com
                """);

        OriginalRequest request = new OriginalRequest(HttpMethod.GET, "https://example.com/api", null, null);
        assertThat(service.matchPattern(request)).isNull();
    }

    @Test
    void matchPatternReturnsMatchedPattern() throws Exception {
        DecisionService service = serviceWith("""
                pattern.1.method=GET
                pattern.1.url=https://example.com/api
                pattern.1.logic=RANDOM:100
                pattern.1.destination=https://dest.example.com
                """);

        OriginalRequest request = new OriginalRequest(HttpMethod.GET, "https://example.com/api", null, null);
        Pattern matched = service.matchPattern(request);

        assertThat(matched).isNotNull();
        assertThat(matched.getId()).isEqualTo(1);
    }

    @Test
    void matchPatternReturnsLowestIdOnMultipleMatches() throws Exception {
        DecisionService service = serviceWith("""
                pattern.1.method=GET
                pattern.1.url=https://example.com/*
                pattern.1.logic=RANDOM:100
                pattern.1.destination=https://dest-a.example.com
                pattern.2.method=GET
                pattern.2.url=https://example.com/api
                pattern.2.logic=RANDOM:100
                pattern.2.destination=https://dest-b.example.com
                """);

        OriginalRequest request = new OriginalRequest(HttpMethod.GET, "https://example.com/api", null, null);
        Pattern matched = service.matchPattern(request);

        assertThat(matched.getId()).isEqualTo(1);
    }

    @Test
    void matchPatternRespectsHttpMethod() throws Exception {
        DecisionService service = serviceWith("""
                pattern.1.method=POST
                pattern.1.url=https://example.com/api
                pattern.1.logic=RANDOM:100
                pattern.1.destination=https://dest.example.com
                """);

        OriginalRequest request = new OriginalRequest(HttpMethod.GET, "https://example.com/api", null, null);
        assertThat(service.matchPattern(request)).isNull();
    }

    // --- helpers ---

    private DecisionService serviceWith(String content) throws Exception {
        Path file = tempDir.resolve("routing.properties");
        Files.writeString(file, content);
        when(environment.getProperty("routing.properties.path")).thenReturn(file.toString());
        DecisionService service = new DecisionService(environment);
        service.init();
        return service;
    }

    private String twoPatterns() throws IOException {
        return """
                pattern.1.method=GET
                pattern.1.url=https://example.com/api
                pattern.1.logic=RANDOM:100
                pattern.1.destination=https://dest-a.example.com
                pattern.2.method=POST
                pattern.2.url=https://example.com/api
                pattern.2.logic=RANDOM:100
                pattern.2.destination=https://dest-b.example.com
                """;
    }
}
