package com.example.mongousage.util;

import com.mongodb.ConnectionString;
import com.mongodb.MongoConfigurationException;

import java.util.regex.Pattern;

public final class ValidationUtils {
    private static final Pattern MONGO_VERSION_PATTERN = Pattern.compile(
            "^(?:\\d+|\\d+\\.\\d+|\\d+\\.\\d+\\.\\d+)$"
    );

    private ValidationUtils() {
    }

    public static void validateUri(String uri) {
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("MongoDB URI cannot be null or empty");
        }
        try {
            new ConnectionString(uri);
        } catch (IllegalArgumentException | MongoConfigurationException e) {
            throw new IllegalArgumentException("Invalid MongoDB URI: " + e.getMessage(), e);
        }
    }

    public static void validateMongoVersion(String version) {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("MongoDB version cannot be null or empty");
        }
        if (!MONGO_VERSION_PATTERN.matcher(version.trim()).matches()) {
            throw new IllegalArgumentException(
                    "Invalid MongoDB version format: '" + version + "'. " +
                            "Expected format: 4, 4.4, 5, 6, 6.0.7, 7, or major.minor.patch"
            );
        }
    }

    public static void validateSampleLimit(int sampleLimit) {
        if (sampleLimit < 0) {
            throw new IllegalArgumentException("Sample limit must be non-negative: " + sampleLimit);
        }
        if (sampleLimit > 100000) {
            throw new IllegalArgumentException("Sample limit too large (max 100000): " + sampleLimit);
        }
    }

    public static void validateProfileSeconds(int profileSeconds) {
        if (profileSeconds < 1) {
            throw new IllegalArgumentException("Profile seconds must be at least 1: " + profileSeconds);
        }
        if (profileSeconds > 3600) {
            throw new IllegalArgumentException("Profile seconds too large (max 3600): " + profileSeconds);
        }
    }

    public static void validateSlowMs(int slowMs) {
        if (slowMs < 0) {
            throw new IllegalArgumentException("Slow ms must be non-negative: " + slowMs);
        }
    }
}
