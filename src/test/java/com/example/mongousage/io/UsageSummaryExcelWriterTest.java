package com.example.mongousage.io;

import com.example.mongousage.model.CollectionInfo;
import com.example.mongousage.model.CommandError;
import com.example.mongousage.model.DatabaseInfo;
import com.example.mongousage.model.DeploymentInfo;
import com.example.mongousage.model.IndexInfo;
import com.example.mongousage.model.QueryShape;
import com.example.mongousage.model.RuntimeMetric;
import com.example.mongousage.model.UsageReport;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UsageSummaryExcelWriterTest {
    @Test
    void writesSummaryWorkbookFromCollectedReport(@TempDir Path tempDir) throws Exception {
        UsageReport report = sampleReport();
        Path output = tempDir.resolve("mongo-usage-summary.xlsx");

        new UsageSummaryExcelWriter().write(report, output);

        assertThat(output).exists();
        try (InputStream in = Files.newInputStream(output);
             Workbook workbook = WorkbookFactory.create(in)) {
            assertThat(workbook.getSheet("Executive Summary")).isNotNull();
            assertThat(workbook.getSheet("Feature Summary")).isNotNull();
            assertThat(workbook.getSheet("Top Collections")).isNotNull();
            assertThat(workbook.getSheet("Top Query Shapes")).isNotNull();
            assertThat(workbook.getSheet("Risks")).isNotNull();

            DataFormatter formatter = new DataFormatter();
            Sheet executive = workbook.getSheet("Executive Summary");
            String executiveText = sheetText(executive, formatter);
            assertThat(executiveText).contains(
                    "Deployment Mode",
                    "replicaSet",
                    "Database Count",
                    "Collection Count",
                    "Index Count",
                    "Profile Samples",
                    "Command Errors",
                    "Skipped Diagnostics"
            );

            Sheet features = workbook.getSheet("Feature Summary");
            String featureText = sheetText(features, formatter);
            assertThat(featureText).contains("jsonSchema validation", "unique indexes", "query sort", "aggregation");
        }
    }

    private UsageReport sampleReport() {
        CollectionInfo orders = new CollectionInfo("app", "orders", "collection",
                new Document("validator", new Document("$jsonSchema", new Document())));
        orders.setStats(new Document("count", 100).append("storageSize", 2048).append("totalIndexSize", 512));
        orders.setIndexes(List.of(
                new IndexInfo("email_1", new Document("email", 1), new Document("unique", true)),
                new IndexInfo("ttl_1", new Document("expiresAt", 1), new Document("expireAfterSeconds", 3600))));

        DatabaseInfo database = new DatabaseInfo("app");
        database.setStats(new Document("dataSize", 4096).append("storageSize", 8192).append("objects", 100));
        database.setCollections(List.of(orders));

        QueryShape shape = new QueryShape();
        shape.setNamespace("app.orders");
        shape.setOperation("find");
        shape.setSampleCount(8);
        shape.setAvgMillis(12);
        shape.setMaxMillis(80);
        shape.setFeatures(List.of("sort", "projection"));
        shape.setShape("{find: 'orders', filter: {status: '?'}, sort: {createdAt: -1}}");

        DeploymentInfo deploymentInfo = new DeploymentInfo();
        deploymentInfo.setDeploymentMode("replicaSet");
        deploymentInfo.setProvider("self-managed");
        deploymentInfo.setHostingType("self-managed");
        deploymentInfo.setReplicaSetMemberCount(3);

        UsageReport report = new UsageReport();
        report.setTarget("mongodb://user:****@host/admin");
        report.setRequestedMongoVersion("7");
        report.setDeploymentInfo(deploymentInfo);
        report.setBuildInfo(new Document("version", "7.0.0"));
        report.setDatabases(List.of(database));
        report.setProfileSamples(List.of());
        report.setQueryShapes(List.of(shape));
        report.setRuntimeMetrics(List.of(new RuntimeMetric("connections", "current", "12")));
        report.setCommandErrors(List.of(new CommandError("admin", "hostInfo", "unauthorized")));
        return report;
    }

    private String sheetText(Sheet sheet, DataFormatter formatter) {
        StringBuilder text = new StringBuilder();
        sheet.forEach(row -> row.forEach(cell -> text.append(formatter.formatCellValue(cell)).append('\n')));
        return text.toString();
    }
}
