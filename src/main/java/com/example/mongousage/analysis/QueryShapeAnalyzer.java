package com.example.mongousage.analysis;

import com.example.mongousage.model.ProfileSample;
import com.example.mongousage.model.QueryShape;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class QueryShapeAnalyzer {
    public List<QueryShape> analyze(List<ProfileSample> samples) {
        Map<String, Accumulator> groups = new LinkedHashMap<>();
        for (ProfileSample sample : samples) {
            Document command = sample.getCommand();
            if (command == null || command.isEmpty()) {
                continue;
            }
            String operation = operation(command, sample.getOperation());
            String normalized = normalize(command).toJson();
            String key = sample.getNamespace() + "|" + operation + "|" + normalized;
            groups.computeIfAbsent(key, ignored -> new Accumulator(operation, sample.getNamespace(), normalized))
                    .add(sample, features(command));
        }
        return groups.values().stream()
                .map(Accumulator::toShape)
                .sorted(Comparator.comparing(QueryShape::getSampleCount).reversed()
                        .thenComparing(QueryShape::getNamespace, Comparator.nullsLast(String::compareTo))
                        .thenComparing(QueryShape::getOperation, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private String operation(Document command, String fallback) {
        for (String candidate : List.of("find", "aggregate", "count", "distinct", "insert", "update", "delete", "findAndModify")) {
            if (command.containsKey(candidate)) {
                return candidate;
            }
        }
        return fallback == null || fallback.isBlank() ? "unknown" : fallback;
    }

    private Document normalize(Document input) {
        Document normalized = new Document();
        input.keySet().stream().sorted().forEach(key -> normalized.put(key, normalizeValue(input.get(key), key)));
        return normalized;
    }

    private Object normalizeValue(Object value, String key) {
        if ("find".equals(key) || "aggregate".equals(key) || "count".equals(key) || "distinct".equals(key)
                || "insert".equals(key) || "update".equals(key) || "delete".equals(key) || "findAndModify".equals(key)) {
            return value;
        }
        if (value instanceof Document document) {
            return normalize(document);
        }
        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Document document) {
                    normalized.add(normalize(document));
                } else {
                    normalized.add("?");
                }
            }
            return normalized;
        }
        return "?";
    }

    private Set<String> features(Document command) {
        Set<String> features = new LinkedHashSet<>();
        collectFeatures(command, features);
        return features;
    }

    private void collectFeatures(Object value, Set<String> features) {
        if (value instanceof Document document) {
            for (Map.Entry<String, Object> entry : document.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith("$")) {
                    features.add(key);
                }
                if ("arrayFilters".equals(key)) {
                    features.add("arrayFilters");
                }
                collectFeatures(entry.getValue(), features);
            }
        } else if (value instanceof List<?> list) {
            for (Object item : list) {
                collectFeatures(item, features);
            }
        }
    }

    private static class Accumulator {
        private final String operation;
        private final String namespace;
        private final String shape;
        private final Set<String> features = new LinkedHashSet<>();
        private int count;
        private long millisTotal;
        private long millisMax;
        private long docsExaminedTotal;
        private long keysExaminedTotal;
        private long returnedTotal;

        private Accumulator(String operation, String namespace, String shape) {
            this.operation = operation;
            this.namespace = namespace;
            this.shape = shape;
        }

        private void add(ProfileSample sample, Set<String> sampleFeatures) {
            count++;
            millisTotal += sample.getMillis();
            millisMax = Math.max(millisMax, sample.getMillis());
            docsExaminedTotal += sample.getDocsExamined();
            keysExaminedTotal += sample.getKeysExamined();
            returnedTotal += sample.getNreturned();
            features.addAll(sampleFeatures);
        }

        private QueryShape toShape() {
            QueryShape queryShape = new QueryShape();
            queryShape.setOperation(operation);
            queryShape.setNamespace(namespace);
            queryShape.setShape(shape);
            queryShape.setFeatures(new ArrayList<>(features));
            queryShape.setSampleCount(count);
            queryShape.setAvgMillis(count == 0 ? 0 : millisTotal / count);
            queryShape.setMaxMillis(millisMax);
            queryShape.setAvgDocsExamined(count == 0 ? 0 : docsExaminedTotal / count);
            queryShape.setAvgKeysExamined(count == 0 ? 0 : keysExaminedTotal / count);
            queryShape.setAvgReturned(count == 0 ? 0 : returnedTotal / count);
            return queryShape;
        }
    }
}
