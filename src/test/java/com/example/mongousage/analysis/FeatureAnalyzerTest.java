package com.example.mongousage.analysis;

import com.example.mongousage.model.CollectionInfo;
import com.example.mongousage.model.DatabaseInfo;
import com.example.mongousage.model.FeatureFinding;
import com.example.mongousage.model.IndexInfo;
import com.example.mongousage.model.ProfileSample;
import com.example.mongousage.model.UsageReport;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureAnalyzerTest {
    @Test
    void detectsStaticCollectionAndIndexFeatures() {
        CollectionInfo orders = new CollectionInfo("shop", "orders", "collection",
                new Document("validator", new Document("$jsonSchema", new Document()))
                        .append("timeseries", new Document("timeField", "ts"))
                        .append("capped", true));
        orders.setIndexes(List.of(
                new IndexInfo("ttl", new Document("expiresAt", 1), new Document("expireAfterSeconds", 3600)),
                new IndexInfo("text", new Document("description", "text"), new Document()),
                new IndexInfo("geo", new Document("location", "2dsphere"), new Document()),
                new IndexInfo("hashed", new Document("tenantId", "hashed"), new Document()),
                new IndexInfo("wild", new Document("$**", 1), new Document())
        ));
        DatabaseInfo db = new DatabaseInfo("shop");
        db.setCollections(List.of(orders));
        UsageReport report = new UsageReport();
        report.setDatabases(List.of(db));

        List<FeatureFinding> findings = new FeatureAnalyzer().analyze(report);

        assertThat(features(findings)).contains(
                "JSON Schema validator",
                "Time series collection",
                "Capped collection",
                "TTL index",
                "Text index",
                "2dsphere index",
                "Hashed index",
                "Wildcard index"
        );
    }

    @Test
    void detectsWorkloadFeaturesFromProfilerCommands() {
        UsageReport report = new UsageReport();
        report.setProfileSamples(List.of(
                new ProfileSample("shop", "shop.orders", "command",
                        new Document("aggregate", "orders")
                                .append("pipeline", List.of(
                                        new Document("$changeStream", new Document()),
                                        new Document("$lookup", new Document()),
                                        new Document("$setWindowFields", new Document())
                                ))),
                new ProfileSample("shop", "shop.orders", "query",
                        new Document("find", "orders")
                                .append("filter", new Document("$text", new Document("$search", "abc"))
                                        .append("location", new Document("$near", List.of(1, 2))))),
                new ProfileSample("shop", "shop.orders", "command",
                        new Document("commitTransaction", 1))
        ));

        List<FeatureFinding> findings = new FeatureAnalyzer().analyze(report);

        assertThat(features(findings)).contains(
                "Change Streams",
                "Aggregation stage $lookup",
                "Aggregation stage $setWindowFields",
                "Text search query",
                "Geospatial query",
                "Transactions"
        );
    }

    private static List<String> features(List<FeatureFinding> findings) {
        return findings.stream().map(FeatureFinding::getFeature).toList();
    }
}
