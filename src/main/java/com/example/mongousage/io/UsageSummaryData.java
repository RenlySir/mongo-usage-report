package com.example.mongousage.io;

import com.example.mongousage.model.CollectionInfo;
import com.example.mongousage.model.IndexInfo;
import com.example.mongousage.model.QueryShape;
import com.example.mongousage.model.UsageReport;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class UsageSummaryData {
    private final List<KeyValue> executiveSummary;
    private final List<FeatureItem> featureSummary;
    private final List<CollectionItem> topCollections;
    private final List<QueryShapeItem> topQueryShapes;
    private final List<RiskItem> risks;

    private UsageSummaryData(
            List<KeyValue> executiveSummary,
            List<FeatureItem> featureSummary,
            List<CollectionItem> topCollections,
            List<QueryShapeItem> topQueryShapes,
            List<RiskItem> risks) {
        this.executiveSummary = executiveSummary;
        this.featureSummary = featureSummary;
        this.topCollections = topCollections;
        this.topQueryShapes = topQueryShapes;
        this.risks = risks;
    }

    static UsageSummaryData from(UsageReport report) {
        List<RiskItem> risks = riskItems(report);
        return new UsageSummaryData(
                executiveSummary(report, risks.size()),
                featureSummary(report),
                topCollections(report),
                topQueryShapes(report),
                risks);
    }

    List<KeyValue> executiveSummary() {
        return executiveSummary;
    }

    List<FeatureItem> featureSummary() {
        return featureSummary;
    }

    List<CollectionItem> topCollections() {
        return topCollections;
    }

    List<QueryShapeItem> topQueryShapes() {
        return topQueryShapes;
    }

    List<RiskItem> risks() {
        return risks;
    }

    private static List<KeyValue> executiveSummary(UsageReport report, int riskCount) {
        return List.of(
                new KeyValue("Generated At", String.valueOf(report.getGeneratedAt())),
                new KeyValue("Target", report.getTarget()),
                new KeyValue("Requested MongoDB Version", report.getRequestedMongoVersion()),
                new KeyValue("Server Version", string(report.getBuildInfo().get("version"))),
                new KeyValue("Deployment Mode", report.getDeploymentInfo().getDeploymentMode()),
                new KeyValue("Hosting Type", report.getDeploymentInfo().getHostingType()),
                new KeyValue("Provider", report.getDeploymentInfo().getProvider()),
                new KeyValue("Replica Set Members", report.getDeploymentInfo().getReplicaSetMemberCount()),
                new KeyValue("Shard Count", report.getDeploymentInfo().getShardCount()),
                new KeyValue("Database Count", report.getDatabases().size()),
                new KeyValue("Collection Count", collections(report).size()),
                new KeyValue("Index Count", indexCount(report)),
                new KeyValue("Total Documents", sumCollectionStat(report, "count")),
                new KeyValue("Total Storage Size", sumCollectionStat(report, "storageSize")),
                new KeyValue("Query Shapes", report.getQueryShapes().size()),
                new KeyValue("Profile Samples", report.getProfileSamples().size()),
                new KeyValue("Command Errors", report.getCommandErrors().size()),
                new KeyValue("Skipped Diagnostics", report.getSkippedDiagnostics().size()),
                new KeyValue("Risk Items", riskCount));
    }

    private static List<FeatureItem> featureSummary(UsageReport report) {
        Map<String, String> features = detectedFeatures(report);
        List<FeatureItem> rows = new ArrayList<>();
        for (Map.Entry<String, String> entry : features.entrySet()) {
            rows.add(new FeatureItem(entry.getKey(), !entry.getValue().isBlank(), entry.getValue()));
        }
        return rows;
    }

    private static List<CollectionItem> topCollections(UsageReport report) {
        return collections(report).stream()
                .sorted(Comparator.comparingLong((CollectionInfo c) -> number(c.getStats(), "storageSize")).reversed())
                .limit(50)
                .map(collection -> new CollectionItem(
                        collection.getNamespace(),
                        collection.getType(),
                        number(collection.getStats(), "count"),
                        number(collection.getStats(), "storageSize"),
                        number(collection.getStats(), "totalIndexSize"),
                        collection.getIndexes().size()))
                .toList();
    }

    private static List<QueryShapeItem> topQueryShapes(UsageReport report) {
        return report.getQueryShapes().stream()
                .sorted(Comparator.comparingLong(QueryShape::getMaxMillis).reversed()
                        .thenComparing(Comparator.comparingInt(QueryShape::getSampleCount).reversed()))
                .limit(50)
                .map(shape -> new QueryShapeItem(
                        shape.getNamespace(),
                        shape.getOperation(),
                        shape.getSampleCount(),
                        shape.getAvgMillis(),
                        shape.getMaxMillis(),
                        String.join(", ", shape.getFeatures()),
                        shape.getShape()))
                .toList();
    }

    private static Map<String, String> detectedFeatures(UsageReport report) {
        Map<String, String> features = new LinkedHashMap<>();
        features.put("jsonSchema validation", collections(report).stream()
                .filter(collection -> collection.getOptions().toJson().contains("$jsonSchema"))
                .map(CollectionInfo::getNamespace)
                .findFirst()
                .orElse(""));
        features.put("unique indexes", indexes(report).stream()
                .filter(index -> Boolean.TRUE.equals(index.getRaw().get("unique")))
                .map(IndexInfo::getName)
                .findFirst()
                .orElse(""));
        features.put("TTL indexes", indexes(report).stream()
                .filter(index -> index.getRaw().containsKey("expireAfterSeconds"))
                .map(IndexInfo::getName)
                .findFirst()
                .orElse(""));
        features.put("partial indexes", indexes(report).stream()
                .filter(index -> index.getRaw().containsKey("partialFilterExpression"))
                .map(IndexInfo::getName)
                .findFirst()
                .orElse(""));
        features.put("query sort", queryFeature(report, "sort"));
        features.put("query projection", queryFeature(report, "projection"));
        features.put("aggregation", report.getQueryShapes().stream()
                .filter(shape -> "aggregate".equalsIgnoreCase(shape.getOperation()) || shape.getShape().contains("aggregate"))
                .map(QueryShape::getNamespace)
                .findFirst()
                .orElse(""));
        features.put("profiling data", report.getProfileSamples().isEmpty() ? "" : report.getProfileSamples().size() + " samples");
        return features;
    }

    private static List<RiskItem> riskItems(UsageReport report) {
        List<RiskItem> risks = new ArrayList<>();
        if (!report.getCommandErrors().isEmpty()) {
            risks.add(new RiskItem("Medium", "Permissions / Compatibility",
                    report.getCommandErrors().size() + " diagnostic commands failed during collection.",
                    "Review Command Errors in the detailed workbook and confirm whether failures are expected for the deployment."));
        }
        for (QueryShape shape : report.getQueryShapes()) {
            if (shape.getAvgDocsExamined() > 0 && shape.getAvgReturned() > 0 && shape.getAvgDocsExamined() > shape.getAvgReturned() * 100) {
                risks.add(new RiskItem("High", "Query Efficiency",
                        "Query shape on " + shape.getNamespace() + " examines far more documents than it returns.",
                        "Review indexes and query predicates before migration cutover."));
            }
        }
        for (CollectionInfo collection : collections(report)) {
            long storageSize = number(collection.getStats(), "storageSize");
            long indexSize = number(collection.getStats(), "totalIndexSize");
            if (storageSize > 0 && indexSize > storageSize * 2) {
                risks.add(new RiskItem("Medium", "Index Footprint",
                        collection.getNamespace() + " has index size more than twice storage size.",
                        "Review unused or duplicate indexes and target storage sizing."));
            }
        }
        if (risks.isEmpty()) {
            risks.add(new RiskItem("Info", "Summary", "No high-signal risks detected from collected metadata.", "Review detailed sheets for workload-specific migration questions."));
        }
        return risks;
    }

    private static String queryFeature(UsageReport report, String feature) {
        return report.getQueryShapes().stream()
                .filter(shape -> shape.getFeatures().stream().anyMatch(item -> item.equalsIgnoreCase(feature)))
                .map(QueryShape::getNamespace)
                .findFirst()
                .orElse("");
    }

    private static List<CollectionInfo> collections(UsageReport report) {
        return report.getDatabases().stream()
                .flatMap(database -> database.getCollections().stream())
                .toList();
    }

    private static List<IndexInfo> indexes(UsageReport report) {
        return collections(report).stream()
                .flatMap(collection -> collection.getIndexes().stream())
                .toList();
    }

    private static long indexCount(UsageReport report) {
        return indexes(report).size();
    }

    private static long sumCollectionStat(UsageReport report, String key) {
        return collections(report).stream().mapToLong(collection -> number(collection.getStats(), key)).sum();
    }

    private static long number(Document document, String key) {
        Object value = document == null ? null : document.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    record KeyValue(String key, Object value) {
    }

    record FeatureItem(String feature, boolean detected, String evidence) {
    }

    record CollectionItem(String namespace, String type, long documents, long storageSize, long indexSize, int indexCount) {
    }

    record QueryShapeItem(String namespace, String operation, int samples, long avgMillis, long maxMillis, String features, String shape) {
    }

    record RiskItem(String severity, String area, String observation, String suggestedReview) {
    }
}
