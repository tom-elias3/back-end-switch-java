package com.tom.backendswitch.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionHelperFlattenPayloadTest {

    @Test
    void flatTopLevelField() {
        Map<String, String> context = new HashMap<>();
        DecisionHelper.flattenPayload(Map.of("role", "admin"), "payload.", context);

        assertThat(context).containsEntry("payload.role", "admin");
    }

    @Test
    void multipleTopLevelFields() {
        Map<String, String> context = new HashMap<>();
        DecisionHelper.flattenPayload(Map.of("a", "1", "b", "2"), "payload.", context);

        assertThat(context).containsEntry("payload.a", "1").containsEntry("payload.b", "2");
    }

    @Test
    void nestedObjectFlattened() {
        Map<String, String> context = new HashMap<>();
        DecisionHelper.flattenPayload(Map.of("user", Map.of("role", "admin")), "payload.", context);

        assertThat(context).containsEntry("payload.user.role", "admin");
    }

    @Test
    void deeplyNestedObjectFlattened() {
        Map<String, String> context = new HashMap<>();
        DecisionHelper.flattenPayload(Map.of("a", Map.of("b", Map.of("c", "deep"))), "payload.", context);

        assertThat(context).containsEntry("payload.a.b.c", "deep");
    }

    @Test
    void nullValueStoredAsNull() {
        Map<String, String> context = new HashMap<>();
        Map<String, Object> map = new HashMap<>();
        map.put("key", null);
        DecisionHelper.flattenPayload(map, "payload.", context);

        assertThat(context).containsKey("payload.key");
        assertThat(context.get("payload.key")).isNull();
    }

    @Test
    void integerValueConvertedToString() {
        Map<String, String> context = new HashMap<>();
        DecisionHelper.flattenPayload(Map.of("count", 42), "payload.", context);

        assertThat(context).containsEntry("payload.count", "42");
    }

    @Test
    void emptyMapProducesNoEntries() {
        Map<String, String> context = new HashMap<>();
        DecisionHelper.flattenPayload(Map.of(), "payload.", context);

        assertThat(context).isEmpty();
    }

    @Test
    void existingContextEntriesPreserved() {
        Map<String, String> context = new HashMap<>();
        context.put("claim.sub", "user1");
        DecisionHelper.flattenPayload(Map.of("role", "admin"), "payload.", context);

        assertThat(context).containsEntry("claim.sub", "user1").containsEntry("payload.role", "admin");
    }
}
