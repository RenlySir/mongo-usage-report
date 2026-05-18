package com.example.mongousage.compat;

import com.mongodb.MongoCommandException;
import com.mongodb.MongoException;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.ValidationOptions;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Aggregates.facet;
import static com.mongodb.client.model.Aggregates.group;
import static com.mongodb.client.model.Aggregates.lookup;
import static com.mongodb.client.model.Aggregates.match;
import static com.mongodb.client.model.Aggregates.sort;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gte;
import static com.mongodb.client.model.Indexes.ascending;
import static com.mongodb.client.model.Indexes.compoundIndex;
import static com.mongodb.client.model.Projections.excludeId;
import static com.mongodb.client.model.Projections.fields;
import static com.mongodb.client.model.Projections.include;
import static com.mongodb.client.model.Accumulators.sum;

public class MongoCompatTestRunner {
    private final MongoClient client;
    private final String databaseName;
    private final boolean dropDatabaseAfterRun;
    private final String mongoVersion;

    public MongoCompatTestRunner(MongoClient client, String databaseName, boolean dropDatabaseAfterRun) {
        this(client, databaseName, dropDatabaseAfterRun, "");
    }

    public MongoCompatTestRunner(MongoClient client, String databaseName, boolean dropDatabaseAfterRun, String mongoVersion) {
        this.client = client;
        this.databaseName = databaseName;
        this.dropDatabaseAfterRun = dropDatabaseAfterRun;
        this.mongoVersion = mongoVersion == null ? "" : mongoVersion;
    }

    public MongoCompatTestReport run() {
        MongoDatabase database = client.getDatabase(databaseName);
        database.drop();
        MongoCompatTestReport report = new MongoCompatTestReport(databaseName, dropDatabaseAfterRun, mongoVersion);
        try {
            runCase(report, "schema-json-validator", () -> createSchema(database));
            runCase(report, "schema-validation-reject", () -> verifySchemaRejectsInvalidDocument(database));
            runCase(report, "index-compound-unique-ttl", () -> createIndexes(database));
            seedData(database);
            runCase(report, "crud-insert-find-update-delete", () -> runCrudLifecycle(database));
            runCase(report, "crud-bulk-write", () -> runBulkWrite(database));
            runCase(report, "query-filter-sort-project", () -> runFilterSortProjection(database));
            runCase(report, "query-array-element", () -> runArrayAndNestedQuery(database));
            runCase(report, "index-hint-explain", () -> runHintAndExplain(database));
            runCase(report, "aggregation-group-sort", () -> runAggregationGroupSort(database));
            runCase(report, "aggregation-lookup", () -> runAggregationLookup(database));
            runCase(report, "aggregation-facet", () -> runAggregationFacet(database));
            runCase(report, "transaction-commit", () -> runTransaction(database));
            runCase(report, "change-stream-open", () -> runChangeStream(database));
            runCase(report, "command-buildinfo-serverstatus", () -> runAdminCommands());
        } finally {
            if (dropDatabaseAfterRun) {
                database.drop();
            }
        }
        return report;
    }

    private void runCase(MongoCompatTestReport report, String id, ThrowingRunnable runnable) {
        MongoCompatTestCase testCase = findCase(id);
        long start = System.nanoTime();
        try {
            runnable.run();
            report.add(MongoCompatTestResult.passed(id, testCase.category(), testCase.name(), elapsedMillis(start)));
        } catch (UnsupportedMongoFeatureException e) {
            report.add(MongoCompatTestResult.skipped(id, testCase.category(), testCase.name(), e.getMessage(), elapsedMillis(start)));
        } catch (Exception e) {
            report.add(MongoCompatTestResult.failed(id, testCase.category(), testCase.name(), e.getMessage(), elapsedMillis(start)));
        }
    }

