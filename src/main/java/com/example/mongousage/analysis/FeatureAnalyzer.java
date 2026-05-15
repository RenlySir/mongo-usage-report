package com.example.mongousage.analysis;

import com.example.mongousage.model.CollectionInfo;
import com.example.mongousage.model.DatabaseInfo;
import com.example.mongousage.model.FeatureFinding;
import com.example.mongousage.model.IndexInfo;
import com.example.mongousage.model.ProfileSample;
import com.example.mongousage.model.UsageReport;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FeatureAnalyzer {
    private static final Set<String> HIGH_RISK_STAGES = Set.of(
            "$changeStream", "$graphLookup", "$setWindowFields", "$search", "$searchMeta",
            "$vectorSearch", "$merge", "$out", "$geoNear", "$densify", "$fill"
    );

    public List<FeatureFinding> analyze(UsageReport report) {
        List<FeatureFinding> findings = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (DatabaseInfo database : report.getDatabases()) {
            for (CollectionInfo collection : database.getCollections()) {
                analyzeCollection(collection, findings, seen);
            }
        }
        for (ProfileSample sample : report.getProfileSamples()) {
            analyzeProfileSample(sample, findings, seen);
        }
        return findings;
    }

    private void analyzeCollection(CollectionInfo collection, List<FeatureFinding> findings, Set<String> seen) {
        Document options = collection.getOptions();
        String namespace = collection.getNamespace();
        if (options.containsKey("validator")) {
            add(findings, seen, "Schema", "JSON Schema validator", namespace, "collection validator configured", "HIGH");
        }
        if (options.containsKey("timeseries")) {
            add(findings, seen, "Collection", "Time series collection", namespace, "timeseries option configured", "HIGH");
        }
        if (Boolean.TRUE.equals(options.getBoolean("capped"))) {
            add(findings, seen, "Collection", "Capped collection", namespace, "capped option configured", "MEDIUM");
        }
        if ("view".equals(collection.getType())) {
            add(findings, seen, "Collection", "View", namespace, "collection type is view", "MEDIUM");
        }

        for (IndexInfo index : collection.getIndexes()) {
            Document key = index.getKey();
            Document raw = index.getRaw();
            for (Map.Entry<String, Object> entry : key.entrySet()) {
                Object value = entry.getValue();
                String keyName = entry.getKey();
                if ("text".equals(value)) {
                    add(findings, seen, "Index", "Text index", namespace, index.getName(), "HIGH");
                }
                if ("2dsphere".equals(value) || "2d".equals(value)) {
                    add(findings, seen, "Index", "2dsphere index", namespace, index.getName(), "HIGH");
                }
                if ("hashed".equals(value)) {
                    add(findings, seen, "Index", "Hashed index", namespace, index.getName(), "MEDIUM");
                }
                if ("$**".equals(keyName)) {
                    add(findings, seen, "Index", "Wildcard index", namespace, index.getName(), "HIGH");
                }
            }
            if (raw.containsKey("expireAfterSeconds")) {
                add(findings, seen, "Index", "TTL index", namespace, index.getName(), "MEDIUM");
            }
            if (Boolean.TRUE.equals(raw.getBoolean("unique"))) {
                add(findings, seen, "Index", "Unique index", namespace, index.getName(), "MEDIUM");
            }
            if (raw.containsKey("partialFilterExpression")) {
                add(findings, seen, "Index", "Partial index", namespace, index.getName(), "MEDIUM");
            }
            if (raw.containsKey("collation")) {
                add(findings, seen, "Index", "Collation index", namespace, index.getName(), "MEDIUM");
            }
        }
    }

    private void analyzeProfileSample(ProfileSample sample, List<FeatureFinding> findings, Set<String> seen) {
        Document command = sample.getCommand();
        String namespace = sample.getNamespace();
        if (command.containsKey("commitTransaction") || command.containsKey("abortTransaction") || command.containsKey("startTransaction")) {
            add(findings, seen, "Workload", "Transactions", namespace, command.keySet().toString(), "HIGH");
        }
        Object pipeline = command.get("pipeline");
        if (pipeline instanceof List<?> stages) {
            for (Object stage : stages) {
                if (stage instanceof Document doc) {
                    for (String stageName : doc.keySet()) {
                        if ("$changeStream".equals(stageName)) {
                            add(findings, seen, "Workload", "Change Streams", namespace, "pipeline contains $changeStream", "HIGH");
                        } else if (stageName.startsWith("$")) {
                            String risk = HIGH_RISK_STAGES.contains(stageName) ? "HIGH" : "MEDIUM";
                            add(findings, seen, "Aggregation", "Aggregation stage " + stageName, namespace, "profiler pipeline", risk);
                        }
                    }
                }
            }
        }
        scanOperators(command, namespace, findings, seen);
    }

    @SuppressWarnings("unchecked")
    private void scanOperators(Object value, String namespace, List<FeatureFinding> findings, Set<String> seen) {
        if (value instanceof Document document) {
            for (Map.Entry<String, Object> entry : document.entrySet()) {
                String key = entry.getKey();
                if ("$text".equals(key)) {
                    add(findings, seen, "Workload", "Text search query", namespace, "$text operator", "HIGH");
                }
                if (key.startsWith("$geo") || "$near".equals(key) || "$nearSphere".equals(key)) {
                    add(findings, seen, "Workload", "Geospatial query", namespace, key + " operator", "HIGH");
                }
                if ("arrayFilters".equals(key)) {
                    add(findings, seen, "Workload", "Array filters update", namespace, "arrayFilters option", "MEDIUM");
                }
                scanOperators(entry.getValue(), namespace, findings, seen);
            }
        } else if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                scanOperators(item, namespace, findings, seen);
            }
        }
    }

    private void add(List<FeatureFinding> findings, Set<String> seen, String category, String feature, String namespace, String evidence, String risk) {
        String key = category + "|" + feature + "|" + namespace + "|" + evidence;
        if (seen.add(key)) {
            findings.add(new FeatureFinding(category, feature, namespace, evidence, risk));
        }
    }
}
