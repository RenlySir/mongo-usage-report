# MongoDB Compatibility Test Cases

`compat-test` 命令会先创建临时库、集合、索引和样例数据，然后按下表逐条执行兼容性检查。以下命令以 `use mongo_usage_compat_test` 后的 mongosh 语法表达，部分 Java Driver 调用用等价 MongoDB 命令表示。

| No. | Test ID | Category | MongoDB command or equivalent |
| --- | --- | --- | --- |
| 001 | `schema-json-validator` | schema | `db.createCollection("orders", { validator: { $jsonSchema: { bsonType: "object", required: ["orderNo","customerId","status","total","createdAt","lines"] } } })` |
| 002 | `schema-validation-reject` | schema | `db.orders.insertOne({ orderNo: "BAD-1" })` |
| 003 | `schema-list-collections` | schema | `db.getCollectionNames()` |
| 004 | `schema-validator-options-readable` | schema | `db.getCollectionInfos({ name: "orders" })` |
| 005 | `schema-valid-document-accepted` | schema | `db.orders.insertOne({ orderNo: "VALID-SCHEMA", customerId: 1, status: "new", total: 1.0, createdAt: new Date(), lines: [{ sku: "SKU-VALID", qty: 1 }] })` |
| 006 | `schema-missing-required-field-rejected` | schema | `db.orders.insertOne({ orderNo: "BAD-1" })` |
| 007 | `schema-invalid-enum-rejected` | schema | `db.orders.insertOne({ orderNo: "BAD-ENUM", customerId: 1, status: "bad", total: 1, createdAt: new Date(), lines: [] })` |
| 008 | `schema-invalid-array-type-rejected` | schema | `db.orders.insertOne({ orderNo: "BAD-ARRAY", customerId: 1, status: "new", total: 1, createdAt: new Date(), lines: "not-array" })` |
| 009 | `crud-insert-find-update-delete` | crud | `db.customers.insertOne(...); db.customers.findOne(...); db.customers.updateOne(...); db.customers.findOneAndUpdate(...); db.customers.deleteOne(...)` |
| 010 | `crud-bulk-write` | crud | `db.customers.bulkWrite([{ insertOne: {...} }, { updateOne: {...} }, { updateOne: { upsert: true, ... } }])` |
| 011 | `crud-insert-one` | crud | `db.scratch.insertOne({ kind: "insert-one", score: 1 })` |
| 012 | `crud-insert-many` | crud | `db.scratch.insertMany([{ kind: "insert-many-a" }, { kind: "insert-many-b" }])` |
| 013 | `crud-find-one` | crud | `db.scratch.findOne({ kind: "seed" })` |
| 014 | `crud-count-documents` | crud | `db.scratch.countDocuments({ kind: "seed" })` |
| 015 | `crud-estimated-count` | crud | `db.scratch.estimatedDocumentCount()` |
| 016 | `crud-distinct` | crud | `db.customers.distinct("region")` |
| 017 | `crud-update-one-set` | crud | `db.scratch.updateOne({ kind: "seed" }, { $set: { marker: "set" } })` |
| 018 | `crud-update-many-inc` | crud | `db.scratch.updateMany({ score: { $exists: true } }, { $inc: { score: 1 } })` |
| 019 | `crud-replace-one` | crud | `db.scratch.replaceOne({ kind: "replace-target" }, { kind: "replace-target", score: 99 })` |
| 020 | `crud-delete-one` | crud | `db.scratch.deleteOne({ kind: "delete-one-target" })` |
| 021 | `crud-delete-many` | crud | `db.scratch.deleteMany({ kind: "delete-many-target" })` |
| 022 | `crud-upsert-one` | crud | `db.scratch.updateOne({ kind: "upsert-target" }, { $set: { score: 1 } }, { upsert: true })` |
| 023 | `crud-find-one-and-delete` | crud | `db.scratch.findOneAndDelete({ kind: "find-delete-target" })` |
| 024 | `crud-find-one-and-replace` | crud | `db.scratch.findOneAndReplace({ kind: "find-replace-target" }, { kind: "find-replace-target", score: 55 })` |
| 025 | `crud-ordered-bulk-write` | crud | `db.scratch.bulkWrite([{ insertOne: { document: { kind: "ordered-bulk" } } }], { ordered: true })` |
| 026 | `crud-unordered-bulk-write` | crud | `db.scratch.bulkWrite([{ insertOne: { document: { kind: "unordered-bulk" } } }], { ordered: false })` |
| 027 | `query-filter-sort-project` | query | `db.orders.find({ total: { $gte: 30 }, status: "paid" }, { orderNo: 1, total: 1, _id: 0 }).sort({ createdAt: -1 }).skip(1).limit(5)` |
| 028 | `query-array-element` | query | `db.orders.countDocuments({ lines: { $elemMatch: { sku: "SKU-1", qty: { $gte: 1 } } } })` |
| 029 | `query-eq` | query | `db.orders.countDocuments({ status: { $eq: "paid" } })` |
| 030 | `query-ne` | query | `db.orders.countDocuments({ status: { $ne: "paid" } })` |
| 031 | `query-gt` | query | `db.orders.countDocuments({ total: { $gt: 50 } })` |
| 032 | `query-gte` | query | `db.orders.countDocuments({ total: { $gte: 50 } })` |
| 033 | `query-lt` | query | `db.orders.countDocuments({ total: { $lt: 50 } })` |
| 034 | `query-lte` | query | `db.orders.countDocuments({ total: { $lte: 50 } })` |
| 035 | `query-in` | query | `db.orders.countDocuments({ status: { $in: ["paid", "new"] } })` |
| 036 | `query-nin` | query | `db.orders.countDocuments({ status: { $nin: ["cancelled"] } })` |
| 037 | `query-and` | query | `db.orders.countDocuments({ $and: [{ status: "paid" }, { total: { $gte: 20 } }] })` |
| 038 | `query-or` | query | `db.orders.countDocuments({ $or: [{ status: "paid" }, { status: "new" }] })` |
| 039 | `query-nor` | query | `db.orders.countDocuments({ $nor: [{ status: "missing" }] })` |
| 040 | `query-exists` | query | `db.orders.countDocuments({ createdAt: { $exists: true } })` |
| 041 | `query-type` | query | `db.orders.countDocuments({ orderNo: { $type: "string" } })` |
| 042 | `query-regex` | query | `db.orders.countDocuments({ orderNo: { $regex: "^COMPAT-" } })` |
| 043 | `query-all` | query | `db.orders.countDocuments({ tags: { $all: ["compat", "order"] } })` |
| 044 | `query-size` | query | `db.orders.countDocuments({ lines: { $size: 1 } })` |
| 045 | `query-elem-match` | query | `db.orders.countDocuments({ lines: { $elemMatch: { sku: "SKU-1", qty: { $gte: 1 } } } })` |
| 046 | `query-dot-path` | query | `db.orders.countDocuments({ "lines.sku": "SKU-1" })` |
| 047 | `query-not` | query | `db.orders.countDocuments({ total: { $not: { $lt: 0 } } })` |
| 048 | `query-mod` | query | `db.orders.countDocuments({ customerId: { $mod: [2, 0] } })` |
| 049 | `query-date-range` | query | `db.orders.countDocuments({ createdAt: { $lte: new Date() } })` |
| 050 | `query-sort-ascending` | query | `db.orders.find().sort({ createdAt: 1 }).limit(1)` |
| 051 | `query-sort-descending` | query | `db.orders.find().sort({ createdAt: -1 }).limit(1)` |
| 052 | `query-skip-limit` | query | `db.orders.find().skip(1).limit(1)` |
| 053 | `index-compound-unique-ttl` | index | `db.orders.createIndex({ customerId: 1, status: 1, createdAt: -1 }); db.customers.createIndex({ email: 1 }, { unique: true }); db.orders.createIndex({ expiresAt: 1 }, { expireAfterSeconds: 3600 })` |
| 054 | `index-hint-explain` | index | `db.orders.explain().find({ region: "cn-east" }).hint({ region: 1, total: -1 })` |
| 055 | `index-list-indexes` | index | `db.orders.getIndexes()` |
| 056 | `index-single-field` | index | `db.scratch.createIndex({ score: 1 })` |
| 057 | `index-compound-order` | index | `db.scratch.createIndex({ kind: 1, score: -1 })` |
| 058 | `index-unique-constraint` | index | `db.scratch.createIndex({ uniqueKey: 1 }, { unique: true, sparse: true })` |
| 059 | `index-ttl-present` | index | `db.scratch.createIndex({ expireAt: 1 }, { expireAfterSeconds: 600 })` |
| 060 | `index-partial-filter` | index | `db.scratch.createIndex({ partialScore: 1 }, { partialFilterExpression: { partialScore: { $exists: true } } })` |
| 061 | `index-sparse` | index | `db.scratch.createIndex({ sparseField: 1 }, { sparse: true })` |
| 062 | `index-text-search` | index | `db.scratch.createIndex({ description: "text" }); db.scratch.find({ $text: { $search: "compatibility" } })` |
| 063 | `index-hashed` | index | `db.scratch.createIndex({ kind: "hashed" })` |
| 064 | `index-wildcard` | index | `db.scratch.createIndex({ "$**": 1 })` |
| 065 | `index-collation` | index | `db.scratch.createIndex({ localeName: 1 }, { collation: { locale: "en" } })` |
| 066 | `index-drop-index` | index | `db.scratch.createIndex({ dropMe: 1 }); db.scratch.dropIndex("dropMe_1")` |
| 067 | `index-recreate-index` | index | `db.scratch.createIndex({ recreateMe: 1 }); db.scratch.dropIndex("recreateMe_1"); db.scratch.createIndex({ recreateMe: 1 })` |
| 068 | `aggregation-group-sort` | aggregation | `db.orders.aggregate([{ $match: { status: "paid" } }, { $group: { _id: "$region", count: { $sum: 1 }, revenue: { $sum: "$total" } } }, { $sort: { count: -1 } }])` |
| 069 | `aggregation-lookup` | aggregation | `db.orders.aggregate([{ $match: { orderNo: "COMPAT-1" } }, { $lookup: { from: "customers", localField: "customerId", foreignField: "customerId", as: "customer" } }])` |
| 070 | `aggregation-facet` | aggregation | `db.orders.aggregate([{ $facet: { byStatus: [{ $group: { _id: "$status", count: { $sum: 1 } } }], highValue: [{ $match: { total: { $gte: 80 } } }] } }])` |
| 071 | `aggregation-match` | aggregation | `db.orders.aggregate([{ $match: { status: "paid" } }])` |
| 072 | `aggregation-project` | aggregation | `db.orders.aggregate([{ $project: { orderNo: 1, _id: 0 } }])` |
| 073 | `aggregation-add-fields` | aggregation | `db.orders.aggregate([{ $addFields: { compatFlag: true } }])` |
| 074 | `aggregation-set` | aggregation | `db.orders.aggregate([{ $set: { compatSet: true } }])` |
| 075 | `aggregation-unset` | aggregation | `db.orders.aggregate([{ $unset: "region" }])` |
| 076 | `aggregation-limit` | aggregation | `db.orders.aggregate([{ $limit: 5 }])` |
| 077 | `aggregation-skip` | aggregation | `db.orders.aggregate([{ $skip: 1 }, { $limit: 5 }])` |
| 078 | `aggregation-sort` | aggregation | `db.orders.aggregate([{ $sort: { total: -1 } }])` |
| 079 | `aggregation-count` | aggregation | `db.orders.aggregate([{ $count: "count" }])` |
| 080 | `aggregation-group-avg` | aggregation | `db.orders.aggregate([{ $group: { _id: "$status", avgTotal: { $avg: "$total" } } }])` |
| 081 | `aggregation-group-min` | aggregation | `db.orders.aggregate([{ $group: { _id: "$status", minTotal: { $min: "$total" } } }])` |
| 082 | `aggregation-group-max` | aggregation | `db.orders.aggregate([{ $group: { _id: "$status", maxTotal: { $max: "$total" } } }])` |
| 083 | `aggregation-group-addToSet` | aggregation | `db.orders.aggregate([{ $group: { _id: "$status", regions: { $addToSet: "$region" } } }])` |
| 084 | `aggregation-group-push` | aggregation | `db.orders.aggregate([{ $group: { _id: "$status", orders: { $push: "$orderNo" } } }])` |
| 085 | `aggregation-unwind` | aggregation | `db.orders.aggregate([{ $unwind: "$lines" }])` |
| 086 | `aggregation-bucket` | aggregation | `db.orders.aggregate([{ $bucket: { groupBy: "$total", boundaries: [0, 50, 100, 200], default: "other" } }])` |
| 087 | `aggregation-sort-by-count` | aggregation | `db.orders.aggregate([{ $sortByCount: "$status" }])` |
| 088 | `aggregation-replace-root` | aggregation | `db.orders.aggregate([{ $replaceRoot: { newRoot: { orderNo: "$orderNo", status: "$status" } } }])` |
| 089 | `aggregation-sample` | aggregation | `db.orders.aggregate([{ $sample: { size: 3 } }])` |
| 090 | `aggregation-date-to-string` | aggregation | `db.orders.aggregate([{ $project: { day: { $dateToString: { format: "%Y-%m-%d", date: "$createdAt" } } } }])` |
| 091 | `aggregation-cond` | aggregation | `db.orders.aggregate([{ $project: { bucket: { $cond: [{ $gte: ["$total", 50] }, "high", "low"] } } }])` |
| 092 | `aggregation-if-null` | aggregation | `db.orders.aggregate([{ $project: { safeRegion: { $ifNull: ["$region", "unknown"] } } }])` |
| 093 | `command-buildinfo-serverstatus` | command | `db.adminCommand({ buildInfo: 1 }); db.adminCommand({ serverStatus: 1 })` |
| 094 | `command-ping` | command | `db.adminCommand({ ping: 1 })` |
| 095 | `command-hello` | command | `db.adminCommand({ hello: 1 })` |
| 096 | `command-listdatabases` | command | `db.adminCommand({ listDatabases: 1 })` |
| 097 | `command-connectionstatus` | command | `db.adminCommand({ connectionStatus: 1, showPrivileges: false })` |
| 098 | `command-dbstats` | command | `db.runCommand({ dbStats: 1 })` |
| 099 | `command-collstats` | command | `db.runCommand({ collStats: "orders" })` |
| 100 | `command-hostinfo` | command | `db.adminCommand({ hostInfo: 1 })` |
| 101 | `command-getcmdlineopts` | command | `db.adminCommand({ getCmdLineOpts: 1 })` |
| 102 | `command-getparameter-fcv` | command | `db.adminCommand({ getParameter: 1, featureCompatibilityVersion: 1 })` |
| 103 | `command-currentop` | command | `db.adminCommand({ currentOp: 1, active: true })` |
| 104 | `command-top` | command | `db.adminCommand({ top: 1 })` |
| 105 | `command-profile-get` | command | `db.runCommand({ profile: -1 })` |
| 106 | `command-listcommands` | command | `db.adminCommand({ listCommands: 1 })` |
| 107 | `datatype-string` | datatype | `db.types.insertOne({ kind: "datatype-string", value: "text" })` |
| 108 | `datatype-int32` | datatype | `db.types.insertOne({ kind: "datatype-int32", value: 32 })` |
| 109 | `datatype-int64` | datatype | `db.types.insertOne({ kind: "datatype-int64", value: NumberLong("64") })` |
| 110 | `datatype-double` | datatype | `db.types.insertOne({ kind: "datatype-double", value: 3.14 })` |
| 111 | `datatype-decimal128` | datatype | `db.types.insertOne({ kind: "datatype-decimal128", value: NumberDecimal("12.34") })` |
| 112 | `datatype-boolean` | datatype | `db.types.insertOne({ kind: "datatype-boolean", value: true })` |
| 113 | `datatype-date` | datatype | `db.types.insertOne({ kind: "datatype-date", value: new Date() })` |
| 114 | `datatype-array` | datatype | `db.types.insertOne({ kind: "datatype-array", value: ["a", "b"] })` |
| 115 | `datatype-document` | datatype | `db.types.insertOne({ kind: "datatype-document", value: { nested: true } })` |
| 116 | `datatype-null` | datatype | `db.types.insertOne({ kind: "datatype-null", value: null })` |
| 117 | `datatype-objectid` | datatype | `db.types.insertOne({ kind: "datatype-objectid", value: ObjectId() })` |
| 118 | `datatype-binary` | datatype | `db.types.insertOne({ kind: "datatype-binary", value: BinData(0, "AQID") })` |
| 119 | `transaction-commit` | transaction | `session.startTransaction(); db.events.insertOne(...); db.customers.updateOne(...); session.commitTransaction()` |
| 120 | `change-stream-open` | changeStream | `const cursor = db.events.watch(); db.events.insertOne({ type: "change-stream", at: new Date() }); cursor.hasNext()` |
