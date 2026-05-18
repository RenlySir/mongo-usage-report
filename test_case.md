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
| 053 | `query-expr` | query | `db.orders.countDocuments({ $expr: { $gte: ["$total", 50] } })` |
| 054 | `query-json-schema` | query | `db.orders.countDocuments({ $jsonSchema: { bsonType: "object", required: ["orderNo", "customerId"] } })` |
| 055 | `query-bits-all-set` | query | `db.scratch.countDocuments({ flags: { $bitsAllSet: 1 } })` |
| 056 | `query-bits-any-clear` | query | `db.scratch.countDocuments({ flags: { $bitsAnyClear: 4 } })` |
| 057 | `query-where` | query | `db.orders.countDocuments({ $where: function() { return this.total >= 0; } })` |
| 058 | `query-collation-case-insensitive` | query | `db.orders.find({ caseName: "compat-case" }).collation({ locale: "en", strength: 2 }).limit(1)` |
| 059 | `query-natural-sort` | query | `db.orders.find().sort({ $natural: 1 }).limit(1)` |
| 060 | `query-min-max` | query | `db.scratch.find().hint({ score: 1 }).min({ score: 0 }).max({ score: 1000 }).limit(1)` |
| 061 | `update-inc` | update | `db.scratch.updateOne({ kind: "update-target" }, { $inc: { score: 1 } })` |
| 062 | `update-mul` | update | `db.scratch.updateOne({ kind: "update-target" }, { $mul: { score: 2 } })` |
| 063 | `update-min` | update | `db.scratch.updateOne({ kind: "update-target" }, { $min: { score: 1 } })` |
| 064 | `update-max` | update | `db.scratch.updateOne({ kind: "update-target" }, { $max: { score: 99 } })` |
| 065 | `update-rename` | update | `db.scratch.updateOne({ kind: "update-target" }, { $rename: { renameSource: "renameTarget" } })` |
| 066 | `update-unset` | update | `db.scratch.updateOne({ kind: "update-target" }, { $unset: { temporary: "" } })` |
| 067 | `update-current-date` | update | `db.scratch.updateOne({ kind: "update-target" }, { $currentDate: { touchedAt: true } })` |
| 068 | `update-add-to-set` | update | `db.scratch.updateOne({ kind: "update-target" }, { $addToSet: { tags: "deduped" } })` |
| 069 | `update-push-each-sort-slice` | update | `db.scratch.updateOne({ kind: "update-target" }, { $push: { scores: { $each: [5, 1, 3], $sort: -1, $slice: 3 } } })` |
| 070 | `update-pull` | update | `db.scratch.updateOne({ kind: "update-target" }, { $pull: { tags: "remove-me" } })` |
| 071 | `update-pop` | update | `db.scratch.updateOne({ kind: "update-target" }, { $pop: { scores: 1 } })` |
| 072 | `update-bit` | update | `db.scratch.updateOne({ kind: "update-target" }, { $bit: { flags: { or: 2 } } })` |
| 073 | `update-array-filter` | update | `db.scratch.updateOne({ kind: "update-target" }, { $set: { "lineItems.$[item].reviewed": true } }, { arrayFilters: [{ "item.qty": { $gte: 2 } }] })` |
| 074 | `update-positional` | update | `db.scratch.updateOne({ kind: "update-target", "lineItems.sku": "SKU-2" }, { $set: { "lineItems.$.matched": true } })` |
| 075 | `update-pipeline` | update | `db.scratch.updateOne({ kind: "update-target" }, [{ $set: { pipelineScore: { $add: ["$score", 1] } } }])` |
| 076 | `index-compound-unique-ttl` | index | `db.orders.createIndex({ customerId: 1, status: 1, createdAt: -1 }); db.customers.createIndex({ email: 1 }, { unique: true }); db.orders.createIndex({ expiresAt: 1 }, { expireAfterSeconds: 3600 })` |
| 077 | `index-hint-explain` | index | `db.orders.explain().find({ region: "cn-east" }).hint({ region: 1, total: -1 })` |
| 078 | `index-list-indexes` | index | `db.orders.getIndexes()` |
| 079 | `index-single-field` | index | `db.scratch.createIndex({ score: 1 })` |
| 080 | `index-compound-order` | index | `db.scratch.createIndex({ kind: 1, score: -1 })` |
| 081 | `index-unique-constraint` | index | `db.scratch.createIndex({ uniqueKey: 1 }, { unique: true, sparse: true })` |
| 082 | `index-ttl-present` | index | `db.scratch.createIndex({ expireAt: 1 }, { expireAfterSeconds: 600 })` |
| 083 | `index-partial-filter` | index | `db.scratch.createIndex({ partialScore: 1 }, { partialFilterExpression: { partialScore: { $exists: true } } })` |
| 084 | `index-sparse` | index | `db.scratch.createIndex({ sparseField: 1 }, { sparse: true })` |
| 085 | `index-text-search` | index | `db.scratch.createIndex({ description: "text" }); db.scratch.find({ $text: { $search: "compatibility" } })` |
| 086 | `index-hashed` | index | `db.scratch.createIndex({ kind: "hashed" })` |
| 087 | `index-wildcard` | index | `db.scratch.createIndex({ "$**": 1 })` |
| 088 | `index-collation` | index | `db.scratch.createIndex({ localeName: 1 }, { collation: { locale: "en" } })` |
| 089 | `index-drop-index` | index | `db.scratch.createIndex({ dropMe: 1 }); db.scratch.dropIndex("dropMe_1")` |
| 090 | `index-recreate-index` | index | `db.scratch.createIndex({ recreateMe: 1 }); db.scratch.dropIndex("recreateMe_1"); db.scratch.createIndex({ recreateMe: 1 })` |
| 091 | `aggregation-group-sort` | aggregation | `db.orders.aggregate([{ $match: { status: "paid" } }, { $group: { _id: "$region", count: { $sum: 1 }, revenue: { $sum: "$total" } } }, { $sort: { count: -1 } }])` |
| 092 | `aggregation-lookup` | aggregation | `db.orders.aggregate([{ $match: { orderNo: "COMPAT-1" } }, { $lookup: { from: "customers", localField: "customerId", foreignField: "customerId", as: "customer" } }])` |
| 093 | `aggregation-facet` | aggregation | `db.orders.aggregate([{ $facet: { byStatus: [{ $group: { _id: "$status", count: { $sum: 1 } } }], highValue: [{ $match: { total: { $gte: 80 } } }] } }])` |
| 094 | `aggregation-match` | aggregation | `db.orders.aggregate([{ $match: { status: "paid" } }])` |
| 095 | `aggregation-project` | aggregation | `db.orders.aggregate([{ $project: { orderNo: 1, _id: 0 } }])` |
| 096 | `aggregation-add-fields` | aggregation | `db.orders.aggregate([{ $addFields: { compatFlag: true } }])` |
| 097 | `aggregation-set` | aggregation | `db.orders.aggregate([{ $set: { compatSet: true } }])` |
| 098 | `aggregation-unset` | aggregation | `db.orders.aggregate([{ $unset: "region" }])` |
| 099 | `aggregation-limit` | aggregation | `db.orders.aggregate([{ $limit: 5 }])` |
| 100 | `aggregation-skip` | aggregation | `db.orders.aggregate([{ $skip: 1 }, { $limit: 5 }])` |
| 101 | `aggregation-sort` | aggregation | `db.orders.aggregate([{ $sort: { total: -1 } }])` |
| 102 | `aggregation-count` | aggregation | `db.orders.aggregate([{ $count: "count" }])` |
| 103 | `aggregation-group-avg` | aggregation | `db.orders.aggregate([{ $group: { _id: "$status", avgTotal: { $avg: "$total" } } }])` |
| 104 | `aggregation-group-min` | aggregation | `db.orders.aggregate([{ $group: { _id: "$status", minTotal: { $min: "$total" } } }])` |
| 105 | `aggregation-group-max` | aggregation | `db.orders.aggregate([{ $group: { _id: "$status", maxTotal: { $max: "$total" } } }])` |
| 106 | `aggregation-group-addToSet` | aggregation | `db.orders.aggregate([{ $group: { _id: "$status", regions: { $addToSet: "$region" } } }])` |
| 107 | `aggregation-group-push` | aggregation | `db.orders.aggregate([{ $group: { _id: "$status", orders: { $push: "$orderNo" } } }])` |
| 108 | `aggregation-unwind` | aggregation | `db.orders.aggregate([{ $unwind: "$lines" }])` |
| 109 | `aggregation-bucket` | aggregation | `db.orders.aggregate([{ $bucket: { groupBy: "$total", boundaries: [0, 50, 100, 200], default: "other" } }])` |
| 110 | `aggregation-sort-by-count` | aggregation | `db.orders.aggregate([{ $sortByCount: "$status" }])` |
| 111 | `aggregation-replace-root` | aggregation | `db.orders.aggregate([{ $replaceRoot: { newRoot: { orderNo: "$orderNo", status: "$status" } } }])` |
| 112 | `aggregation-sample` | aggregation | `db.orders.aggregate([{ $sample: { size: 3 } }])` |
| 113 | `aggregation-date-to-string` | aggregation | `db.orders.aggregate([{ $project: { day: { $dateToString: { format: "%Y-%m-%d", date: "$createdAt" } } } }])` |
| 114 | `aggregation-cond` | aggregation | `db.orders.aggregate([{ $project: { bucket: { $cond: [{ $gte: ["$total", 50] }, "high", "low"] } } }])` |
| 115 | `aggregation-if-null` | aggregation | `db.orders.aggregate([{ $project: { safeRegion: { $ifNull: ["$region", "unknown"] } } }])` |
| 116 | `aggregation-map` | aggregation | `db.orders.aggregate([{ $project: { lineSkus: { $map: { input: "$lines", as: "line", in: "$$line.sku" } } } }])` |
| 117 | `aggregation-filter` | aggregation | `db.orders.aggregate([{ $project: { largeLines: { $filter: { input: "$lines", as: "line", cond: { $gte: ["$$line.qty", 2] } } } } }])` |
| 118 | `aggregation-reduce` | aggregation | `db.orders.aggregate([{ $project: { tagText: { $reduce: { input: "$tags", initialValue: "", in: { $concat: ["$$value", "$$this"] } } } } }])` |
| 119 | `aggregation-to-decimal` | aggregation | `db.orders.aggregate([{ $project: { decimalTotal: { $toDecimal: "$total" } } }])` |
| 120 | `aggregation-convert` | aggregation | `db.orders.aggregate([{ $project: { stringTotal: { $convert: { input: "$total", to: "string", onError: "", onNull: "" } } } }])` |
| 121 | `aggregation-regex-match` | aggregation | `db.orders.aggregate([{ $project: { matched: { $regexMatch: { input: "$orderNo", regex: "^COMPAT-" } } } }])` |
| 122 | `aggregation-date-trunc` | aggregation | `db.orders.aggregate([{ $project: { day: { $dateTrunc: { date: "$createdAt", unit: "day" } } } }])` |
| 123 | `aggregation-union-with` | aggregation | `db.orders.aggregate([{ $limit: 1 }, { $unionWith: { coll: "customers", pipeline: [{ $limit: 1 }] } }])` |
| 124 | `aggregation-graph-lookup` | aggregation | `db.categories.aggregate([{ $match: { _id: "leaf" } }, { $graphLookup: { from: "categories", startWith: "$parent", connectFromField: "parent", connectToField: "_id", as: "ancestors" } }])` |
| 125 | `aggregation-out` | aggregation | `db.orders.aggregate([{ $match: { status: "paid" } }, { $out: "agg_out_orders" }])` |
| 126 | `aggregation-merge` | aggregation | `db.orders.aggregate([{ $match: { status: "paid" } }, { $project: { _id: "$orderNo", status: 1 } }, { $merge: { into: "agg_merge_orders", whenMatched: "replace", whenNotMatched: "insert" } }])` |
| 127 | `aggregation-set-window-fields` | aggregation | `db.orders.aggregate([{ $setWindowFields: { partitionBy: "$status", sortBy: { createdAt: 1 }, output: { runningTotal: { $sum: "$total", window: { documents: ["unbounded", "current"] } } } } }])` |
| 128 | `collection-create-view` | collection | `db.createView("orders_paid_view", "orders", [{ $match: { status: "paid" } }])` |
| 129 | `collection-rename` | collection | `db.rename_source.renameCollection("rename_target")` |
| 130 | `collection-capped` | collection | `db.createCollection("capped_events", { capped: true, size: 1048576, max: 100 })` |
| 131 | `collection-timeseries` | collection | `db.createCollection("ts_metrics", { timeseries: { timeField: "ts", metaField: "metadata", granularity: "seconds" } })` |
| 132 | `collection-collmod-validator` | collection | `db.runCommand({ collMod: "collmod_target", validator: { $jsonSchema: { bsonType: "object", required: ["name"] } } })` |
| 133 | `collection-collmod-index-hidden` | collection | `db.runCommand({ collMod: "scratch", index: { name: "hideMe_1", hidden: true } })` |
| 134 | `collection-list-search-indexes-command` | collection | `db.runCommand({ listSearchIndexes: "orders" })` |
| 135 | `command-buildinfo-serverstatus` | command | `db.adminCommand({ buildInfo: 1 }); db.adminCommand({ serverStatus: 1 })` |
| 136 | `command-ping` | command | `db.adminCommand({ ping: 1 })` |
| 137 | `command-hello` | command | `db.adminCommand({ hello: 1 })` |
| 138 | `command-listdatabases` | command | `db.adminCommand({ listDatabases: 1 })` |
| 139 | `command-connectionstatus` | command | `db.adminCommand({ connectionStatus: 1, showPrivileges: false })` |
| 140 | `command-dbstats` | command | `db.runCommand({ dbStats: 1 })` |
| 141 | `command-collstats` | command | `db.runCommand({ collStats: "orders" })` |
| 142 | `command-hostinfo` | command | `db.adminCommand({ hostInfo: 1 })` |
| 143 | `command-getcmdlineopts` | command | `db.adminCommand({ getCmdLineOpts: 1 })` |
| 144 | `command-getparameter-fcv` | command | `db.adminCommand({ getParameter: 1, featureCompatibilityVersion: 1 })` |
| 145 | `command-currentop` | command | `db.adminCommand({ currentOp: 1, active: true })` |
| 146 | `command-top` | command | `db.adminCommand({ top: 1 })` |
| 147 | `command-profile-get` | command | `db.runCommand({ profile: -1 })` |
| 148 | `command-listcommands` | command | `db.adminCommand({ listCommands: 1 })` |
| 149 | `command-usersinfo` | command | `db.adminCommand({ usersInfo: 1 })` |
| 150 | `command-rolesinfo` | command | `db.adminCommand({ rolesInfo: 1 })` |
| 151 | `command-replset-get-status` | command | `db.adminCommand({ replSetGetStatus: 1 })` |
| 152 | `command-list-shards` | command | `db.adminCommand({ listShards: 1 })` |
| 153 | `command-get-default-rw-concern` | command | `db.adminCommand({ getDefaultRWConcern: 1 })` |
| 154 | `command-validate` | command | `db.runCommand({ validate: "orders", full: false })` |
| 155 | `command-plan-cache-clear` | command | `db.runCommand({ planCacheClear: "orders" })` |
| 156 | `command-plan-cache-list` | command | `db.runCommand({ planCacheListPlans: "orders", query: { status: "paid" }, sort: {}, projection: {} })` |
| 157 | `datatype-string` | datatype | `db.types.insertOne({ kind: "datatype-string", value: "text" })` |
| 158 | `datatype-int32` | datatype | `db.types.insertOne({ kind: "datatype-int32", value: 32 })` |
| 159 | `datatype-int64` | datatype | `db.types.insertOne({ kind: "datatype-int64", value: NumberLong("64") })` |
| 160 | `datatype-double` | datatype | `db.types.insertOne({ kind: "datatype-double", value: 3.14 })` |
| 161 | `datatype-decimal128` | datatype | `db.types.insertOne({ kind: "datatype-decimal128", value: NumberDecimal("12.34") })` |
| 162 | `datatype-boolean` | datatype | `db.types.insertOne({ kind: "datatype-boolean", value: true })` |
| 163 | `datatype-date` | datatype | `db.types.insertOne({ kind: "datatype-date", value: new Date() })` |
| 164 | `datatype-array` | datatype | `db.types.insertOne({ kind: "datatype-array", value: ["a", "b"] })` |
| 165 | `datatype-document` | datatype | `db.types.insertOne({ kind: "datatype-document", value: { nested: true } })` |
| 166 | `datatype-null` | datatype | `db.types.insertOne({ kind: "datatype-null", value: null })` |
| 167 | `datatype-objectid` | datatype | `db.types.insertOne({ kind: "datatype-objectid", value: ObjectId() })` |
| 168 | `datatype-binary` | datatype | `db.types.insertOne({ kind: "datatype-binary", value: BinData(0, "AQID") })` |
| 169 | `datatype-timestamp` | datatype | `db.types.insertOne({ kind: "datatype-timestamp", value: Timestamp() })` |
| 170 | `datatype-regex` | datatype | `db.types.insertOne({ kind: "datatype-regex", value: /^compat/i })` |
| 171 | `datatype-min-key` | datatype | `db.types.insertOne({ kind: "datatype-min-key", value: MinKey() })` |
| 172 | `datatype-max-key` | datatype | `db.types.insertOne({ kind: "datatype-max-key", value: MaxKey() })` |
| 173 | `geospatial-2dsphere-index` | geospatial | `db.places.createIndex({ location: "2dsphere" })` |
| 174 | `geospatial-geo-within` | geospatial | `db.places.countDocuments({ location: { $geoWithin: { $geometry: { type: "Polygon", coordinates: [[[116,39],[117,39],[117,40.5],[116,40.5],[116,39]]] } } } })` |
| 175 | `geospatial-near` | geospatial | `db.places.find({ location: { $near: { $geometry: { type: "Point", coordinates: [116.397, 39.908] }, $maxDistance: 100000 } } }).limit(1)` |
| 176 | `geospatial-geo-intersects` | geospatial | `db.places.countDocuments({ area: { $geoIntersects: { $geometry: { type: "Point", coordinates: [116.397, 39.908] } } } })` |
| 177 | `transaction-commit` | transaction | `session.startTransaction(); db.events.insertOne(...); db.customers.updateOne(...); session.commitTransaction()` |
| 178 | `transaction-abort` | transaction | `session.startTransaction(); db.events.insertOne(...); session.abortTransaction()` |
| 179 | `transaction-read-your-writes` | transaction | `session.startTransaction(); db.events.insertOne(...); db.events.countDocuments(...); session.commitTransaction()` |
| 180 | `change-stream-open` | changeStream | `const cursor = db.events.watch(); db.events.insertOne({ type: "change-stream", at: new Date() }); cursor.hasNext()` |
| 181 | `change-stream-pipeline` | changeStream | `db.events.watch([{ $match: { "fullDocument.type": "change-stream-pipeline" } }])` |
| 182 | `change-stream-full-document` | changeStream | `db.events.watch([], { fullDocument: "updateLookup" })` |