    private MongoCompatTestCase findCase(String id) {
        return MongoCompatTestCatalog.cases().stream()
                .filter(testCase -> testCase.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown compatibility test case: " + id));
    }

    private long elapsedMillis(long start) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    private void createSchema(MongoDatabase database) {
        Document validator = new Document("$jsonSchema", new Document("bsonType", "object")
                .append("required", List.of("orderNo", "customerId", "status", "total", "createdAt", "lines"))
                .append("properties", new Document()
                        .append("orderNo", new Document("bsonType", "string"))
                        .append("customerId", new Document("bsonType", "int"))
                        .append("status", new Document("enum", List.of("new", "paid", "shipped", "cancelled")))
                        .append("total", new Document("bsonType", List.of("double", "int", "long", "decimal")))
                        .append("createdAt", new Document("bsonType", "date"))
                        .append("lines", new Document("bsonType", "array"))));
        database.createCollection("orders", new com.mongodb.client.model.CreateCollectionOptions()
                .validationOptions(new ValidationOptions().validator(validator)));
        database.createCollection("customers");
        database.createCollection("events");
    }

    private void verifySchemaRejectsInvalidDocument(MongoDatabase database) {
        try {
            database.getCollection("orders").insertOne(new Document("orderNo", "BAD-1"));
        } catch (MongoException expected) {
            return;
        }
        throw new IllegalStateException("Invalid document was accepted by JSON schema validator");
    }

    private void createIndexes(MongoDatabase database) {
        MongoCollection<Document> orders = database.getCollection("orders");
        orders.createIndex(compoundIndex(ascending("customerId", "status"), Indexes.descending("createdAt")));
        orders.createIndex(compoundIndex(ascending("region"), Indexes.descending("total")));
        orders.createIndex(ascending("expiresAt"), new IndexOptions().expireAfter(3600L, TimeUnit.SECONDS));
        MongoCollection<Document> customers = database.getCollection("customers");
        customers.createIndex(ascending("email"), new IndexOptions().unique(true));
        customers.createIndex(ascending("tier", "region"));
    }

    private void seedData(MongoDatabase database) {
        MongoCollection<Document> customers = database.getCollection("customers");
        MongoCollection<Document> orders = database.getCollection("orders");
        List<Document> customerDocs = new ArrayList<>();
        List<Document> orderDocs = new ArrayList<>();
        String[] regions = {"cn-north", "cn-east", "us-west", "eu-central"};
        String[] statuses = {"new", "paid", "shipped", "cancelled"};
        for (int i = 0; i < 40; i++) {
            customerDocs.add(new Document("customerId", i)
                    .append("email", "compat-" + i + "@example.com")
                    .append("tier", new String[]{"silver", "gold", "platinum"}[i % 3])
                    .append("region", regions[i % regions.length]));
        }
        for (int i = 0; i < 160; i++) {
            orderDocs.add(new Document("orderNo", "COMPAT-" + i)
                    .append("customerId", i % 40)
                    .append("status", statuses[i % statuses.length])
                    .append("region", regions[i % regions.length])
                    .append("total", (double) ((i % 100) + 10))
                    .append("createdAt", new java.util.Date(System.currentTimeMillis() - i * 60000L))
                    .append("expiresAt", new java.util.Date(System.currentTimeMillis() + 86400000L))
                    .append("lines", List.of(new Document("sku", "SKU-" + (i % 20)).append("qty", (i % 5) + 1))));
        }
        customers.insertMany(customerDocs);
        orders.insertMany(orderDocs);
        database.getCollection("events").insertOne(new Document("type", "seed").append("at", new java.util.Date()));
    }

    private void runCrudLifecycle(MongoDatabase database) {
        MongoCollection<Document> customers = database.getCollection("customers");
        customers.insertOne(new Document("customerId", 999).append("email", "compat-extra@example.com").append("tier", "gold").append("region", "cn-east"));
        require(customers.countDocuments(eq("customerId", 999)) == 1, "insert/find failed");
        customers.updateOne(eq("customerId", 999), Updates.set("tier", "platinum"));
        Document updated = customers.findOneAndUpdate(eq("customerId", 999), Updates.inc("visits", 1), new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
        require("platinum".equals(updated.getString("tier")), "update failed");
        customers.deleteOne(eq("customerId", 999));
        require(customers.countDocuments(eq("customerId", 999)) == 0, "delete failed");
    }

    private void runBulkWrite(MongoDatabase database) {
        MongoCollection<Document> customers = database.getCollection("customers");
        List<WriteModel<Document>> writes = List.of(
                new InsertOneModel<>(new Document("customerId", 1001).append("email", "bulk-1001@example.com").append("tier", "silver").append("region", "cn-north")),
                new UpdateOneModel<>(eq("customerId", 1001), Updates.set("tier", "gold")),
                new UpdateOneModel<>(eq("customerId", 1002), Updates.combine(Updates.set("email", "bulk-1002@example.com"), Updates.set("tier", "gold"), Updates.set("region", "us-west")), new com.mongodb.client.model.UpdateOptions().upsert(true)));
        require(customers.bulkWrite(writes, new BulkWriteOptions().ordered(true)).wasAcknowledged(), "bulk write not acknowledged");
    }

    private void runFilterSortProjection(MongoDatabase database) {
        List<Document> rows = database.getCollection("orders")
                .find(Filters.and(gte("total", 30), eq("status", "paid")))
                .projection(fields(include("orderNo", "total"), excludeId()))
                .sort(Sorts.descending("createdAt"))
                .skip(1)
                .limit(5)
                .into(new ArrayList<>());
        require(!rows.isEmpty(), "query returned no rows");
        require(rows.get(0).containsKey("orderNo") && !rows.get(0).containsKey("_id"), "projection failed");
    }

    private void runArrayAndNestedQuery(MongoDatabase database) {
        long count = database.getCollection("orders").countDocuments(Filters.elemMatch("lines", Filters.and(eq("sku", "SKU-1"), gte("qty", 1))));
        require(count > 0, "array query returned no rows");
    }

    private void runHintAndExplain(MongoDatabase database) {
        Document explain = database.runCommand(new Document("explain", new Document("find", "orders")
                .append("filter", new Document("region", "cn-east"))
                .append("hint", new Document("region", 1).append("total", -1))));
        require(explain != null && !explain.isEmpty(), "explain returned no plan");
    }

    private void runAggregationGroupSort(MongoDatabase database) {
        List<Document> rows = database.getCollection("orders")
                .aggregate(List.of(match(eq("status", "paid")), group("$region", sum("count", 1), sum("revenue", "$total")), sort(Sorts.descending("count"))))
                .into(new ArrayList<>());
        require(!rows.isEmpty(), "aggregation returned no rows");
    }

    private void runAggregationLookup(MongoDatabase database) {
        List<Document> rows = database.getCollection("orders")
                .aggregate(List.of(match(eq("orderNo", "COMPAT-1")), lookup("customers", "customerId", "customerId", "customer")))
                .into(new ArrayList<>());
        require(!rows.isEmpty() && rows.get(0).get("customer", List.class) != null, "$lookup failed");
    }

    private void runAggregationFacet(MongoDatabase database) {
        List<Document> rows = database.getCollection("orders")
                .aggregate(List.of(facet(
                        new com.mongodb.client.model.Facet("byStatus", group("$status", sum("count", 1))),
                        new com.mongodb.client.model.Facet("highValue", match(gte("total", 80))))))
                .into(new ArrayList<>());
        require(!rows.isEmpty(), "$facet returned no rows");
    }

    private void runTransaction(MongoDatabase database) {
        try (ClientSession session = client.startSession()) {
            session.startTransaction();
            database.getCollection("events").insertOne(session, new Document("type", "transaction").append("at", new java.util.Date()));
            database.getCollection("customers").updateOne(session, eq("customerId", 1), Updates.inc("txnVisits", 1));
            session.commitTransaction();
        } catch (MongoCommandException e) {
            if (e.getErrorMessage() != null && e.getErrorMessage().contains("Transaction numbers are only allowed")) {
                throw new UnsupportedMongoFeatureException("Transactions require a replica set or sharded cluster");
            }
            throw e;
        } catch (MongoException e) {
            if (isUnsupportedTransactionMessage(e.getMessage())) {
                throw new UnsupportedMongoFeatureException("Transactions are unavailable on this deployment: " + e.getMessage());
            }
            throw e;
        }
    }

    static boolean isUnsupportedTransactionMessage(String message) {
        String normalized = message == null ? "" : message.toLowerCase();
        return normalized.contains("transaction") || normalized.contains("does not support retryable writes");
    }

    private void runChangeStream(MongoDatabase database) {
        try (MongoCursor<com.mongodb.client.model.changestream.ChangeStreamDocument<Document>> cursor = database.getCollection("events")
                .watch()
                .maxAwaitTime(2, TimeUnit.SECONDS)
                .iterator()) {
            database.getCollection("events").insertOne(new Document("type", "change-stream").append("at", new java.util.Date()));
            if (!cursor.hasNext()) {
                throw new IllegalStateException("change stream did not receive event");
            }
        } catch (MongoCommandException e) {
            if (e.getErrorMessage() != null && e.getErrorMessage().contains("replica set")) {
                throw new UnsupportedMongoFeatureException("Change streams require a replica set or sharded cluster");
            }
            throw e;
        } catch (MongoException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("change stream")) {
                throw new UnsupportedMongoFeatureException("Change streams are unavailable on this deployment: " + e.getMessage());
            }
            throw e;
        }
    }

    private void runAdminCommands() {
        client.getDatabase("admin").runCommand(new Document("buildInfo", 1));
        client.getDatabase("admin").runCommand(new Document("serverStatus", 1));
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private interface ThrowingRunnable {
        void run();
    }

    private static class UnsupportedMongoFeatureException extends RuntimeException {
        UnsupportedMongoFeatureException(String message) {
            super(message);
        }
    }
}
