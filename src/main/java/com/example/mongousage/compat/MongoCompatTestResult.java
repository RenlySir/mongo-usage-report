package com.example.mongousage.compat;

public record MongoCompatTestResult(
        String id,
        String category,
        String name,
        String status,
        String message,
        long elapsedMillis
) {
    public static MongoCompatTestResult passed(String id, String category, String name, long elapsedMillis) {
        return new MongoCompatTestResult(id, category, name, "PASS", "", elapsedMillis);
    }

    public static MongoCompatTestResult failed(String id, String category, String name, String message, long elapsedMillis) {
        return new MongoCompatTestResult(id, category, name, "FAIL", message == null ? "" : message, elapsedMillis);
    }

    public static MongoCompatTestResult skipped(String id, String category, String name, String message, long elapsedMillis) {
        return new MongoCompatTestResult(id, category, name, "SKIP", message == null ? "" : message, elapsedMillis);
    }
}
