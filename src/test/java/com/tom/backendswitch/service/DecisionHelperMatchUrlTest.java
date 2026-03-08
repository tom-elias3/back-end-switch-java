package com.tom.backendswitch.service;

import com.tom.backendswitch.model.Pattern;
import com.tom.backendswitch.model.ResolutionType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionHelperMatchUrlTest {

    @Test
    void exactUrlAndMethodMatches() {
        assertThat(match(HttpMethod.GET, "https://example.com/path", HttpMethod.GET, "https://example.com/path")).isTrue();
    }

    @Test
    void wrongMethodDoesNotMatch() {
        assertThat(match(HttpMethod.POST, "https://example.com/path", HttpMethod.GET, "https://example.com/path")).isFalse();
    }

    @Test
    void exactUrlDifferentPathDoesNotMatch() {
        assertThat(match(HttpMethod.GET, "https://example.com/other", HttpMethod.GET, "https://example.com/path")).isFalse();
    }

    @Test
    void wildcardMatchesAnyHost() {
        assertThat(match(HttpMethod.GET, "https://foo.example.com/api", HttpMethod.GET, "https://*.example.com/api")).isTrue();
    }

    @Test
    void wildcardMatchesAnyPathSegment() {
        assertThat(match(HttpMethod.GET, "https://example.com/api/resource", HttpMethod.GET, "https://example.com/api/*")).isTrue();
    }

    @Test
    void wildcardMatchesQueryParam() {
        assertThat(match(HttpMethod.GET, "https://example.com/path?operation=5", HttpMethod.GET, "https://example.com/path?operation=*")).isTrue();
    }

    @Test
    void multipleWildcardsAllMatch() {
        assertThat(match(HttpMethod.GET, "https://foo.example.com/api/resource", HttpMethod.GET, "https://*.example.com/api/*")).isTrue();
    }

    @Test
    void wildcardDoesNotMatchIfLiteralPartsDiffer() {
        assertThat(match(HttpMethod.GET, "https://foo.example.com/other", HttpMethod.GET, "https://*.example.com/api/*")).isFalse();
    }

    @Test
    void wildcardAtEndMatchesRemainder() {
        assertThat(match(HttpMethod.GET, "https://example.com/api/a/b/c", HttpMethod.GET, "https://example.com/api/*")).isTrue();
    }

    @Test
    void noWildcardPatternDoesNotMatchUrlWithExtraPath() {
        assertThat(match(HttpMethod.GET, "https://example.com/api/extra", HttpMethod.GET, "https://example.com/api")).isFalse();
    }

    // --- helper ---

    private boolean match(HttpMethod requestMethod, String requestUrl, HttpMethod patternMethod, String patternUrl) {
        Pattern pattern = new Pattern(1, patternMethod, patternUrl, "true", "https://dest", ResolutionType.REDIRECT, null, null);
        return DecisionHelper.matchUrl(requestMethod, requestUrl, pattern);
    }
}
