package com.example.mongousage.mongo;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MongoCollectorTest {
    @Test
    void ignoresNonDocumentEntriesInTopTotals() {
        Document top = new Document("totals", new Document()
                .append("note", "all times in microseconds")
                .append("shop.orders", new Document("total", new Document("time", 42))));

        assertThat(MongoCollector.toNamespaceUsageRows(top))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getString("namespace")).isEqualTo("shop.orders");
                    assertThat(row.get("usage", Document.class))
                            .isEqualTo(new Document("total", new Document("time", 42)));
                });
    }

    @Test
    void collectsDefaultReadWriteConcernOnlyForReplicaSetsAndMongos() {
        assertThat(MongoCollector.isDistributedDeployment(new Document("isWritablePrimary", true))).isFalse();
        assertThat(MongoCollector.isDistributedDeployment(new Document("setName", "rs0"))).isTrue();
        assertThat(MongoCollector.isDistributedDeployment(new Document("msg", "isdbgrid"))).isTrue();
    }

    @Test
    void currentOpAggregationPipelineFiltersActiveOperationsWithMatchStage() {
        assertThat(MongoCollector.currentOpAggregationPipeline())
                .containsExactly(
                        new Document("$currentOp", new Document("allUsers", false)),
                        new Document("$match", new Document("active", true)));
    }

    @Test
    void skipsDiagnosticAggregationsForSystemCollections() {
        assertThat(MongoCollector.shouldCollectCollectionAggregations("orders")).isTrue();
        assertThat(MongoCollector.shouldCollectCollectionAggregations("system.profile")).isFalse();
        assertThat(MongoCollector.shouldCollectCollectionAggregations("system.views")).isFalse();
    }
}
