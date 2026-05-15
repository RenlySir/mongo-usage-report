package com.example.mongousage.util;

import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BsonRedactor {
    private static final List<String> SENSITIVE_KEY_PARTS = List.of(
            "password", "passwd", "pwd", "secret", "token", "apikey", "api_key", "authorization", "cookie"
    );

    public Document redact(Document input) {
        if (input == null) {
            return new Document();
        }
        Document output = new Document();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            if (isSensitive(entry.getKey())) {
                output.put(entry.getKey(), "***REDACTED***");
            } else {
                output.put(entry.getKey(), redactValue(entry.getValue()));
            }
        }
        return output;
    }

    @SuppressWarnings("unchecked")
    private Object redactValue(Object value) {
        if (value instanceof Document document) {
            return redact(document);
        }
        if (value instanceof List<?> list) {
            List<Object> redacted = new ArrayList<>();
            for (Object item : list) {
                redacted.add(redactValue(item));
            }
            return redacted;
        }
        return value;
    }

    private boolean isSensitive(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return SENSITIVE_KEY_PARTS.stream().anyMatch(normalized::contains);
    }
}
