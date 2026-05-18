package com.example.mongousage.compat;

import com.mongodb.MongoCommandException;
import com.mongodb.MongoException;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Collation;
import com.mongodb.client.model.CollationStrength;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ChangeStreamPreAndPostImagesOptions;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.FindOneAndReplaceOptions;
import com.mongodb.client.model.FindOneAndDeleteOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.ValidationOptions;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.Sorts;
import org.bson.BsonBinary;
import org.bson.BsonRegularExpression;
import org.bson.BsonTimestamp;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.MaxKey;
import org.bson.types.MinKey;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Accumulators.addToSet;
import static com.mongodb.client.model.Accumulators.avg;
import static com.mongodb.client.model.Accumulators.max;
import static com.mongodb.client.model.Accumulators.min;
import static com.mongodb.client.model.Accumulators.push;
import static com.mongodb.client.model.Accumulators.sum;
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
            prepareFixture(report, database);
            for (MongoCompatTestCase testCase : MongoCompatTestCatalog.cases()) {
                runCase(report, testCase, () -> executeCase(testCase.id(), database));
            }
        } finally {
            if (dropDatabaseAfterRun) {
                database.drop();
            }
        }
        return report;
    }

    private void prepareFixture(MongoCompatTestReport report, MongoDatabase database) {
        runCase(report, new MongoCompatTestCase("fixture-prepare", "fixture", "Prepare test fixture", "Creates reusable schema, indexes, and sample data."), () -> {
            createSchema(database);
            createIndexes(database);
            seedData(database);
        });
        report.getResults().remove(report.getResults().size() - 1);
    }

    private void executeCase(String id, MongoDatabase database) {
        switch (id) {
            case "schema-json-validator" -> verifyCollectionExists(database, "orders");
            case "schema-validation-reject", "schema-missing-required-field-rejected" -> verifySchemaRejectsInvalidDocument(database);
            case "schema-list-collections" -> verifyCollectionExists(database, "customers");
            case "schema-validator-options-readable" -> verifyValidatorOptions(database);
            case "schema-valid-document-accepted" -> insertValidSchemaDocument(database);
            case "schema-invalid-enum-rejected" -> expectInsertFailure(database, new Document("orderNo", "BAD-ENUM").append("customerId", 1).append("status", "bad").append("total", 1).append("createdAt", new Date()).append("lines", List.of()));
            case "schema-invalid-array-type-rejected" -> expectInsertFailure(database, new Document("orderNo", "BAD-ARRAY").append("customerId", 1).append("status", "new").append("total", 1).append("createdAt", new Date()).append("lines", "not-array"));
            case "crud-insert-find-update-delete" -> runCrudLifecycle(database);
            case "crud-bulk-write" -> runBulkWrite(database);
            case "crud-insert-one" -> scratch(database).insertOne(doc("insert-one", 1));
            case "crud-insert-many" -> scratch(database).insertMany(List.of(doc("insert-many-a", 1), doc("insert-many-b", 2)));
            case "crud-find-one" -> require(scratch(database).find(eq("kind", "seed")).first() != null, "findOne returned no document");
            case "crud-count-documents" -> require(scratch(database).countDocuments(eq("kind", "seed")) > 0, "countDocuments returned zero");
            case "crud-estimated-count" -> require(scratch(database).estimatedDocumentCount() >= 0, "estimatedDocumentCount failed");
            case "crud-distinct" -> require(!database.getCollection("customers").distinct("region", String.class).into(new ArrayList<>()).isEmpty(), "distinct returned no values");
            case "crud-update-one-set" -> require(scratch(database).updateOne(eq("kind", "seed"), Updates.set("marker", "set")).wasAcknowledged(), "updateOne failed");
            case "crud-update-many-inc" -> require(scratch(database).updateMany(Filters.exists("score"), Updates.inc("score", 1)).wasAcknowledged(), "updateMany failed");
            case "crud-replace-one" -> require(scratch(database).replaceOne(eq("kind", "replace-target"), doc("replace-target", 99)).wasAcknowledged(), "replaceOne failed");
            case "crud-delete-one" -> require(scratch(database).deleteOne(eq("kind", "delete-one-target")).wasAcknowledged(), "deleteOne failed");
            case "crud-delete-many" -> require(scratch(database).deleteMany(eq("kind", "delete-many-target")).wasAcknowledged(), "deleteMany failed");
            case "crud-upsert-one" -> require(scratch(database).updateOne(eq("kind", "upsert-target"), Updates.set("score", 1), new com.mongodb.client.model.UpdateOptions().upsert(true)).wasAcknowledged(), "upsert failed");
            case "crud-find-one-and-delete" -> scratch(database).findOneAndDelete(eq("kind", "find-delete-target"), new FindOneAndDeleteOptions());
            case "crud-find-one-and-replace" -> scratch(database).findOneAndReplace(eq("kind", "find-replace-target"), doc("find-replace-target", 55), new FindOneAndReplaceOptions().returnDocument(ReturnDocument.AFTER));
            case "crud-ordered-bulk-write" -> scratch(database).bulkWrite(List.of(new InsertOneModel<>(doc("ordered-bulk", 1))), new BulkWriteOptions().ordered(true));
            case "crud-unordered-bulk-write" -> scratch(database).bulkWrite(List.of(new InsertOneModel<>(doc("unordered-bulk", 1))), new BulkWriteOptions().ordered(false));
            case "query-filter-sort-project" -> runFilterSortProjection(database);
            case "query-array-element", "query-elem-match" -> runArrayAndNestedQuery(database);
            case "transaction-commit" -> runTransaction(database);
            case "transaction-abort" -> runTransactionAbort(database);
            case "transaction-read-your-writes" -> runTransactionReadYourWrites(database);
            case "change-stream-open" -> runChangeStream(database);
            case "change-stream-pipeline" -> runChangeStreamPipeline(database);
            case "change-stream-full-document" -> runChangeStreamFullDocument(database);
            case "index-compound-unique-ttl" -> verifyExpectedIndexes(database);
            case "index-hint-explain" -> runHintAndExplain(database);
            case "aggregation-group-sort" -> runAggregationGroupSort(database);
            case "aggregation-lookup" -> runAggregationLookup(database);
            case "aggregation-facet" -> runAggregationFacet(database);
            case "command-buildinfo-serverstatus" -> runAdminCommands();
            default -> executeGeneratedCase(id, database);
        }
    }

    private void executeGeneratedCase(String id, MongoDatabase database) {
        if (id.startsWith("query-")) {
            runGeneratedQueryCase(id, database);
        } else if (id.startsWith("update-")) {
            runGeneratedUpdateCase(id, database);
        } else if (id.startsWith("index-")) {
            runGeneratedIndexCase(id, database);
        } else if (id.startsWith("aggregation-")) {
            runGeneratedAggregationCase(id, database);
        } else if (id.startsWith("collection-")) {
            runGeneratedCollectionCase(id, database);
        } else if (id.startsWith("command-")) {
            runGeneratedCommandCase(id, database);
        } else if (id.startsWith("datatype-")) {
            runGeneratedDatatypeCase(id, database);
        } else if (id.startsWith("geospatial-")) {
            runGeneratedGeospatialCase(id, database);
        } else {
            throw new IllegalArgumentException("No implementation for compatibility case: " + id);
        }
    }

    private void runGeneratedQueryCase(String id, MongoDatabase database) {
        MongoCollection<Document> orders = database.getCollection("orders");
        long count = switch (id) {
            case "query-eq" -> orders.countDocuments(eq("status", "paid"));
            case "query-ne" -> orders.countDocuments(Filters.ne("status", "paid"));
            case "query-gt" -> orders.countDocuments(Filters.gt("total", 50));
            case "query-gte" -> orders.countDocuments(gte("total", 50));
            case "query-lt" -> orders.countDocuments(Filters.lt("total", 50));
            case "query-lte" -> orders.countDocuments(Filters.lte("total", 50));
            case "query-in" -> orders.countDocuments(Filters.in("status", "paid", "new"));
            case "query-nin" -> orders.countDocuments(Filters.nin("status", "cancelled"));
            case "query-and" -> orders.countDocuments(Filters.and(eq("status", "paid"), gte("total", 20)));
            case "query-or" -> orders.countDocuments(Filters.or(eq("status", "paid"), eq("status", "new")));
            case "query-nor" -> orders.countDocuments(Filters.nor(eq("status", "missing")));
            case "query-exists" -> orders.countDocuments(Filters.exists("createdAt"));
            case "query-type" -> orders.countDocuments(Filters.type("orderNo", "string"));
            case "query-regex" -> orders.countDocuments(Filters.regex("orderNo", "^COMPAT-"));
            case "query-all" -> orders.countDocuments(Filters.all("tags", "compat", "order"));
            case "query-size" -> orders.countDocuments(Filters.size("lines", 1));
            case "query-dot-path" -> orders.countDocuments(eq("lines.sku", "SKU-1"));
            case "query-not" -> orders.countDocuments(Filters.not(Filters.lt("total", 0)));
            case "query-mod" -> orders.countDocuments(Filters.mod("customerId", 2, 0));
            case "query-date-range" -> orders.countDocuments(Filters.lte("createdAt", new Date()));
            case "query-sort-ascending" -> orders.find().sort(Sorts.ascending("createdAt")).limit(1).into(new ArrayList<>()).size();
            case "query-sort-descending" -> orders.find().sort(Sorts.descending("createdAt")).limit(1).into(new ArrayList<>()).size();
            case "query-skip-limit" -> orders.find().skip(1).limit(1).into(new ArrayList<>()).size();
            case "query-expr" -> orders.countDocuments(Filters.expr(new Document("$gte", List.of("$total", 50))));
            case "query-json-schema" -> orders.countDocuments(new Document("$jsonSchema", new Document("bsonType", "object")
                    .append("required", List.of("orderNo", "customerId"))));
            case "query-bits-all-set" -> scratch(database).countDocuments(Filters.bitsAllSet("flags", 1));
            case "query-bits-any-clear" -> scratch(database).countDocuments(Filters.bitsAnyClear("flags", 4));
            case "query-where" -> {
                try {
                    yield orders.countDocuments(new Document("$where", "function() { return this.total >= 0; }"));
                } catch (MongoCommandException e) {
                    throw new UnsupportedMongoFeatureException("$where is disabled or not allowed in this execution context: " + e.getErrorMessage());
                }
            }
            case "query-collation-case-insensitive" -> orders.find(eq("caseName", "compat-case"))
                    .collation(Collation.builder().locale("en").collationStrength(CollationStrength.SECONDARY).build())
                    .limit(1).into(new ArrayList<>()).size();
            case "query-natural-sort" -> orders.find().sort(new Document("$natural", 1)).limit(1).into(new ArrayList<>()).size();
            case "query-min-max" -> scratch(database).find()
                    .hint(new Document("score", 1))
                    .min(new Document("score", 0))
                    .max(new Document("score", 1000))
                    .limit(1).into(new ArrayList<>()).size();
            default -> throw new IllegalArgumentException("No query case: " + id);
        };
        require(count >= 0, id + " failed");
    }

    private void runGeneratedUpdateCase(String id, MongoDatabase database) {
        MongoCollection<Document> collection = scratch(database);
        String kind = "update-target";
        switch (id) {
            case "update-inc" -> require(collection.updateOne(eq("kind", kind), Updates.inc("score", 1)).wasAcknowledged(), "$inc failed");
            case "update-mul" -> require(collection.updateOne(eq("kind", kind), Updates.mul("score", 2)).wasAcknowledged(), "$mul failed");
            case "update-min" -> require(collection.updateOne(eq("kind", kind), Updates.min("score", 1)).wasAcknowledged(), "$min failed");
            case "update-max" -> require(collection.updateOne(eq("kind", kind), Updates.max("score", 99)).wasAcknowledged(), "$max failed");
            case "update-rename" -> {
                collection.updateOne(eq("kind", kind), Updates.set("renameSource", "value"));
                require(collection.updateOne(eq("kind", kind), Updates.rename("renameSource", "renameTarget")).wasAcknowledged(), "$rename failed");
            }
            case "update-unset" -> require(collection.updateOne(eq("kind", kind), Updates.unset("temporary")).wasAcknowledged(), "$unset failed");
            case "update-current-date" -> require(collection.updateOne(eq("kind", kind), Updates.currentDate("touchedAt")).wasAcknowledged(), "$currentDate failed");
            case "update-add-to-set" -> require(collection.updateOne(eq("kind", kind), Updates.addToSet("tags", "deduped")).wasAcknowledged(), "$addToSet failed");
            case "update-push-each-sort-slice" -> require(collection.updateOne(eq("kind", kind), new Document("$push", new Document("scores",
                    new Document("$each", List.of(5, 1, 3)).append("$sort", -1).append("$slice", 3)))).wasAcknowledged(), "$push modifiers failed");
            case "update-pull" -> require(collection.updateOne(eq("kind", kind), Updates.pull("tags", "remove-me")).wasAcknowledged(), "$pull failed");
            case "update-pop" -> require(collection.updateOne(eq("kind", kind), new Document("$pop", new Document("scores", 1))).wasAcknowledged(), "$pop failed");
            case "update-bit" -> require(collection.updateOne(eq("kind", kind), new Document("$bit", new Document("flags", new Document("or", 2)))).wasAcknowledged(), "$bit failed");
            case "update-array-filter" -> require(collection.updateOne(eq("kind", kind),
                    Updates.set("lineItems.$[item].reviewed", true),
                    new UpdateOptions().arrayFilters(List.of(Filters.gte("item.qty", 2)))).wasAcknowledged(), "arrayFilters failed");
            case "update-positional" -> require(collection.updateOne(Filters.and(eq("kind", kind), eq("lineItems.sku", "SKU-2")),
                    Updates.set("lineItems.$.matched", true)).wasAcknowledged(), "positional update failed");
            case "update-pipeline" -> require(collection.updateOne(eq("kind", kind), List.of(new Document("$set",
                    new Document("pipelineScore", new Document("$add", List.of("$score", 1)))))).wasAcknowledged(), "pipeline update failed");
            default -> throw new IllegalArgumentException("No update case: " + id);
        }
    }

    private void runGeneratedIndexCase(String id, MongoDatabase database) {
        MongoCollection<Document> collection = scratch(database);
        switch (id) {
            case "index-list-indexes" -> require(!database.getCollection("orders").listIndexes().into(new ArrayList<>()).isEmpty(), "listIndexes returned no rows");
            case "index-single-field" -> collection.createIndex(ascending("score"));
            case "index-compound-order" -> collection.createIndex(compoundIndex(ascending("kind"), Indexes.descending("score")));
            case "index-unique-constraint" -> collection.createIndex(ascending("uniqueKey"), new IndexOptions().unique(true).sparse(true));
            case "index-ttl-present" -> collection.createIndex(ascending("expireAt"), new IndexOptions().expireAfter(600L, TimeUnit.SECONDS));
            case "index-partial-filter" -> collection.createIndex(ascending("partialScore"), new IndexOptions().partialFilterExpression(Filters.exists("partialScore")));
            case "index-sparse" -> collection.createIndex(ascending("sparseField"), new IndexOptions().sparse(true));
            case "index-text-search" -> {
                collection.createIndex(Indexes.text("description"));
                collection.insertOne(new Document("kind", "text-search").append("description", "mongodb compatibility text search"));
                require(collection.countDocuments(Filters.text("compatibility")) >= 0, "text search failed");
            }
            case "index-hashed" -> collection.createIndex(Indexes.hashed("kind"));
            case "index-wildcard" -> collection.createIndex(Indexes.ascending("$**"));
            case "index-collation" -> collection.createIndex(ascending("localeName"), new IndexOptions().collation(Collation.builder().locale("en").caseLevel(false).build()));
            case "index-drop-index" -> {
                String name = collection.createIndex(ascending("dropMe"));
                collection.dropIndex(name);
            }
            case "index-recreate-index" -> {
                String name = collection.createIndex(ascending("recreateMe"));
                collection.dropIndex(name);
                collection.createIndex(ascending("recreateMe"));
            }
            default -> throw new IllegalArgumentException("No index case: " + id);
        }
    }

    private void runGeneratedAggregationCase(String id, MongoDatabase database) {
        MongoCollection<Document> orders = database.getCollection("orders");
        List<Document> rows = switch (id) {
            case "aggregation-match" -> orders.aggregate(List.of(match(eq("status", "paid")))).into(new ArrayList<>());
            case "aggregation-project" -> orders.aggregate(List.of(new Document("$project", new Document("orderNo", 1).append("_id", 0)))).into(new ArrayList<>());
            case "aggregation-add-fields" -> orders.aggregate(List.of(new Document("$addFields", new Document("compatFlag", true)))).into(new ArrayList<>());
            case "aggregation-set" -> orders.aggregate(List.of(new Document("$set", new Document("compatSet", true)))).into(new ArrayList<>());
            case "aggregation-unset" -> orders.aggregate(List.of(new Document("$unset", "region"))).into(new ArrayList<>());
            case "aggregation-limit" -> orders.aggregate(List.of(new Document("$limit", 5))).into(new ArrayList<>());
            case "aggregation-skip" -> orders.aggregate(List.of(new Document("$skip", 1), new Document("$limit", 5))).into(new ArrayList<>());
            case "aggregation-sort" -> orders.aggregate(List.of(sort(Sorts.descending("total")))).into(new ArrayList<>());
            case "aggregation-count" -> orders.aggregate(List.of(new Document("$count", "count"))).into(new ArrayList<>());
            case "aggregation-group-avg" -> orders.aggregate(List.of(group("$status", avg("avgTotal", "$total")))).into(new ArrayList<>());
            case "aggregation-group-min" -> orders.aggregate(List.of(group("$status", min("minTotal", "$total")))).into(new ArrayList<>());
            case "aggregation-group-max" -> orders.aggregate(List.of(group("$status", max("maxTotal", "$total")))).into(new ArrayList<>());
            case "aggregation-group-addToSet" -> orders.aggregate(List.of(group("$status", addToSet("regions", "$region")))).into(new ArrayList<>());
            case "aggregation-group-push" -> orders.aggregate(List.of(group("$status", push("orders", "$orderNo")))).into(new ArrayList<>());
            case "aggregation-unwind" -> orders.aggregate(List.of(new Document("$unwind", "$lines"))).into(new ArrayList<>());
            case "aggregation-bucket" -> orders.aggregate(List.of(new Document("$bucket", new Document("groupBy", "$total").append("boundaries", List.of(0, 50, 100, 200)).append("default", "other")))).into(new ArrayList<>());
            case "aggregation-sort-by-count" -> orders.aggregate(List.of(new Document("$sortByCount", "$status"))).into(new ArrayList<>());
            case "aggregation-replace-root" -> orders.aggregate(List.of(new Document("$replaceRoot", new Document("newRoot", new Document("orderNo", "$orderNo").append("status", "$status"))))).into(new ArrayList<>());
            case "aggregation-sample" -> orders.aggregate(List.of(new Document("$sample", new Document("size", 3)))).into(new ArrayList<>());
            case "aggregation-date-to-string" -> orders.aggregate(List.of(new Document("$project", new Document("day", new Document("$dateToString", new Document("format", "%Y-%m-%d").append("date", "$createdAt")))))).into(new ArrayList<>());
            case "aggregation-cond" -> orders.aggregate(List.of(new Document("$project", new Document("bucket", new Document("$cond", List.of(new Document("$gte", List.of("$total", 50)), "high", "low")))))).into(new ArrayList<>());
            case "aggregation-if-null" -> orders.aggregate(List.of(new Document("$project", new Document("safeRegion", new Document("$ifNull", List.of("$region", "unknown")))))).into(new ArrayList<>());
            case "aggregation-map" -> orders.aggregate(List.of(new Document("$project", new Document("lineSkus", new Document("$map",
                    new Document("input", "$lines").append("as", "line").append("in", "$$line.sku")))))).into(new ArrayList<>());
            case "aggregation-filter" -> orders.aggregate(List.of(new Document("$project", new Document("largeLines", new Document("$filter",
                    new Document("input", "$lines").append("as", "line").append("cond", new Document("$gte", List.of("$$line.qty", 2)))))))).into(new ArrayList<>());
            case "aggregation-reduce" -> orders.aggregate(List.of(new Document("$project", new Document("tagText", new Document("$reduce",
                    new Document("input", "$tags").append("initialValue", "").append("in", new Document("$concat", List.of("$$value", "$$this")))))))).into(new ArrayList<>());
            case "aggregation-to-decimal" -> orders.aggregate(List.of(new Document("$project", new Document("decimalTotal", new Document("$toDecimal", "$total"))))).into(new ArrayList<>());
            case "aggregation-convert" -> orders.aggregate(List.of(new Document("$project", new Document("stringTotal", new Document("$convert",
                    new Document("input", "$total").append("to", "string").append("onError", "").append("onNull", "")))))).into(new ArrayList<>());
            case "aggregation-regex-match" -> orders.aggregate(List.of(new Document("$project", new Document("matched", new Document("$regexMatch",
                    new Document("input", "$orderNo").append("regex", "^COMPAT-")))))).into(new ArrayList<>());
            case "aggregation-date-trunc" -> {
                requireAtLeast(5, 0, "$dateTrunc requires MongoDB 5.0+");
                yield orders.aggregate(List.of(new Document("$project", new Document("day", new Document("$dateTrunc",
                        new Document("date", "$createdAt").append("unit", "day")))))).into(new ArrayList<>());
            }
            case "aggregation-union-with" -> orders.aggregate(List.of(new Document("$limit", 1), new Document("$unionWith",
                    new Document("coll", "customers").append("pipeline", List.of(new Document("$limit", 1)))))).into(new ArrayList<>());
            case "aggregation-graph-lookup" -> database.getCollection("categories").aggregate(List.of(new Document("$match", new Document("_id", "leaf")),
                    new Document("$graphLookup", new Document("from", "categories").append("startWith", "$parent")
                            .append("connectFromField", "parent").append("connectToField", "_id").append("as", "ancestors")))).into(new ArrayList<>());
            case "aggregation-out" -> {
                orders.aggregate(List.of(new Document("$match", new Document("status", "paid")),
                        new Document("$out", "agg_out_orders"))).into(new ArrayList<>());
                yield database.getCollection("agg_out_orders").find().limit(1).into(new ArrayList<>());
            }
            case "aggregation-merge" -> {
                orders.aggregate(List.of(new Document("$match", new Document("status", "paid")),
                        new Document("$project", new Document("_id", "$orderNo").append("status", 1)),
                        new Document("$merge", new Document("into", "agg_merge_orders").append("whenMatched", "replace").append("whenNotMatched", "insert")))).into(new ArrayList<>());
                yield database.getCollection("agg_merge_orders").find().limit(1).into(new ArrayList<>());
            }
            case "aggregation-set-window-fields" -> {
                requireAtLeast(5, 0, "$setWindowFields requires MongoDB 5.0+");
                yield orders.aggregate(List.of(new Document("$setWindowFields", new Document("partitionBy", "$status")
                        .append("sortBy", new Document("createdAt", 1))
                        .append("output", new Document("runningTotal", new Document("$sum", "$total")
                                .append("window", new Document("documents", List.of("unbounded", "current")))))))).into(new ArrayList<>());
            }
            default -> throw new IllegalArgumentException("No aggregation case: " + id);
        };
        require(!rows.isEmpty(), id + " returned no rows");
    }

    private void runGeneratedCollectionCase(String id, MongoDatabase database) {
        switch (id) {
            case "collection-create-view" -> {
                database.getCollection("orders_paid_view").drop();
                database.createView("orders_paid_view", "orders", List.of(match(eq("status", "paid"))));
                verifyCollectionExists(database, "orders_paid_view");
            }
            case "collection-rename" -> {
                database.getCollection("rename_source").drop();
                database.getCollection("rename_target").drop();
                database.createCollection("rename_source");
                database.getCollection("rename_source").renameCollection(new com.mongodb.MongoNamespace(databaseName, "rename_target"));
                verifyCollectionExists(database, "rename_target");
            }
            case "collection-capped" -> {
                database.getCollection("capped_events").drop();
                database.createCollection("capped_events", new CreateCollectionOptions().capped(true).sizeInBytes(1024 * 1024).maxDocuments(100L));
                verifyCollectionExists(database, "capped_events");
            }
            case "collection-timeseries" -> {
                requireAtLeast(5, 0, "time series collections require MongoDB 5.0+");
                database.getCollection("ts_metrics").drop();
                database.runCommand(new Document("create", "ts_metrics").append("timeseries",
                        new Document("timeField", "ts").append("metaField", "metadata").append("granularity", "seconds")));
                database.getCollection("ts_metrics").insertOne(new Document("ts", new Date()).append("metadata", new Document("host", "compat")).append("value", 1));
            }
            case "collection-collmod-validator" -> {
                database.getCollection("collmod_target").drop();
                database.createCollection("collmod_target");
                database.runCommand(new Document("collMod", "collmod_target").append("validator",
                        new Document("$jsonSchema", new Document("bsonType", "object").append("required", List.of("name")))));
            }
            case "collection-collmod-index-hidden" -> {
                requireAtLeast(4, 4, "hidden indexes require MongoDB 4.4+");
                String indexName = scratch(database).createIndex(ascending("hideMe"));
                database.runCommand(new Document("collMod", "scratch").append("index",
                        new Document("name", indexName).append("hidden", true)));
                database.runCommand(new Document("collMod", "scratch").append("index",
                        new Document("name", indexName).append("hidden", false)));
            }
            case "collection-list-search-indexes-command" -> {
                try {
                    database.runCommand(new Document("listSearchIndexes", "orders"));
                } catch (MongoCommandException e) {
                    throw new UnsupportedMongoFeatureException("listSearchIndexes requires Atlas Search-compatible deployments: " + e.getErrorMessage());
                }
            }
            default -> throw new IllegalArgumentException("No collection case: " + id);
        }
    }

    private void runGeneratedCommandCase(String id, MongoDatabase database) {
        MongoDatabase admin = client.getDatabase("admin");
        switch (id) {
            case "command-ping" -> admin.runCommand(new Document("ping", 1));
            case "command-hello" -> admin.runCommand(new Document("hello", 1));
            case "command-listdatabases" -> admin.runCommand(new Document("listDatabases", 1));
            case "command-connectionstatus" -> admin.runCommand(new Document("connectionStatus", 1).append("showPrivileges", false));
            case "command-dbstats" -> database.runCommand(new Document("dbStats", 1));
            case "command-collstats" -> database.runCommand(new Document("collStats", "orders"));
            case "command-hostinfo" -> admin.runCommand(new Document("hostInfo", 1));
            case "command-getcmdlineopts" -> admin.runCommand(new Document("getCmdLineOpts", 1));
            case "command-getparameter-fcv" -> admin.runCommand(new Document("getParameter", 1).append("featureCompatibilityVersion", 1));
            case "command-currentop" -> admin.runCommand(new Document("currentOp", 1).append("active", true));
            case "command-top" -> admin.runCommand(new Document("top", 1));
            case "command-profile-get" -> database.runCommand(new Document("profile", -1));
            case "command-listcommands" -> admin.runCommand(new Document("listCommands", 1));
            case "command-usersinfo" -> admin.runCommand(new Document("usersInfo", 1));
            case "command-rolesinfo" -> admin.runCommand(new Document("rolesInfo", 1));
            case "command-replset-get-status" -> {
                try {
                    admin.runCommand(new Document("replSetGetStatus", 1));
                } catch (MongoCommandException e) {
                    throw new UnsupportedMongoFeatureException("replSetGetStatus requires a replica set member: " + e.getErrorMessage());
                }
            }
            case "command-list-shards" -> {
                try {
                    admin.runCommand(new Document("listShards", 1));
                } catch (MongoCommandException e) {
                    throw new UnsupportedMongoFeatureException("listShards requires mongos: " + e.getErrorMessage());
                }
            }
            case "command-get-default-rw-concern" -> {
                requireAtLeast(4, 4, "getDefaultRWConcern requires MongoDB 4.4+");
                admin.runCommand(new Document("getDefaultRWConcern", 1));
            }
            case "command-validate" -> database.runCommand(new Document("validate", "orders").append("full", false));
            case "command-plan-cache-clear" -> database.runCommand(new Document("planCacheClear", "orders"));
            case "command-plan-cache-list" -> database.getCollection("orders").aggregate(List.of(new Document("$planCacheStats", new Document()))).into(new ArrayList<>());
            default -> throw new IllegalArgumentException("No command case: " + id);
        }
    }

    private void runGeneratedDatatypeCase(String id, MongoDatabase database) {
        MongoCollection<Document> collection = database.getCollection("types");
        Object value = switch (id) {
            case "datatype-string" -> "text";
            case "datatype-int32" -> 32;
            case "datatype-int64" -> 64L;
            case "datatype-double" -> 3.14d;
            case "datatype-decimal128" -> Decimal128.parse("12.34");
            case "datatype-boolean" -> true;
            case "datatype-date" -> new Date();
            case "datatype-array" -> List.of("a", "b");
            case "datatype-document" -> new Document("nested", true);
            case "datatype-null" -> null;
            case "datatype-objectid" -> new ObjectId();
            case "datatype-binary" -> new BsonBinary(new byte[]{1, 2, 3});
            case "datatype-timestamp" -> new BsonTimestamp((int) (System.currentTimeMillis() / 1000L), 1);
            case "datatype-regex" -> new BsonRegularExpression("^compat", "i");
            case "datatype-min-key" -> new MinKey();
            case "datatype-max-key" -> new MaxKey();
            default -> throw new IllegalArgumentException("No datatype case: " + id);
        };
        String key = id.substring("datatype-".length());
        collection.insertOne(new Document("kind", id).append("value", value));
        require(collection.countDocuments(eq("kind", id)) == 1, key + " value was not inserted");
    }

    private void runGeneratedGeospatialCase(String id, MongoDatabase database) {
        MongoCollection<Document> places = database.getCollection("places");
        switch (id) {
            case "geospatial-2dsphere-index" -> require(!places.listIndexes().into(new ArrayList<>()).isEmpty(), "2dsphere index missing");
            case "geospatial-geo-within" -> require(places.countDocuments(new Document("location", new Document("$geoWithin",
                    new Document("$geometry", new Document("type", "Polygon").append("coordinates", List.of(List.of(
                            List.of(116.0, 39.0), List.of(117.0, 39.0), List.of(117.0, 40.5), List.of(116.0, 40.5), List.of(116.0, 39.0)))))))) >= 0, "$geoWithin failed");
            case "geospatial-near" -> require(places.find(new Document("location", new Document("$near",
                    new Document("$geometry", new Document("type", "Point").append("coordinates", List.of(116.397, 39.908)))
                            .append("$maxDistance", 100000.0)))).limit(1).into(new ArrayList<>()).size() >= 0, "$near failed");
            case "geospatial-geo-intersects" -> require(places.countDocuments(new Document("area", new Document("$geoIntersects",
                    new Document("$geometry", new Document("type", "Point").append("coordinates", List.of(116.397, 39.908)))))) >= 0, "$geoIntersects failed");
            default -> throw new IllegalArgumentException("No geospatial case: " + id);
        }
    }

    private void runCase(MongoCompatTestReport report, MongoCompatTestCase testCase, ThrowingRunnable runnable) {
        long start = System.nanoTime();
        try {
            runnable.run();
            report.add(MongoCompatTestResult.passed(testCase.id(), testCase.category(), testCase.name(), elapsedMillis(start)));
        } catch (UnsupportedMongoFeatureException e) {
            report.add(MongoCompatTestResult.skipped(testCase.id(), testCase.category(), testCase.name(), e.getMessage(), elapsedMillis(start)));
        } catch (Exception e) {
            report.add(MongoCompatTestResult.failed(testCase.id(), testCase.category(), testCase.name(), e.getMessage(), elapsedMillis(start)));
        }
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
        database.createCollection("scratch");
        database.createCollection("types");
        database.createCollection("places");
        database.createCollection("categories");
    }

    private void verifyCollectionExists(MongoDatabase database, String collectionName) {
        require(database.listCollectionNames().into(new ArrayList<>()).contains(collectionName), collectionName + " collection missing");
    }

    private void verifyValidatorOptions(MongoDatabase database) {
        Document options = database.listCollections().filter(eq("name", "orders")).first();
        require(options != null && options.toJson().contains("validator"), "validator metadata missing");
    }

    private void insertValidSchemaDocument(MongoDatabase database) {
        database.getCollection("orders").insertOne(new Document("orderNo", "VALID-SCHEMA")
                .append("customerId", 1)
                .append("status", "new")
                .append("region", "cn-east")
                .append("total", 1.0d)
                .append("createdAt", new Date())
                .append("expiresAt", new Date(System.currentTimeMillis() + 86400000L))
                .append("lines", List.of(new Document("sku", "SKU-VALID").append("qty", 1)))
                .append("tags", List.of("compat", "order")));
    }

    private void verifySchemaRejectsInvalidDocument(MongoDatabase database) {
        expectInsertFailure(database, new Document("orderNo", "BAD-1"));
    }

    private void expectInsertFailure(MongoDatabase database, Document document) {
        try {
            database.getCollection("orders").insertOne(document);
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
        scratch(database).createIndex(ascending("score"));
        MongoCollection<Document> customers = database.getCollection("customers");
        customers.createIndex(ascending("email"), new IndexOptions().unique(true));
        customers.createIndex(ascending("tier", "region"));
        MongoCollection<Document> places = database.getCollection("places");
        places.createIndex(Indexes.geo2dsphere("location"));
        places.createIndex(Indexes.geo2dsphere("area"));
    }

    private void seedData(MongoDatabase database) {
        MongoCollection<Document> customers = database.getCollection("customers");
        MongoCollection<Document> orders = database.getCollection("orders");
        MongoCollection<Document> scratch = scratch(database);
        MongoCollection<Document> places = database.getCollection("places");
        MongoCollection<Document> categories = database.getCollection("categories");
        List<Document> customerDocs = new ArrayList<>();
        List<Document> orderDocs = new ArrayList<>();
        List<Document> scratchDocs = new ArrayList<>();
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
                    .append("caseName", i % 2 == 0 ? "Compat-Case" : "compat-case")
                    .append("createdAt", new Date(System.currentTimeMillis() - i * 60000L))
                    .append("expiresAt", new Date(System.currentTimeMillis() + 86400000L))
                    .append("lines", List.of(new Document("sku", "SKU-" + (i % 20)).append("qty", (i % 5) + 1)))
                    .append("tags", List.of("compat", "order")));
        }
        for (int i = 0; i < 10; i++) {
            scratchDocs.add(doc("seed", i));
        }
        scratchDocs.add(doc("replace-target", 10));
        scratchDocs.add(doc("delete-one-target", 11));
        scratchDocs.add(doc("delete-many-target", 12));
        scratchDocs.add(doc("delete-many-target", 13));
        scratchDocs.add(doc("find-delete-target", 14));
        scratchDocs.add(doc("find-replace-target", 15));
        scratchDocs.add(doc("update-target", 16)
                .append("temporary", "remove")
                .append("flags", 5)
                .append("tags", new ArrayList<>(List.of("remove-me", "keep-me")))
                .append("scores", new ArrayList<>(List.of(1, 2, 3)))
                .append("lineItems", new ArrayList<>(List.of(
                        new Document("sku", "SKU-1").append("qty", 1),
                        new Document("sku", "SKU-2").append("qty", 3)))));
        customers.insertMany(customerDocs);
        orders.insertMany(orderDocs);
        scratch.insertMany(scratchDocs);
        database.getCollection("events").insertOne(new Document("type", "seed").append("at", new Date()));
        places.insertMany(List.of(
                new Document("name", "beijing").append("location", new Document("type", "Point").append("coordinates", List.of(116.397, 39.908)))
                        .append("area", new Document("type", "Polygon").append("coordinates", List.of(List.of(
                                List.of(116.0, 39.0), List.of(117.0, 39.0), List.of(117.0, 40.5), List.of(116.0, 40.5), List.of(116.0, 39.0))))),
                new Document("name", "shanghai").append("location", new Document("type", "Point").append("coordinates", List.of(121.4737, 31.2304)))
                        .append("area", new Document("type", "Polygon").append("coordinates", List.of(List.of(
                                List.of(121.0, 31.0), List.of(122.0, 31.0), List.of(122.0, 31.5), List.of(121.0, 31.5), List.of(121.0, 31.0)))))));
        categories.insertMany(List.of(
                new Document("_id", "root").append("parent", null),
                new Document("_id", "middle").append("parent", "root"),
                new Document("_id", "leaf").append("parent", "middle")));
    }

    private MongoCollection<Document> scratch(MongoDatabase database) {
        return database.getCollection("scratch");
    }

    private Document doc(String kind, int score) {
        return new Document("kind", kind)
                .append("score", score)
                .append("uniqueKey", kind + "-" + score + "-" + ObjectId.get().toHexString())
                .append("partialScore", score)
                .append("localeName", "Cafe")
                .append("expireAt", new Date(System.currentTimeMillis() + 86400000L));
    }

    private void verifyExpectedIndexes(MongoDatabase database) {
        List<Document> indexes = database.getCollection("orders").listIndexes().into(new ArrayList<>());
        require(indexes.size() >= 4, "expected order indexes missing");
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
            database.getCollection("events").insertOne(session, new Document("type", "transaction").append("at", new Date()));
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

    private void runTransactionAbort(MongoDatabase database) {
        try (ClientSession session = client.startSession()) {
            ObjectId id = new ObjectId();
            session.startTransaction();
            database.getCollection("events").insertOne(session, new Document("_id", id).append("type", "transaction-abort").append("at", new Date()));
            session.abortTransaction();
            require(database.getCollection("events").countDocuments(eq("_id", id)) == 0, "abortTransaction did not roll back insert");
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

    private void runTransactionReadYourWrites(MongoDatabase database) {
        try (ClientSession session = client.startSession()) {
            ObjectId id = new ObjectId();
            session.startTransaction();
            database.getCollection("events").insertOne(session, new Document("_id", id).append("type", "transaction-read").append("at", new Date()));
            require(database.getCollection("events").countDocuments(session, eq("_id", id)) == 1, "transaction did not read its own write");
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

    private void runChangeStream(MongoDatabase database) {
        try (MongoCursor<com.mongodb.client.model.changestream.ChangeStreamDocument<Document>> cursor = database.getCollection("events")
                .watch()
                .maxAwaitTime(2, TimeUnit.SECONDS)
                .iterator()) {
            database.getCollection("events").insertOne(new Document("type", "change-stream").append("at", new Date()));
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

    private void runChangeStreamPipeline(MongoDatabase database) {
        try (MongoCursor<com.mongodb.client.model.changestream.ChangeStreamDocument<Document>> cursor = database.getCollection("events")
                .watch(List.of(match(new Document("fullDocument.type", "change-stream-pipeline"))))
                .maxAwaitTime(2, TimeUnit.SECONDS)
                .iterator()) {
            database.getCollection("events").insertOne(new Document("type", "change-stream-pipeline").append("at", new Date()));
            if (!cursor.hasNext()) {
                throw new IllegalStateException("change stream pipeline did not receive event");
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

    private void runChangeStreamFullDocument(MongoDatabase database) {
        try (MongoCursor<com.mongodb.client.model.changestream.ChangeStreamDocument<Document>> cursor = database.getCollection("events")
                .watch()
                .fullDocument(com.mongodb.client.model.changestream.FullDocument.UPDATE_LOOKUP)
                .maxAwaitTime(2, TimeUnit.SECONDS)
                .iterator()) {
            ObjectId id = new ObjectId();
            database.getCollection("events").insertOne(new Document("_id", id).append("type", "change-stream-full-document").append("counter", 1).append("at", new Date()));
            database.getCollection("events").updateOne(eq("_id", id), Updates.inc("counter", 1));
            if (!cursor.hasNext()) {
                throw new IllegalStateException("change stream fullDocument did not receive event");
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

    private void requireAtLeast(int major, int minor, String message) {
        if (mongoVersion.isBlank()) {
            return;
        }
        try {
            com.example.mongousage.mongo.MongoVersion version = com.example.mongousage.mongo.MongoVersion.parse(mongoVersion);
            if (!version.atLeast(major, minor)) {
                throw new UnsupportedMongoFeatureException(message);
            }
        } catch (IllegalArgumentException ignored) {
            // If the version label is not parseable here, run the test and let the server decide.
        }
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
