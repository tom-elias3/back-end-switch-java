package com.tom.backendswitch.service;

import com.tom.backendswitch.model.Pattern;
import com.tom.backendswitch.model.ResolutionType;
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
class DecisionServiceInitTest {

    @Mock Environment environment;
    @TempDir Path tempDir;

    @Test
    void loadsFromClasspathWhenNoExternalPathSet() throws Exception {
        when(environment.getProperty("routing.properties.path")).thenReturn(null);
        DecisionService service = new DecisionService(environment);

        service.init();

        assertThat(service.getPatterns()).hasSize(2);
    }

    @Test
    void loadsFromExternalFile() throws Exception {
        Path file = writeProperties(tempDir, """
                pattern.1.method=GET
                pattern.1.url=https://example.com/api
                pattern.1.logic=RANDOM:100
                pattern.1.destination=https://dest.example.com
                """);
        when(environment.getProperty("routing.properties.path")).thenReturn(file.toString());
        DecisionService service = new DecisionService(environment);

        service.init();

        assertThat(service.getPatterns()).hasSize(1);
    }

    @Test
    void externalFileNotFoundThrows() {
        when(environment.getProperty("routing.properties.path")).thenReturn("/nonexistent/routing.properties");
        DecisionService service = new DecisionService(environment);

        assertThatThrownBy(service::init).isInstanceOf(IOException.class);
    }

    @Test
    void patternMissingRequiredFieldIsSkipped() throws Exception {
        Path file = writeProperties(tempDir, """
                pattern.1.method=GET
                pattern.1.url=https://example.com/api
                pattern.1.logic=RANDOM:100
                pattern.1.destination=https://dest.example.com
                pattern.2.method=GET
                pattern.2.url=https://example.com/other
                pattern.2.logic=RANDOM:100
                """);
        // pattern.2 missing destination — should be skipped
        when(environment.getProperty("routing.properties.path")).thenReturn(file.toString());
        DecisionService service = new DecisionService(environment);

        service.init();

        assertThat(service.getPatterns()).hasSize(1).containsKey(1);
    }

    @Test
    void resolutionDefaultsToRedirectWhenAbsent() throws Exception {
        Pattern pattern = loadSinglePattern(tempDir, environment, "");

        assertThat(pattern.getResolution()).isEqualTo(ResolutionType.REDIRECT);
    }

    @Test
    void resolutionFollowParsedCaseInsensitive() throws Exception {
        Pattern pattern = loadSinglePattern(tempDir, environment, "pattern.1.resolution=follow");

        assertThat(pattern.getResolution()).isEqualTo(ResolutionType.FOLLOW);
    }

    @Test
    void unrecognisedResolutionDefaultsToRedirect() throws Exception {
        Pattern pattern = loadSinglePattern(tempDir, environment, "pattern.1.resolution=bogus");

        assertThat(pattern.getResolution()).isEqualTo(ResolutionType.REDIRECT);
    }

    @Test
    void timeoutParsedOnFollowPattern() throws Exception {
        Pattern pattern = loadSinglePattern(tempDir, environment,
                "pattern.1.resolution=FOLLOW\npattern.1.timeout=3000");

        assertThat(pattern.getTimeout()).isEqualTo(3000);
    }

    @Test
    void followWithTimeoutBuildsRestClient() throws Exception {
        Pattern pattern = loadSinglePattern(tempDir, environment,
                "pattern.1.resolution=FOLLOW\npattern.1.timeout=3000");

        assertThat(pattern.getRestClient()).isNotNull();
    }

    @Test
    void followWithoutTimeoutHasNullRestClient() throws Exception {
        Pattern pattern = loadSinglePattern(tempDir, environment, "pattern.1.resolution=FOLLOW");

        assertThat(pattern.getRestClient()).isNull();
    }

    @Test
    void redirectWithTimeoutHasNullRestClient() throws Exception {
        Pattern pattern = loadSinglePattern(tempDir, environment,
                "pattern.1.resolution=REDIRECT\npattern.1.timeout=3000");

        assertThat(pattern.getRestClient()).isNull();
    }

    @Test
    void patternFieldsMappedCorrectly() throws Exception {
        Pattern pattern = loadSinglePattern(tempDir, environment, "");

        assertThat(pattern.getId()).isEqualTo(1);
        assertThat(pattern.getMethod()).isEqualTo(HttpMethod.GET);
        assertThat(pattern.getUrl()).isEqualTo("https://example.com/api");
        assertThat(pattern.getLogic()).isEqualTo("RANDOM:100");
        assertThat(pattern.getDestination()).isEqualTo("https://dest.example.com");
    }

    @Test
    void reloadClearsPreviousPatterns() throws Exception {
        Path file = writeProperties(tempDir, """
                pattern.1.method=GET
                pattern.1.url=https://example.com/api
                pattern.1.logic=RANDOM:100
                pattern.1.destination=https://dest.example.com
                """);
        when(environment.getProperty("routing.properties.path")).thenReturn(file.toString());
        DecisionService service = new DecisionService(environment);
        service.init();
        assertThat(service.getPatterns()).hasSize(1);

        // Overwrite with empty file and reload
        Files.writeString(file, "");
        service.init();

        assertThat(service.getPatterns()).isEmpty();
    }

    @Test
    void patternsOrderedById() throws Exception {
        Path file = writeProperties(tempDir, """
                pattern.3.method=GET
                pattern.3.url=https://example.com/c
                pattern.3.logic=RANDOM:100
                pattern.3.destination=https://dest.example.com
                pattern.1.method=GET
                pattern.1.url=https://example.com/a
                pattern.1.logic=RANDOM:100
                pattern.1.destination=https://dest.example.com
                pattern.2.method=GET
                pattern.2.url=https://example.com/b
                pattern.2.logic=RANDOM:100
                pattern.2.destination=https://dest.example.com
                """);
        when(environment.getProperty("routing.properties.path")).thenReturn(file.toString());
        DecisionService service = new DecisionService(environment);

        service.init();

        assertThat(service.getPatterns().keySet()).containsExactly(1, 2, 3);
    }

    // --- helpers ---

    private Path writeProperties(Path dir, String content) throws IOException {
        Path file = dir.resolve("routing.properties");
        Files.writeString(file, content);
        return file;
    }

    private Pattern loadSinglePattern(Path dir, Environment env, String extraLines) throws Exception {
        Path file = writeProperties(dir, """
                pattern.1.method=GET
                pattern.1.url=https://example.com/api
                pattern.1.logic=RANDOM:100
                pattern.1.destination=https://dest.example.com
                """ + extraLines);
        when(env.getProperty("routing.properties.path")).thenReturn(file.toString());
        DecisionService service = new DecisionService(env);
        service.init();
        Map<Integer, Pattern> patterns = service.getPatterns();
        assertThat(patterns).hasSize(1);
        return patterns.get(1);
    }
}
