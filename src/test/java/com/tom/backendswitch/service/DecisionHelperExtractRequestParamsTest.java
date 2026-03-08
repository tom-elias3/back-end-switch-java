package com.tom.backendswitch.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionHelperExtractRequestParamsTest {

    @Test
    void nullUrlReturnsEmptyMap() {
        assertThat(DecisionHelper.extractRequestParams(null)).isEmpty();
    }

    @Test
    void blankUrlReturnsEmptyMap() {
        assertThat(DecisionHelper.extractRequestParams("   ")).isEmpty();
    }

    @Test
    void urlWithNoQueryStringReturnsEmptyMap() {
        assertThat(DecisionHelper.extractRequestParams("https://example.com/path")).isEmpty();
    }

    @Test
    void singleParamExtracted() {
        Map<String, String> params = DecisionHelper.extractRequestParams("https://example.com/path?operation=5");

        assertThat(params).containsOnlyKeys("operation").containsEntry("operation", "5");
    }

    @Test
    void multipleParamsExtracted() {
        Map<String, String> params = DecisionHelper.extractRequestParams("https://example.com/path?a=1&b=2&c=3");

        assertThat(params).hasSize(3)
                .containsEntry("a", "1")
                .containsEntry("b", "2")
                .containsEntry("c", "3");
    }

    @Test
    void valueContainingEqualsSignPreserved() {
        Map<String, String> params = DecisionHelper.extractRequestParams("https://example.com/?token=abc=def");

        assertThat(params).containsEntry("token", "abc=def");
    }

    @Test
    void urlWithTrailingQuestionMarkReturnsEmptyMap() {
        assertThat(DecisionHelper.extractRequestParams("https://example.com/path?")).isEmpty();
    }
}
