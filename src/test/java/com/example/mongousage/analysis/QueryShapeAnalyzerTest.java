package com.example.mongousage.analysis;

import com.example.mongousage.model.ProfileSample;
import com.example.mongousage.model.QueryShape;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QueryShapeAnalyzerTest {
    @Test
    void deduplicatesEquivalentFindQueriesWithDifferentLiteralValues() {
        ProfileSample paid = sample(new Document("find", "orders")
                .append("filter", new Document("status", "PAID").append("amount", new Document("$gt", 100)))
                .append("sort", new Document("createdAt", -1)));
        paid.setMillis(20);
        paid.setDocsExamined(100);
        paid.setKeysExamined(50);
        paid.setNreturned(10);

        ProfileSample pending = sample(new Document("find", "orders")
                .append("filter", new Document("status", "PENDING").append("amount", new Document("$gt", 500)))
                .append("sort", new Document("createdAt", -1)));
        pending.setMillis(40);
        pending.setDocsExamined(300);
        pending.setKeysExamined(80);
        pending.setNreturned(20);

        List<QueryShape> shapes = new QueryShapeAnalyzer().analyze(List.of(paid, pending));

        assertThat(shapes).hasSize(1);
        QueryShape shape = shapes.get(0);
        assertThat(shape.getSampleCount()).isEqualTo(2);
        assertThat(shape.getOperation()).isEqualTo("find");
        assertThat(shape.getNamespace()).isEqualTo("shop.orders");
        assertThat(shape.getShape()).contains("\"status\": \"?\"", "\"$gt\": \"?\"", "\"createdAt\": \"?\"");
        assertThat(shape.getAvgMillis()).isEqualTo(30);
        assertThat(shape.getMaxMillis()).isEqualTo(40);
    }

    @Test
    void extractsAggregationStagesForShape() {
        ProfileSample sample = sample(new Document("aggregate", "orders")
                .append("pipeline", List.of(
                        new Document("$match", new Document("status", "PAID")),
                        new Document("$group", new Document("_id", "$customerId").append("total", new Document("$sum", "$amount")))
                )));

        List<QueryShape> shapes = new QueryShapeAnalyzer().analyze(List.of(sample));

        assertThat(shapes).hasSize(1);
        assertThat(shapes.get(0).getFeatures()).contains("$match", "$group");
    }

    private ProfileSample sample(Document command) {
        ProfileSample sample = new ProfileSample("shop", "shop.orders", "query", command);
        return sample;
    }
}
