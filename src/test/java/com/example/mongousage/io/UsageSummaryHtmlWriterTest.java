package com.example.mongousage.io;

import com.example.mongousage.model.CollectionInfo;
import com.example.mongousage.model.CommandError;
import com.example.mongousage.model.DatabaseInfo;
import com.example.mongousage.model.DeploymentInfo;
import com.example.mongousage.model.IndexInfo;
import com.example.mongousage.model.QueryShape;
import com.example.mongousage.model.RuntimeMetric;
import com.example.mongousage.model.UsageReport;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UsageSummaryHtmlWriterTest {
    @Test
    void writesSummaryHtmlFromCollectedReport(@TempDir Path tempDir) throws Exception {
        UsageReport report = sampleReport();
        Path output = tempDir.resolve("mongo-usage-summary.html");

        new UsageSummaryHtmlWriter().write(report, output);

        assertThat(output).exists();
        String html = Files.readString(output);
        assertThat(html).contains(
                "<!doctype html>",
                "MongoDB Usage Summary",
                "Executive Summary",
                "Feature Summary",
                "Top Collections",
                "Top Query Shapes",
                "Risks",
                "replicaSet",
                "app.orders",
                "jsonSchema validation",
                "unique indexes",
                "Command Errors",
                "Skipped Diagnostics"
        );
        assertThat(html).contains("&lt;unsafe&gt;");
        assertThat(html).doesNotContain("<unsafe>");
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
        shape.setShape("{find: '<unsafe>', filter: {status: '?'}, sort: {createdAt: -1}}");

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
}
