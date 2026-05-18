package com.example.mongousage.compat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class MongoCompatTestCaseReference {
    private static final Map<String, Reference> REFERENCES = parseReferences();

    private MongoCompatTestCaseReference() {
    }

    public static Optional<Reference> find(String id) {
        return Optional.ofNullable(REFERENCES.get(id));
    }

    public static Map<String, Reference> all() {
        return REFERENCES;
    }

    private static Map<String, Reference> parseReferences() {
        Map<String, Reference> references = new LinkedHashMap<>();
        for (String line : RAW_REFERENCES.strip().split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\t", 3);
            references.put(parts[1], new Reference(Integer.parseInt(parts[0]), parts[2]));
        }
        return Map.copyOf(references);
    }

    public record Reference(int number, String mongoshCommand) {
        public String formattedNumber() {
            return "%03d".formatted(number);
        }
    }

    private static final String RAW_REFERENCES = """
            1	schema-json-validator	db.createCollection("orders", { validator: { $jsonSchema: { bsonType: "object", required: ["orderNo","customerId","status","total","createdAt","lines"] } } })
            2	schema-validation-reject	db.orders.insertOne({ orderNo: "BAD-1" })
            3	schema-list-collections	db.getCollectionNames()
            4	schema-validator-options-readable	db.getCollectionInfos({ name: "orders" })
            5	schema-valid-document-accepted	db.orders.insertOne({ orderNo: "VALID-SCHEMA", customerId: 1, status: "new", total: 1.0, createdAt: new Date(), lines: [{ sku: "SKU-VALID", qty: 1 }] })
            6	schema-missing-required-field-rejected	db.orders.insertOne({ orderNo: "BAD-1" })
            7	schema-invalid-enum-rejected	db.orders.insertOne({ orderNo: "BAD-ENUM", customerId: 1, status: "bad", total: 1, createdAt: new Date(), lines: [] })
            8	schema-invalid-array-type-rejected	db.orders.insertOne({ orderNo: "BAD-ARRAY", customerId: 1, status: "new", total: 1, createdAt: new Date(), lines: "not-array" })
            9	crud-insert-find-update-delete	db.customers.insertOne(...); db.customers.findOne(...); db.customers.updateOne(...); db.customers.findOneAndUpdate(...); db.customers.deleteOne(...)
            10	crud-bulk-write	db.customers.bulkWrite([{ insertOne: {...} }, { updateOne: {...} }, { updateOne: { upsert: true, ... } }])
            11	crud-insert-one	db.scratch.insertOne({ kind: "insert-one", score: 1 })
            12	crud-insert-many	db.scratch.insertMany([{ kind: "insert-many-a" }, { kind: "insert-many-b" }])
            13	crud-find-one	db.scratch.findOne({ kind: "seed" })
            14	crud-count-documents	db.scratch.countDocuments({ kind: "seed" })
            15	crud-estimated-count	db.scratch.estimatedDocumentCount()
            16	crud-distinct	db.customers.distinct("region")
            17	crud-update-one-set	db.scratch.updateOne({ kind: "seed" }, { $set: { marker: "set" } })
            18	crud-update-many-inc	db.scratch.updateMany({ score: { $exists: true } }, { $inc: { score: 1 } })
            19	crud-replace-one	db.scratch.replaceOne({ kind: "replace-target" }, { kind: "replace-target", score: 99 })
            20	crud-delete-one	db.scratch.deleteOne({ kind: "delete-one-target" })
            21	crud-delete-many	db.scratch.deleteMany({ kind: "delete-many-target" })
            22	crud-upsert-one	db.scratch.updateOne({ kind: "upsert-target" }, { $set: { score: 1 } }, { upsert: true })
            23	crud-find-one-and-delete	db.scratch.findOneAndDelete({ kind: "find-delete-target" })
            24	crud-find-one-and-replace	db.scratch.findOneAndReplace({ kind: "find-replace-target" }, { kind: "find-replace-target", score: 55 })
            25	crud-ordered-bulk-write	db.scratch.bulkWrite([{ insertOne: { document: { kind: "ordered-bulk" } } }], { ordered: true })
            26	crud-unordered-bulk-write	db.scratch.bulkWrite([{ insertOne: { document: { kind: "unordered-bulk" } } }], { ordered: false })
            27	query-filter-sort-project	db.orders.find({ total: { $gte: 30 }, status: "paid" }, { orderNo: 1, total: 1, _id: 0 }).sort({ createdAt: -1 }).skip(1).limit(5)
            28	query-array-element	db.orders.countDocuments({ lines: { $elemMatch: { sku: "SKU-1", qty: { $gte: 1 } } } })
            29	query-eq	db.orders.countDocuments({ status: { $eq: "paid" } })
            30	query-ne	db.orders.countDocuments({ status: { $ne: "paid" } })
            31	query-gt	db.orders.countDocuments({ total: { $gt: 50 } })
            32	query-gte	db.orders.countDocuments({ total: { $gte: 50 } })
            33	query-lt	db.orders.countDocuments({ total: { $lt: 50 } })
            34	query-lte	db.orders.countDocuments({ total: { $lte: 50 } })
            35	query-in	db.orders.countDocuments({ status: { $in: ["paid", "new"] } })
            36	query-nin	db.orders.countDocuments({ status: { $nin: ["cancelled"] } })
            37	query-and	db.orders.countDocuments({ $and: [{ status: "paid" }, { total: { $gte: 20 } }] })
            38	query-or	db.orders.countDocuments({ $or: [{ status: "paid" }, { status: "new" }] })
            39	query-nor	db.orders.countDocuments({ $nor: [{ status: "missing" }] })
            40	query-exists	db.orders.countDocuments({ createdAt: { $exists: true } })
            41	query-type	db.orders.countDocuments({ orderNo: { $type: "string" } })
            42	query-regex	db.orders.countDocuments({ orderNo: { $regex: "^COMPAT-" } })
            43	query-all	db.orders.countDocuments({ tags: { $all: ["compat", "order"] } })
            44	query-size	db.orders.countDocuments({ lines: { $size: 1 } })
            45	query-elem-match	db.orders.countDocuments({ lines: { $elemMatch: { sku: "SKU-1", qty: { $gte: 1 } } } })
            46	query-dot-path	db.orders.countDocuments({ "lines.sku": "SKU-1" })
            47	query-not	db.orders.countDocuments({ total: { $not: { $lt: 0 } } })
            48	query-mod	db.orders.countDocuments({ customerId: { $mod: [2, 0] } })
            49	query-date-range	db.orders.countDocuments({ createdAt: { $lte: new Date() } })
            50	query-sort-ascending	db.orders.find().sort({ createdAt: 1 }).limit(1)
            51	query-sort-descending	db.orders.find().sort({ createdAt: -1 }).limit(1)
            52	query-skip-limit	db.orders.find().skip(1).limit(1)
            53	index-compound-unique-ttl	db.orders.createIndex({ customerId: 1, status: 1, createdAt: -1 }); db.customers.createIndex({ email: 1 }, { unique: true }); db.orders.createIndex({ expiresAt: 1 }, { expireAfterSeconds: 3600 })
            54	index-hint-explain	db.orders.explain().find({ region: "cn-east" }).hint({ region: 1, total: -1 })
            55	index-list-indexes	db.orders.getIndexes()
            56	index-single-field	db.scratch.createIndex({ score: 1 })
            57	index-compound-order	db.scratch.createIndex({ kind: 1, score: -1 })
            58	index-unique-constraint	db.scratch.createIndex({ uniqueKey: 1 }, { unique: true, sparse: true })
            59	index-ttl-present	db.scratch.createIndex({ expireAt: 1 }, { expireAfterSeconds: 600 })
            60	index-partial-filter	db.scratch.createIndex({ partialScore: 1 }, { partialFilterExpression: { partialScore: { $exists: true } } })
            61	index-sparse	db.scratch.createIndex({ sparseField: 1 }, { sparse: true })
            62	index-text-search	db.scratch.createIndex({ description: "text" }); db.scratch.find({ $text: { $search: "compatibility" } })
            63	index-hashed	db.scratch.createIndex({ kind: "hashed" })
            64	index-wildcard	db.scratch.createIndex({ "$**": 1 })
            65	index-collation	db.scratch.createIndex({ localeName: 1 }, { collation: { locale: "en" } })
            66	index-drop-index	db.scratch.createIndex({ dropMe: 1 }); db.scratch.dropIndex("dropMe_1")
            67	index-recreate-index	db.scratch.createIndex({ recreateMe: 1 }); db.scratch.dropIndex("recreateMe_1"); db.scratch.createIndex({ recreateMe: 1 })
            68	aggregation-group-sort	db.orders.aggregate([{ $match: { status: "paid" } }, { $group: { _id: "$region", count: { $sum: 1 }, revenue: { $sum: "$total" } } }, { $sort: { count: -1 } }])
            69	aggregation-lookup	db.orders.aggregate([{ $match: { orderNo: "COMPAT-1" } }, { $lookup: { from: "customers", localField: "customerId", foreignField: "customerId", as: "customer" } }])
            70	aggregation-facet	db.orders.aggregate([{ $facet: { byStatus: [{ $group: { _id: "$status", count: { $sum: 1 } } }], highValue: [{ $match: { total: { $gte: 80 } } }] } }])
            71	aggregation-match	db.orders.aggregate([{ $match: { status: "paid" } }])
            72	aggregation-project	db.orders.aggregate([{ $project: { orderNo: 1, _id: 0 } }])
            73	aggregation-add-fields	db.orders.aggregate([{ $addFields: { compatFlag: true } }])
            74	aggregation-set	db.orders.aggregate([{ $set: { compatSet: true } }])
            75	aggregation-unset	db.orders.aggregate([{ $unset: "region" }])
            76	aggregation-limit	db.orders.aggregate([{ $limit: 5 }])
            77	aggregation-skip	db.orders.aggregate([{ $skip: 1 }, { $limit: 5 }])
            78	aggregation-sort	db.orders.aggregate([{ $sort: { total: -1 } }])
            79	aggregation-count	db.orders.aggregate([{ $count: "count" }])
            80	aggregation-group-avg	db.orders.aggregate([{ $group: { _id: "$status", avgTotal: { $avg: "$total" } } }])
            81	aggregation-group-min	db.orders.aggregate([{ $group: { _id: "$status", minTotal: { $min: "$total" } } }])
            82	aggregation-group-max	db.orders.aggregate([{ $group: { _id: "$status", maxTotal: { $max: "$total" } } }])
            83	aggregation-group-addToSet	db.orders.aggregate([{ $group: { _id: "$status", regions: { $addToSet: "$region" } } }])
            84	aggregation-group-push	db.orders.aggregate([{ $group: { _id: "$status", orders: { $push: "$orderNo" } } }])
            85	aggregation-unwind	db.orders.aggregate([{ $unwind: "$lines" }])
            86	aggregation-bucket	db.orders.aggregate([{ $bucket: { groupBy: "$total", boundaries: [0, 50, 100, 200], default: "other" } }])
            87	aggregation-sort-by-count	db.orders.aggregate([{ $sortByCount: "$status" }])
            88	aggregation-replace-root	db.orders.aggregate([{ $replaceRoot: { newRoot: { orderNo: "$orderNo", status: "$status" } } }])
            89	aggregation-sample	db.orders.aggregate([{ $sample: { size: 3 } }])
            90	aggregation-date-to-string	db.orders.aggregate([{ $project: { day: { $dateToString: { format: "%Y-%m-%d", date: "$createdAt" } } } }])
            91	aggregation-cond	db.orders.aggregate([{ $project: { bucket: { $cond: [{ $gte: ["$total", 50] }, "high", "low"] } } }])
            92	aggregation-if-null	db.orders.aggregate([{ $project: { safeRegion: { $ifNull: ["$region", "unknown"] } } }])
            93	command-buildinfo-serverstatus	db.adminCommand({ buildInfo: 1 }); db.adminCommand({ serverStatus: 1 })
            94	command-ping	db.adminCommand({ ping: 1 })
            95	command-hello	db.adminCommand({ hello: 1 })
            96	command-listdatabases	db.adminCommand({ listDatabases: 1 })
            97	command-connectionstatus	db.adminCommand({ connectionStatus: 1, showPrivileges: false })
            98	command-dbstats	db.runCommand({ dbStats: 1 })
            99	command-collstats	db.runCommand({ collStats: "orders" })
            100	command-hostinfo	db.adminCommand({ hostInfo: 1 })
            101	command-getcmdlineopts	db.adminCommand({ getCmdLineOpts: 1 })
            102	command-getparameter-fcv	db.adminCommand({ getParameter: 1, featureCompatibilityVersion: 1 })
            103	command-currentop	db.adminCommand({ currentOp: 1, active: true })
            104	command-top	db.adminCommand({ top: 1 })
            105	command-profile-get	db.runCommand({ profile: -1 })
            106	command-listcommands	db.adminCommand({ listCommands: 1 })
            107	datatype-string	db.types.insertOne({ kind: "datatype-string", value: "text" })
            108	datatype-int32	db.types.insertOne({ kind: "datatype-int32", value: 32 })
            109	datatype-int64	db.types.insertOne({ kind: "datatype-int64", value: NumberLong("64") })
            110	datatype-double	db.types.insertOne({ kind: "datatype-double", value: 3.14 })
            111	datatype-decimal128	db.types.insertOne({ kind: "datatype-decimal128", value: NumberDecimal("12.34") })
            112	datatype-boolean	db.types.insertOne({ kind: "datatype-boolean", value: true })
            113	datatype-date	db.types.insertOne({ kind: "datatype-date", value: new Date() })
            114	datatype-array	db.types.insertOne({ kind: "datatype-array", value: ["a", "b"] })
            115	datatype-document	db.types.insertOne({ kind: "datatype-document", value: { nested: true } })
            116	datatype-null	db.types.insertOne({ kind: "datatype-null", value: null })
            117	datatype-objectid	db.types.insertOne({ kind: "datatype-objectid", value: ObjectId() })
            118	datatype-binary	db.types.insertOne({ kind: "datatype-binary", value: BinData(0, "AQID") })
            119	transaction-commit	session.startTransaction(); db.events.insertOne(...); db.customers.updateOne(...); session.commitTransaction()
            120	change-stream-open	const cursor = db.events.watch(); db.events.insertOne({ type: "change-stream", at: new Date() }); cursor.hasNext()
            """;
}
