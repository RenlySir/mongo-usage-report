package com.example.mongousage.io;

import com.example.mongousage.model.CollectionInfo;
import com.example.mongousage.model.DatabaseInfo;
import com.example.mongousage.model.IndexInfo;
import com.example.mongousage.model.UsageReport;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

class CollectExcelWriterTest {
    @Test
    void writesWorkbookWithExpectedSheets(@TempDir Path tempDir) throws Exception {
        CollectionInfo collection = new CollectionInfo("app", "orders", "collection", new Document("validator", new Document()));
        collection.setStats(new Document("count", 10).append("storageSize", 2048).append("totalIndexSize", 512));
        collection.setIndexes(List.of(new IndexInfo("status_1", new Document("status", 1), new Document("unique", false))));
        DatabaseInfo database = new DatabaseInfo("app");
        database.setStats(new Document("dataSize", 4096).append("storageSize", 8192));
        database.setCollections(List.of(collection));
        UsageReport report = new UsageReport();
        report.setTarget("mongodb://user:****@host/admin");
        report.setDatabases(List.of(database));

        new CollectExcelWriter().write(report, tempDir.resolve("mongo-usage-report.xlsx"));

        try (ZipFile workbook = new ZipFile(tempDir.resolve("mongo-usage-report.xlsx").toFile())) {
            String workbookXml = new String(workbook.getInputStream(workbook.getEntry("xl/workbook.xml")).readAllBytes());
            assertThat(workbookXml).contains(
                    "Overview",
                    "Deployment",
                    "Runtime Metrics",
                    "Databases",
                    "Collections",
                    "Indexes",
                    "Namespace Usage",
                    "Query Stats",
                    "Query Shapes",
                    "Workload",
                    "Command Errors"
            );
        }
    }
}
