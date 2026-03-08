package com.tom.backendswitch.service;

import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionHelperExtractClaimsTest {

    @Test
    void nullTokenReturnsEmptyMap() {
        assertThat(DecisionHelper.extractClaims(null)).isEmpty();
    }

    @Test
    void blankTokenReturnsEmptyMap() {
        assertThat(DecisionHelper.extractClaims("   ")).isEmpty();
    }

    @Test
    void tokenWithoutBearerPrefixReturnsEmptyMap() {
        assertThat(DecisionHelper.extractClaims(jwt("{\"sub\":\"user1\"}"))).isEmpty();
    }

    @Test
    void validTokenReturnsClaims() {
        Map<String, Object> claims = DecisionHelper.extractClaims("Bearer " + jwt("{\"sub\":\"user1\",\"iat\":12345}"));

        assertThat(claims).containsEntry("sub", "user1").containsEntry("iat", 12345);
    }

    @Test
    void claimsMapContainsAllFields() {
        String payload = "{\"sub\":\"u\",\"role\":\"admin\",\"iat\":1}";
        Map<String, Object> claims = DecisionHelper.extractClaims("Bearer " + jwt(payload));

        assertThat(claims).hasSize(3)
                .containsEntry("sub", "u")
                .containsEntry("role", "admin")
                .containsEntry("iat", 1);
    }

    @Test
    void invalidJsonInPayloadReturnsEmptyMap() {
        String token = "Bearer header." + b64("not-valid-json") + ".sig";
        assertThat(DecisionHelper.extractClaims(token)).isEmpty();
    }

    // --- helpers ---

    /** Wraps a JSON string as the payload of a dummy JWT (header.payload.sig). */
    private String jwt(String payloadJson) {
        return "header." + b64(payloadJson) + ".sig";
    }

    private String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes());
    }
}
