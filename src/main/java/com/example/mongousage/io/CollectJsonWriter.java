package com.example.mongousage.io;

import com.example.mongousage.model.UsageReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persists {@link UsageReport} as JSON files (no analysis or spreadsheet output).
 */
public class CollectJsonWriter {
    private final ObjectMapper objectMapper;

    public CollectJsonWriter() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void write(UsageReport report, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        writeJson(outputDirectory.resolve("raw.json"), report);
        writeJson(outputDirectory.resolve("inventory.json"), inventory(report));
        writeJson(outputDirectory.resolve("workload.json"), report.getProfileSamples());
    }

    private void writeJson(Path path, Object value) throws IOException {
        objectMapper.writeValue(path.toFile(), value);
    }

    private Map<String, Object> inventory(UsageReport report) {
        long collectionCount = report.getDatabases().stream().mapToLong(db -> db.getCollections().size()).sum();
        long indexCount = report.getDatabases().stream()
                .flatMap(db -> db.getCollections().stream())
                .mapToLong(collection -> collection.getIndexes().size())
                .sum();
        Map<String, Object> inventory = new LinkedHashMap<>();
        inventory.put("generatedAt", report.getGeneratedAt());
        inventory.put("target", report.getTarget());
        inventory.put("databaseCount", report.getDatabases().size());
        inventory.put("collectionCount", collectionCount);
        inventory.put("indexCount", indexCount);
        inventory.put("deploymentInfo", report.getDeploymentInfo());
        inventory.put("buildInfo", report.getBuildInfo());
        inventory.put("hello", report.getHello());
        inventory.put("serverStatus", report.getServerStatus());
        inventory.put("runtimeMetrics", report.getRuntimeMetrics());
        inventory.put("queryShapes", report.getQueryShapes());
        inventory.put("databases", report.getDatabases());
        inventory.put("commandErrors", report.getCommandErrors());
        return inventory;
    }
}
