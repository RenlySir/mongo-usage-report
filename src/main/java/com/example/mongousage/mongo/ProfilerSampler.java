package com.example.mongousage.mongo;

import com.example.mongousage.config.CollectorOptions;
import com.example.mongousage.model.CommandError;
import com.example.mongousage.model.UsageReport;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProfilerSampler {
    private final MongoClient client;
    private final CollectorOptions options;
    private final UsageReport report;

    public ProfilerSampler(MongoClient client, CollectorOptions options, UsageReport report) {
        this.client = client;
        this.options = options;
        this.report = report;
    }

    public void sample(List<String> databaseNames) {
        Map<String, Document> originalLevels = new HashMap<>();
        for (String databaseName : databaseNames) {
            if (!shouldSampleDatabase(databaseName)) {
                continue;
            }
            MongoDatabase database = client.getDatabase(databaseName);
            try {
                Document original = database.runCommand(new Document("profile", -1));
                originalLevels.put(databaseName, original);
                database.runCommand(new Document("profile", 1).append("slowms", options.getSlowMs()));
            } catch (Exception e) {
                report.getCommandErrors().add(new CommandError(databaseName, "profile-enable", e.getMessage()));
            }
        }

        try {
            Thread.sleep(Math.max(0, options.getProfileSeconds()) * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            report.getCommandErrors().add(new CommandError("cluster", "profile-sleep", e.getMessage()));
        } finally {
            restore(originalLevels);
        }
    }

    private boolean shouldSampleDatabase(String databaseName) {
        if (!options.getIncludeDatabases().isEmpty() && !options.getIncludeDatabases().contains(databaseName)) {
            return false;
        }
        return !options.getExcludeDatabases().contains(databaseName);
    }

    private void restore(Map<String, Document> originalLevels) {
        for (Map.Entry<String, Document> entry : originalLevels.entrySet()) {
            String databaseName = entry.getKey();
            MongoDatabase database = client.getDatabase(databaseName);
            Document original = entry.getValue();
            int was = original.get("was", 0);
            Document command = new Document("profile", was);
            if (original.containsKey("slowms")) {
                command.append("slowms", original.get("slowms"));
            }
            try {
                database.runCommand(command);
            } catch (Exception e) {
                report.getCommandErrors().add(new CommandError(databaseName, "profile-restore", e.getMessage()));
            }
        }
    }
}
