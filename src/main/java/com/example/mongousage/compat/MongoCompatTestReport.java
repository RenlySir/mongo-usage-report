package com.example.mongousage.compat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class MongoCompatTestReport {
    private final Instant generatedAt = Instant.now();
    private final String databaseName;
    private final boolean dropDatabaseAfterRun;
    private final String mongoVersion;
    private final List<MongoCompatTestResult> results = new ArrayList<>();

    public MongoCompatTestReport(String databaseName, boolean dropDatabaseAfterRun) {
        this(databaseName, dropDatabaseAfterRun, "");
    }

    public MongoCompatTestReport(String databaseName, boolean dropDatabaseAfterRun, String mongoVersion) {
        this.databaseName = databaseName;
        this.dropDatabaseAfterRun = dropDatabaseAfterRun;
        this.mongoVersion = mongoVersion == null ? "" : mongoVersion;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public boolean isDropDatabaseAfterRun() {
        return dropDatabaseAfterRun;
    }

    public String getMongoVersion() {
        return mongoVersion;
    }

    public List<MongoCompatTestResult> getResults() {
        return results;
    }

    public void add(MongoCompatTestResult result) {
        results.add(result);
    }

    public long total() {
        return results.size();
    }

    public long getTotal() {
        return total();
    }

    public long passed() {
        return count("PASS");
    }

    public long getPassed() {
        return passed();
    }

    public long failed() {
        return count("FAIL");
    }

    public long getFailed() {
        return failed();
    }

    public long skipped() {
        return count("SKIP");
    }

    public long getSkipped() {
        return skipped();
    }

    public boolean isSuccess() {
        return failed() == 0;
    }

    private long count(String status) {
        return results.stream().filter(result -> status.equals(result.status())).count();
    }
}
