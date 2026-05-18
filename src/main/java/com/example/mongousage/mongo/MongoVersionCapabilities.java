package com.example.mongousage.mongo;

public class MongoVersionCapabilities {
    private final MongoVersion version;

    private MongoVersionCapabilities(MongoVersion version) {
        this.version = version;
    }

    public static MongoVersionCapabilities forVersion(String version) {
        return new MongoVersionCapabilities(MongoVersion.parse(version));
    }

    public MongoVersion version() {
        return version;
    }

    public String helloCommand() {
        return version.major() >= 5 ? "hello" : "isMaster";
    }

    public boolean supportsDefaultReadWriteConcern() {
        return version.atLeast(4, 4);
    }

    public boolean useCurrentOpAggregation() {
        return version.atLeast(6, 2);
    }

    public boolean supportsQueryStats() {
        return version.atLeast(6, 0, 7);
    }
}
