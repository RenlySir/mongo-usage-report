package com.example.mongousage.compat;

import java.util.ArrayList;
import java.util.List;

public final class MongoCompatTestCatalog {
    private static final List<MongoCompatTestCase> CASES = buildCases();

    private MongoCompatTestCatalog() {
    }

    public static List<MongoCompatTestCase> cases() {
        return CASES;
    }

    private static List<MongoCompatTestCase> buildCases() {
        List<MongoCompatTestCase> cases = new ArrayList<>();
        schema(cases);
        crud(cases);
        query(cases);
        index(cases);
        aggregation(cases);
        command(cases);
        datatype(cases);
        add(cases, "transaction-commit", "transaction", "Transaction commit", "Attempts a multi-document transaction when the deployment supports sessions.");
        add(cases, "change-stream-open", "changeStream", "Change stream open", "Attempts to open a change stream when the deployment supports it.");
        return List.copyOf(cases);
    }

    private static void schema(List<MongoCompatTestCase> cases) {
        add(cases, "schema-json-validator", "schema", "JSON schema validation", "Creates a collection with required fields and typed nested documents.");
        add(cases, "schema-validation-reject", "schema", "Validation rejection", "Verifies invalid documents are rejected by the schema validator.");
        add(cases, "schema-list-collections", "schema", "List collections", "Verifies created collections are visible via listCollections.");
        add(cases, "schema-validator-options-readable", "schema", "Validator metadata", "Reads collection options and verifies validator metadata is exposed.");
        add(cases, "schema-valid-document-accepted", "schema", "Valid document accepted", "Inserts a document satisfying the JSON schema validator.");
        add(cases, "schema-missing-required-field-rejected", "schema", "Missing required field rejected", "Verifies required-field validation rejects invalid documents.");
        add(cases, "schema-invalid-enum-rejected", "schema", "Invalid enum rejected", "Verifies enum validation rejects invalid status values.");
        add(cases, "schema-invalid-array-type-rejected", "schema", "Invalid array type rejected", "Verifies array type validation rejects non-array line items.");
    }

    private static void crud(List<MongoCompatTestCase> cases) {
        add(cases, "crud-insert-find-update-delete", "crud", "CRUD lifecycle", "Exercises insertOne, find, updateOne, findOneAndUpdate, and deleteOne.");
        add(cases, "crud-bulk-write", "crud", "Bulk writes", "Exercises mixed insert, update, and upsert bulk writes.");
        add(cases, "crud-insert-one", "crud", "insertOne", "Inserts one scratch document.");
        add(cases, "crud-insert-many", "crud", "insertMany", "Inserts multiple scratch documents.");
        add(cases, "crud-find-one", "crud", "findOne", "Finds a single scratch document.");
        add(cases, "crud-count-documents", "crud", "countDocuments", "Counts matching scratch documents.");
        add(cases, "crud-estimated-count", "crud", "estimatedDocumentCount", "Reads estimated collection count.");
        add(cases, "crud-distinct", "crud", "distinct", "Reads distinct customer regions.");
        add(cases, "crud-update-one-set", "crud", "updateOne $set", "Updates one document with $set.");
        add(cases, "crud-update-many-inc", "crud", "updateMany $inc", "Updates multiple documents with $inc.");
        add(cases, "crud-replace-one", "crud", "replaceOne", "Replaces one scratch document.");
        add(cases, "crud-delete-one", "crud", "deleteOne", "Deletes one scratch document.");
        add(cases, "crud-delete-many", "crud", "deleteMany", "Deletes multiple scratch documents.");
        add(cases, "crud-upsert-one", "crud", "upsert", "Upserts one scratch document.");
        add(cases, "crud-find-one-and-delete", "crud", "findOneAndDelete", "Finds and deletes one document.");
        add(cases, "crud-find-one-and-replace", "crud", "findOneAndReplace", "Finds and replaces one document.");
        add(cases, "crud-ordered-bulk-write", "crud", "Ordered bulkWrite", "Runs ordered bulk write operations.");
        add(cases, "crud-unordered-bulk-write", "crud", "Unordered bulkWrite", "Runs unordered bulk write operations.");
    }

