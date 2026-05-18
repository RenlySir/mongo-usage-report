package com.example.mongousage.io;

import com.example.mongousage.analysis.QueryShapeAnalyzer;
import com.example.mongousage.model.CollectionInfo;
import com.example.mongousage.model.CommandError;
import com.example.mongousage.model.DatabaseInfo;
import com.example.mongousage.model.DeploymentInfo;
import com.example.mongousage.model.IndexInfo;
import com.example.mongousage.model.ProfileSample;
import com.example.mongousage.model.RuntimeMetric;
import com.example.mongousage.model.UsageReport;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

class SampleExcelReportGeneratorTest {
    @Test
    void writesSampleExcelReport() throws Exception {
        UsageReport report = new UsageReport();
        report.setGeneratedAt(Instant.parse("2026-05-15T08:00:00Z"));
        report.setTarget("mongodb://sampleUser:****@sample-host:27017/admin");
        report.setRequestedMongoVersion("7.0.0");
        report.setBuildInfo(new Document("version", "7.0.11").append("gitVersion", "sample"));
        report.setHello(new Document("ok", 1).append("maxWireVersion", 21));
        report.setConnectionStatus(new Document("authInfo", new Document("authenticatedUsers", List.of(new Document("user", "sampleUser").append("db", "admin")))));
        report.setDefaultReadWriteConcern(new Document("defaultReadConcern", new Document("level", "majority"))
                .append("defaultWriteConcern", new Document("w", "majority")));
        DeploymentInfo deployment = new DeploymentInfo();
        deployment.setDeploymentMode("replicaSet");
        deployment.setReplicaSetName("rs0");
        deployment.setPrimary("mongo-1:27017");
        deployment.setHosts(List.of("mongo-1:27017", "mongo-2:27017", "mongo-3:27017"));
        deployment.setStorageEngine("wiredTiger");
        deployment.setFeatureCompatibilityVersion("7.0");
        report.setDeploymentInfo(deployment);
        report.setRuntimeMetrics(List.of(
                new RuntimeMetric("connections", "current", "128"),
                new RuntimeMetric("opcounters", "query", "980000"),
                new RuntimeMetric("opcounters", "insert", "120000"),
                new RuntimeMetric("network", "bytesIn", "734003200")
        ));

        CollectionInfo orders = new CollectionInfo("shop", "orders", "collection",
                new Document("validator", new Document("$jsonSchema", new Document("bsonType", "object"))));
        orders.setStats(new Document("count", 128000)
                .append("storageSize", 73400320)
                .append("totalIndexSize", 6291456)
                .append("avgObjSize", 512));
        orders.setIndexes(List.of(
                new IndexInfo("_id_", new Document("_id", 1), new Document("unique", true)),
                new IndexInfo("status_createdAt", new Document("status", 1).append("createdAt", -1), new Document("unique", false)),
                new IndexInfo("expireAt_ttl", new Document("expireAt", 1), new Document("expireAfterSeconds", 0))
        ));

        CollectionInfo customers = new CollectionInfo("shop", "customers", "collection", new Document());
        customers.setStats(new Document("count", 35000)
                .append("storageSize", 18874368)
                .append("totalIndexSize", 2097152)
                .append("avgObjSize", 384));
        customers.setIndexes(List.of(
                new IndexInfo("_id_", new Document("_id", 1), new Document("unique", true)),
                new IndexInfo("email_unique", new Document("email", 1), new Document("unique", true))
        ));

        DatabaseInfo shop = new DatabaseInfo("shop");
        shop.setStats(new Document("dataSize", 92274688).append("storageSize", 104857600).append("objects", 163000));
        shop.setCollections(List.of(orders, customers));
        report.setDatabases(List.of(shop));

        ProfileSample findSample = new ProfileSample("shop", "shop.orders", "query",
                new Document("find", "orders").append("filter", new Document("status", "PAID")));
        findSample.setMillis(72);
        findSample.setDocsExamined(1200);
        findSample.setKeysExamined(1200);
        findSample.setNreturned(50);
        ProfileSample similarFindSample = new ProfileSample("shop", "shop.orders", "query",
                new Document("find", "orders").append("filter", new Document("status", "PENDING")));
        similarFindSample.setMillis(95);
        similarFindSample.setDocsExamined(1800);
        similarFindSample.setKeysExamined(1800);
        similarFindSample.setNreturned(80);
        ProfileSample aggregateSample = new ProfileSample("shop", "shop.orders", "command",
                new Document("aggregate", "orders").append("pipeline", List.of(
                        new Document("$match", new Document("status", "PAID")),
                        new Document("$group", new Document("_id", "$customerId").append("total", new Document("$sum", "$amount")))
                )));
        aggregateSample.setMillis(180);
        aggregateSample.setDocsExamined(30000);
        aggregateSample.setNreturned(1000);
        report.setProfileSamples(List.of(findSample, similarFindSample, aggregateSample));
        report.setQueryShapes(new QueryShapeAnalyzer().analyze(report.getProfileSamples()));
        report.setNamespaceUsage(List.of(new Document("namespace", "shop.orders")
                .append("usage", new Document("readLock", new Document("time", 1000).append("count", 42))
                        .append("writeLock", new Document("time", 500).append("count", 12)))));
        report.setQueryStats(List.of(new Document("key", new Document("queryShape", "find orders by status"))
                .append("metrics", new Document("execCount", 2).append("totalExecMicros", 167000))));
        report.setCommandErrors(List.of(new CommandError("shop.orders", "$planCacheStats", "Sample: command not available on target service")));

        new CollectExcelWriter().write(report, Path.of("target/sample-data-excel-report/mongo-usage-report.xlsx"));
    }
}
