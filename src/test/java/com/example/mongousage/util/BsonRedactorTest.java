package com.example.mongousage.util;

import org.bson.Document;
import org.bson.BsonArray;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BsonRedactor Tests")
class BsonRedactorTest {

    private final BsonRedactor redactor = new BsonRedactor();

    @Test
    @DisplayName("Should redact sensitive top-level fields")
    void redact_sensitiveFields_redactsValues() {
        Document input = new Document()
                .append("username", "admin")
                .append("password", "secret123")
                .append("token", "abc123xyz")
                .append("normalField", "keep-this");

        Document result = redactor.redact(input);

        assertThat(result.getString("username")).isEqualTo("admin");
        assertThat(result.getString("password")).isEqualTo("***REDACTED***");
        assertThat(result.getString("token")).isEqualTo("***REDACTED***");
        assertThat(result.getString("normalField")).isEqualTo("keep-this");
    }

    @Test
    @DisplayName("Should redact sensitive fields with case insensitivity")
    void redact_caseInsensitive_redactsValues() {
        Document input = new Document()
                .append("PASSWORD", "secret")
                .append("SecretKey", "value")
                .append("api_key", "key123");

        Document result = redactor.redact(input);

        assertThat(result.getString("PASSWORD")).isEqualTo("***REDACTED***");
        assertThat(result.getString("SecretKey")).isEqualTo("***REDACTED***");
        assertThat(result.getString("api_key")).isEqualTo("***REDACTED***");
    }

    @Test
    @DisplayName("Should handle null input")
    void redact_nullInput_returnsEmptyDocument() {
        Document result = redactor.redact(null);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should handle empty document")
    void redact_emptyDocument_returnsEmptyDocument() {
        Document result = redactor.redact(new Document());
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should recursively redact nested documents")
    void redact_nestedDocument_redactsNestedSensitiveFields() {
        Document input = new Document()
                .append("user", "john")
                .append("credentials", new Document()
                        .append("username", "admin")
                        .append("secret", "top-secret"));

        Document result = redactor.redact(input);

        assertThat(result.getString("user")).isEqualTo("john");
        Document credentials = result.get("credentials", Document.class);
        assertThat(credentials.getString("username")).isEqualTo("admin");
        assertThat(credentials.getString("secret")).isEqualTo("***REDACTED***");
    }

    @Test
    @DisplayName("Should redact sensitive fields in arrays")
    void redact_arrayWithSensitiveFields_redactsValues() {
        Document input = new Document()
                .append("items", List.of(
                        new Document("name", "item1").append("token", "token1"),
                        new Document("name", "item2").append("token", "token2")
                ));

        Document result = redactor.redact(input);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = result.get("items", List.class);
        assertThat(items).hasSize(2);
        assertThat(items.get(0).get("token")).isEqualTo("***REDACTED***");
        assertThat(items.get(1).get("token")).isEqualTo("***REDACTED***");
    }

    @Test
    @DisplayName("Should handle deeply nested structures")
    void redact_deeplyNested_redactsAtAllLevels() {
        Document input = new Document()
                .append("level1", new Document()
                        .append("password", "level1-secret")
                        .append("level2", new Document()
                                .append("apiKey", "level2-key")
                                .append("level3", new Document()
                                        .append("authorization", "level3-auth"))));

        Document result = redactor.redact(input);

        Document level1 = result.get("level1", Document.class);
        assertThat(level1.getString("password")).isEqualTo("***REDACTED***");

        Document level2 = level1.get("level2", Document.class);
        assertThat(level2.getString("apiKey")).isEqualTo("***REDACTED***");

        Document level3 = level2.get("level3", Document.class);
        assertThat(level3.getString("authorization")).isEqualTo("***REDACTED***");
    }

    @Test
    @DisplayName("Should not modify original document")
    void redact_originalDocumentUnchanged() {
        Document original = new Document()
                .append("password", "secret")
                .append("data", "value");

        redactor.redact(original);

        assertThat(original.getString("password")).isEqualTo("secret");
        assertThat(original.getString("data")).isEqualTo("value");
    }

    @Test
    @DisplayName("Should handle arrays containing non-document values")
    void redact_arraysWithPrimitives_preservesPrimitives() {
        Document input = new Document()
                .append("numbers", List.of(1, 2, 3))
                .append("strings", List.of("a", "b", "c"))
                .append("mixed", List.of("text", 123, true));

        Document result = redactor.redact(input);

        assertThat(result.get("numbers", List.class)).isEqualTo(List.of(1, 2, 3));
        assertThat(result.get("strings", List.class)).isEqualTo(List.of("a", "b", "c"));
        assertThat(result.get("mixed", List.class)).isEqualTo(List.of("text", 123, true));
    }

    @Test
    @DisplayName("Should handle all sensitive key parts")
    void redact_allSensitiveKeys_redactsAll() {
        Document input = new Document()
                .append("password", "pw")
                .append("passwd", "pw")
                .append("pwd", "pw")
                .append("secret", "sec")
                .append("token", "tok")
                .append("apikey", "key")
                .append("api_key", "key")
                .append("authorization", "auth")
                .append("cookie", "cook");

        Document result = redactor.redact(input);

        for (String key : input.keySet()) {
            assertThat(result.getString(key)).as(key + " should be redacted")
                    .isEqualTo("***REDACTED***");
        }
    }
}
