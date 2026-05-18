package com.example.mongousage.compat;

import java.util.List;

public final class MongoCompatTestCatalog {
    private static final List<MongoCompatTestCase> CASES = List.of(
            new MongoCompatTestCase("schema-json-validator", "schema", "JSON schema validation", "Creates a collection with required fields and typed nested documents."),
            new MongoCompatTestCase("schema-validation-reject", "schema", "Validation rejection", "Verifies invalid documents are rejected by the schema validator."),
            new MongoCompatTestCase("crud-insert-find-update-delete", "crud", "CRUD lifecycle", "Exercises insertOne, find, updateOne, findOneAndUpdate, and deleteOne."),
            new MongoCompatTestCase("crud-bulk-write", "crud", "Bulk writes", "Exercises mixed insert, update, and upsert bulk writes."),
            new MongoCompatTestCase("query-filter-sort-project", "query", "Filter sort projection", "Exercises comparison operators, sorting, projection, skip, and limit."),
            new MongoCompatTestCase("query-array-element", "query", "Array and nested document queries", "Exercises $elemMatch and dot-path predicates."),
            new MongoCompatTestCase("index-compound-unique-ttl", "index", "Compound, unique, and TTL indexes", "Creates and verifies common index types used in migrations."),
            new MongoCompatTestCase("index-hint-explain", "index", "Hint and explain", "Uses an index hint and explain plan for a covered query."),
            new MongoCompatTestCase("aggregation-group-sort", "aggregation", "Aggregation group sort", "Exercises $match, $group, $sort, and computed totals."),
            new MongoCompatTestCase("aggregation-lookup", "aggregation", "Aggregation lookup", "Exercises local $lookup between orders and customers."),
            new MongoCompatTestCase("aggregation-facet", "aggregation", "Aggregation facet", "Exercises $facet with independent result branches."),
            new MongoCompatTestCase("transaction-commit", "transaction", "Transaction commit", "Attempts a multi-document transaction when the deployment supports sessions."),
            new MongoCompatTestCase("change-stream-open", "changeStream", "Change stream open", "Attempts to open a change stream when the deployment supports it."),
            new MongoCompatTestCase("command-buildinfo-serverstatus", "command", "Admin commands", "Exercises buildInfo and serverStatus read-only commands.")
    );

    private MongoCompatTestCatalog() {
    }

    public static List<MongoCompatTestCase> cases() {
        return CASES;
    }
}
