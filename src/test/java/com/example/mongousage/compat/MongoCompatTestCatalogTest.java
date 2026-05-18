package com.example.mongousage.compat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MongoCompatTestCatalogTest {
    @Test
    void catalogRecordsBroadMongoDbFeatureCoverage() {
        assertThat(MongoCompatTestCatalog.cases())
                .extracting(MongoCompatTestCase::category)
                .contains(
                        "schema",
                        "crud",
                        "query",
                        "index",
                        "aggregation",
                        "transaction",
                        "changeStream",
                        "command");

        assertThat(MongoCompatTestCatalog.cases()).hasSizeGreaterThanOrEqualTo(100);

        assertThat(MongoCompatTestCatalog.cases())
                .extracting(MongoCompatTestCase::id)
                .doesNotHaveDuplicates()
                .contains("schema-json-validator", "crud-bulk-write", "aggregation-lookup", "transaction-commit");
    }

    @Test
    void catalogDocumentationListsEveryTestCase() throws Exception {
        String document = java.nio.file.Files.readString(java.nio.file.Path.of("test_case.md"));

        assertThat(document).contains("# MongoDB Compatibility Test Cases");
        for (MongoCompatTestCase testCase : MongoCompatTestCatalog.cases()) {
            assertThat(document).contains("| `" + testCase.id() + "` |");
        }
    }
}