    private static void query(List<MongoCompatTestCase> cases) {
        add(cases, "query-filter-sort-project", "query", "Filter sort projection", "Exercises comparison operators, sorting, projection, skip, and limit.");
        add(cases, "query-array-element", "query", "Array and nested document queries", "Exercises $elemMatch and dot-path predicates.");
        String[][] items = {
                {"query-eq", "$eq"}, {"query-ne", "$ne"}, {"query-gt", "$gt"}, {"query-gte", "$gte"},
                {"query-lt", "$lt"}, {"query-lte", "$lte"}, {"query-in", "$in"}, {"query-nin", "$nin"},
                {"query-and", "$and"}, {"query-or", "$or"}, {"query-nor", "$nor"}, {"query-exists", "$exists"},
                {"query-type", "$type"}, {"query-regex", "$regex"}, {"query-all", "$all"}, {"query-size", "$size"},
                {"query-elem-match", "$elemMatch"}, {"query-dot-path", "dot path"}, {"query-not", "$not"}, {"query-mod", "$mod"},
                {"query-date-range", "date range"}, {"query-sort-ascending", "sort ascending"}, {"query-sort-descending", "sort descending"},
                {"query-skip-limit", "skip and limit"}
        };
        for (String[] item : items) {
            add(cases, item[0], "query", item[1], "Exercises " + item[1] + " query behavior.");
        }
    }

    private static void index(List<MongoCompatTestCase> cases) {
        add(cases, "index-compound-unique-ttl", "index", "Compound, unique, and TTL indexes", "Creates and verifies common index types used in migrations.");
        add(cases, "index-hint-explain", "index", "Hint and explain", "Uses an index hint and explain plan for a covered query.");
        String[][] items = {
                {"index-list-indexes", "listIndexes"}, {"index-single-field", "single-field index"},
                {"index-compound-order", "compound key order"}, {"index-unique-constraint", "unique constraint"},
                {"index-ttl-present", "TTL index"}, {"index-partial-filter", "partialFilterExpression"},
                {"index-sparse", "sparse index"}, {"index-text-search", "text index and $text"},
                {"index-hashed", "hashed index"}, {"index-wildcard", "wildcard index"},
                {"index-collation", "collation index"}, {"index-drop-index", "dropIndex"},
                {"index-recreate-index", "recreate index"}
        };
        for (String[] item : items) {
            add(cases, item[0], "index", item[1], "Exercises " + item[1] + ".");
        }
    }

    private static void aggregation(List<MongoCompatTestCase> cases) {
        add(cases, "aggregation-group-sort", "aggregation", "Aggregation group sort", "Exercises $match, $group, $sort, and computed totals.");
        add(cases, "aggregation-lookup", "aggregation", "Aggregation lookup", "Exercises local $lookup between orders and customers.");
        add(cases, "aggregation-facet", "aggregation", "Aggregation facet", "Exercises $facet with independent result branches.");
        String[] stages = {
                "match", "project", "add-fields", "set", "unset", "limit", "skip", "sort", "count",
                "group-avg", "group-min", "group-max", "group-addToSet", "group-push", "unwind", "bucket",
                "sort-by-count", "replace-root", "sample", "date-to-string", "cond", "if-null"
        };
        for (String stage : stages) {
            add(cases, "aggregation-" + stage, "aggregation", "$" + stage, "Exercises aggregation stage or expression $" + stage + ".");
        }
    }

    private static void command(List<MongoCompatTestCase> cases) {
        add(cases, "command-buildinfo-serverstatus", "command", "Admin commands", "Exercises buildInfo and serverStatus read-only commands.");
        String[] commands = {
                "ping", "hello", "listdatabases", "connectionstatus", "dbstats", "collstats", "hostinfo",
                "getcmdlineopts", "getparameter-fcv", "currentop", "top", "profile-get", "listcommands"
        };
        for (String command : commands) {
            add(cases, "command-" + command, "command", command, "Runs the " + command + " command or equivalent diagnostic command.");
        }
    }

    private static void datatype(List<MongoCompatTestCase> cases) {
        String[] types = {
                "string", "int32", "int64", "double", "decimal128", "boolean", "date", "array", "document", "null", "objectid", "binary"
        };
        for (String type : types) {
            add(cases, "datatype-" + type, "datatype", type, "Inserts and reads a BSON " + type + " value.");
        }
    }

    private static void add(List<MongoCompatTestCase> cases, String id, String category, String name, String description) {
        cases.add(new MongoCompatTestCase(id, category, name, description));
    }
}
