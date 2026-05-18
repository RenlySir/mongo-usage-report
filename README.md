# MongoDB Usage Collector

Java 17 CLI that **collects** MongoDB migration assessment data and writes an Excel report. It is aligned with the migration questionnaire: deployment mode, version/configuration, database and collection scale, indexes, runtime counters, profiler/currentOp workload samples, and de-duplicated query shapes. Default mode is read-only.

## 项目功能总结

本项目是一个面向 MongoDB 替换、迁移评估和兼容性验证的 Java 命令行工具。它主要解决三个问题：

- 在不导出业务数据的前提下，收集客户 MongoDB 环境的部署模式、版本、库表规模、索引、运行负载、查询形态和潜在迁移风险。
- 将 `collect` 收集到的明细结果再次汇总，生成更适合迁移评审和客户沟通的 Excel 摘要。
- 在指定 MongoDB 或 MongoDB 兼容服务上自动创建测试 schema、测试数据并执行兼容性用例，输出 JSON 和 Excel 测试结果。

核心命令如下：

| 命令 | 是否连接 MongoDB | 是否写入测试数据 | 适用场景 | 主要产物 |
| --- | --- | --- | --- | --- |
| `collect` | 是 | 否，默认只读；启用 profiler 时会临时修改 profiling 配置 | 迁移前信息收集、负载与查询特征分析、部署模式识别 | `mongo-usage-report.xlsx`, `raw.json`, `inventory.json`, `workload.json` |
| `summarize` | 否 | 否 | 对 `collect` 产物进行二次汇总，形成评审版摘要 | `mongo-usage-summary.xlsx`, `mongo-usage-summary.html` |
| `compat-test` | 是 | 是，创建临时测试库、集合、索引和样例数据 | 验证目标数据库对 MongoDB 基础能力的兼容性 | `compat-test-report.json`, `compat-test-results.xlsx` |

推荐使用流程：

```bash
# 1. 构建可执行 JAR
mvn package

# 2. 只读收集 MongoDB 使用信息
java -jar target/mongo-usage-collector.jar collect \
  --mongo-version 7 \
  --uri "mongodb://user:password@host:27017/admin?authSource=admin" \
  --out ./mongo-usage-report

# 3. 基于 collect 结果生成汇总 Excel，不再连接 MongoDB
java -jar target/mongo-usage-collector.jar summarize \
  --report-dir ./mongo-usage-report

# 4. 可选：在测试环境或授权环境执行兼容性测试
java -jar target/mongo-usage-collector.jar compat-test \
  --mongo-version 7 \
  --uri "mongodb://user:password@host:27017/admin?authSource=admin" \
  --out ./mongo-compat-test-report \
  --compat-db mongo_usage_compat_test
```

输出文件定位：

| 文件 | 来源命令 | 用途 |
| --- | --- | --- |
| `mongo-usage-report.xlsx` | `collect` | 明细版采集报告，包含部署、库表、索引、负载、查询形态和错误明细。 |
| `raw.json` | `collect` | 完整机器可读采集结果，也是 `summarize` 的输入。 |
| `mongo-usage-summary.xlsx` | `summarize` | Excel 评审版摘要，突出部署规模、特性使用、重点集合、查询形态和风险项。 |
| `mongo-usage-summary.html` | `summarize` | HTML 评审版摘要，内容与 Excel 汇总口径一致，便于浏览器直接查看和分享。 |
| `compat-test-results.xlsx` | `compat-test` | 兼容性测试 Excel，包含编号、mongosh 命令、状态、耗时和错误原因。 |
| `compat-test-report.json` | `compat-test` | 完整机器可读兼容性测试结果。 |

边界说明：

- `collect` 不导出业务文档内容，默认不创建索引、不创建用户、不修改集合数据。
- `summarize` 只读取已有 `raw.json`，适合离线汇总和重复生成报告。
- `compat-test` 会写入测试数据，建议在测试实例、临时库或已授权环境运行。
- MongoDB 版本差异会影响可用诊断命令；请用 `--mongo-version` 指定目标版本族。

## Build

```bash
mvn package
```

Fat JAR:

```text
target/mongo-usage-collector.jar
```

## Run

The JAR has three independent commands. Use `collect` for read-only information collection. Use `summarize` to summarize an existing `collect` output directory into a review-oriented Excel workbook. Use `compat-test` when you intentionally want the tool to create schema, generate data, and run automated MongoDB feature tests.

```bash
java -jar target/mongo-usage-collector.jar --help
java -jar target/mongo-usage-collector.jar collect --help
java -jar target/mongo-usage-collector.jar summarize --help
java -jar target/mongo-usage-collector.jar compat-test --help
```

## Collect MongoDB usage information

Read-only: no profiler changes, no index or user changes, no collection document export.

```bash
java -jar target/mongo-usage-collector.jar collect \
  --mongo-version 7 \
  --uri "mongodb://user:password@host:27017/admin?authSource=admin" \
  --out ./mongo-usage-report
```

Output:

```text
mongo-usage-report/
  mongo-usage-report.xlsx  # Excel workbook for delivery and review
  raw.json          # full collected report
  inventory.json    # summary + databases tree
  workload.json     # profiler samples (may be empty if profiling is off)
```

The Excel workbook contains these sheets: `Overview`, `Deployment`, `Runtime Metrics`, `Databases`, `Collections`, `Indexes`, `Namespace Usage`, `Query Stats`, `Query Shapes`, `Workload`, `Command Errors`, and `Skipped Diagnostics`.

## Summarize collected report

After `collect` has written a `mongo-usage-report` directory, use `summarize` to generate compact summary reports from the existing `raw.json`. This command does not connect to MongoDB and does not collect new data.

```bash
java -jar target/mongo-usage-collector.jar summarize \
  --report-dir ./mongo-usage-report
```

Output:

```text
mongo-usage-report/
  mongo-usage-summary.xlsx  # summarized workbook for migration review
  mongo-usage-summary.html  # summarized browser-readable report
```

The summary reports contain these sections: `Executive Summary`, `Feature Summary`, `Top Collections`, `Top Query Shapes`, and `Risks`. The Excel workbook keeps the existing sheet-based format, and the HTML report presents the same summary in a browser-readable page. They are intended for quick review after collection: deployment mode, version, scale, detected feature usage, largest collections, slowest or most sampled query shapes, and high-signal migration review items.

## What it collects

- Deployment: standalone / replica set / sharding / mongos hints, Atlas and common managed-compatible service hints, self-managed hints, provider, hosting type, process type, node role, replica set members, shard names, storage engine, FCV, `replSetGetStatus`, `listShards`, `getCmdLineOpts`, `hostInfo`, `connectionStatus`, `getDefaultRWConcern`.
- Runtime and load: `serverStatus` connection counters, opcounters, network bytes, memory, WiredTiger cache where available.
- Inventory: databases, collections, collection stats, indexes, index stats, plan cache stats, and namespace usage from `top` where supported.
- Workload: existing `system.profile` rows, optional profiler sampling, and active operations from `$currentOp` with fallback to `currentOp`.
- Query usage: `$queryStats` when available, plus normalized query shapes with literal values replaced by type placeholders. Pagination, sort, projection, read/write concern, collation, and hint are preserved in the shape because they materially affect migration behavior.

## Performance impact on MongoDB

Default mode is designed to be read-only and bounded, but it still runs diagnostic commands against the target MongoDB deployment.

- Cluster-level commands such as `buildInfo`, `hello` / `isMaster`, `serverStatus`, `connectionStatus`, `getDefaultRWConcern`, `replSetGetStatus`, `listShards`, `getCmdLineOpts`, and `hostInfo` are one-time metadata or status reads. Their impact is normally low.
- Inventory collection runs `dbStats`, `listCollections`, `collStats`, `listIndexes`, `$indexStats`, and `$planCacheStats` per selected database or collection. The request count grows with database and collection count. The tool does not export business documents or run full collection scans intentionally, but collection/index statistics can still consume server CPU and I/O on very large clusters.
- Workload collection reads existing `system.profile` documents with `sort(ts desc).limit(--sample-limit)`. Lower `--sample-limit` reduces reads from profile collections. The default limit is `1000` per database.
- Active operation and query telemetry use diagnostic reads (`$currentOp` or `currentOp`, `top`, and `$queryStats` on supported versions). These are best-effort calls; permission or version failures are captured in the report instead of aborting collection.
- Diagnostic reads are bounded with a short server-side timeout where the MongoDB driver supports it. If a diagnostic command is unavailable, unauthorized, or too expensive to finish in time, the collector records the failure or skip and continues with the rest of the report.
- Topology-specific unsupported diagnostics are skipped instead of treated as unexpected errors. For example, `mongos` does not expose `top` or `getParameter.featureCompatibilityVersion`; these are written to `Skipped Diagnostics`.
- Excel and JSON generation happens locally after data is fetched, so workbook generation does not add load to MongoDB.

Enabling `--enable-profiler` has a higher impact because it changes profiling settings temporarily:

- The tool records the current profiling settings per selected database, sets profiler level `1` with the requested `--slow-ms`, waits for `--profile-seconds`, and then restores the original settings.
- During the profiling window, MongoDB writes matching slow operations to `system.profile`. Lower `--slow-ms` values and longer sampling windows increase write volume, disk usage, and CPU overhead.
- Avoid `--slow-ms 0` in production unless the customer explicitly approves full operation profiling. Prefer a short off-peak window and start with `--slow-ms 100` or higher, then lower it only if the captured workload is insufficient.

For large or production environments, start with read-only mode, restrict scope with `--include-dbs`, keep `--sample-limit` modest, and run profiler sampling only after customer approval.

## Optional profiler sampling

Use only with explicit approval. The tool records current profiling settings per database, sets profiler level 1 with the requested `slowms`, waits, then restores the original settings.

```bash
java -jar target/mongo-usage-collector.jar collect \
  --mongo-version 7 \
  --uri "mongodb://user:password@host:27017/admin?authSource=admin" \
  --out ./mongo-usage-report \
  --enable-profiler \
  --profile-seconds 300 \
  --slow-ms 50
```

## Automated compatibility test data and cases

Use the separate `compat-test` command to create a temporary MongoDB test database, create schema and indexes, insert sample data, execute feature tests, then write JSON and Excel test reports. This command does not run the inventory/workload collector.

```bash
java -jar target/mongo-usage-collector.jar compat-test \
  --mongo-version 7 \
  --uri "mongodb://user:password@host:27017/admin?authSource=admin" \
  --out ./mongo-usage-report \
  --compat-db mongo_usage_compat_test
```

The compatibility test catalog is maintained in code at `MongoCompatTestCatalog`. It currently runs 120 numbered checks covering JSON schema validation, CRUD, query operators, indexes, aggregation stages and expressions, admin commands, BSON data types, transactions, and change streams. Transactions and change streams are marked `SKIP` on standalone deployments where MongoDB itself requires a replica set or sharded cluster.

The full test case list is documented in `test_case.md`. Each row includes the test number, test ID, category, and the corresponding MongoDB shell command or equivalent command sequence.

Output:

```text
mongo-usage-report/
  compat-test-report.json     # full machine-readable compatibility test result
  compat-test-results.xlsx    # Excel workbook for delivery and review
```

The Excel workbook contains `Summary` and `Test Results` sheets. The `Test Results` sheet includes the numbered case ID, category, test name, corresponding mongosh command or equivalent command sequence, status (`PASS`, `FAIL`, `SKIP`), success flag, elapsed milliseconds, and failure or skip reason.

By default the temporary test database is dropped after the run. Add `--keep-compat-db` when you want to inspect the generated schema and sample data manually.

## Command options

`collect` options:

```text
--mongo-version 4|4.4|5|6|6.0.7|6.2|7
                          Required. Selects version-compatible collection methods.
--uri mongodb://...        Required. MongoDB connection string.
--out ./dir                Output directory. Defaults to mongo-usage-report.
--include-dbs db1,db2      Only collect listed databases.
--exclude-dbs db1,db2      Skip listed databases. Defaults to local.
--sample-limit 1000        Max existing system.profile rows per database.
--enable-profiler          Temporarily enable profiler level 1, sample, and restore settings.
--profile-seconds 300      Profiler sampling window when --enable-profiler is set.
--slow-ms 50               Profiler slowms when --enable-profiler is set.
--redact / --no-redact     Redact sensitive command fields. Defaults to enabled.
```

`summarize` options:

```text
--report-dir ./dir          Directory produced by collect. Defaults to mongo-usage-report.
--out ./summary.xlsx        Optional summary Excel output file. Defaults to <report-dir>/mongo-usage-summary.xlsx.
--html-out ./summary.html   Optional summary HTML output file. Defaults to <report-dir>/mongo-usage-summary.html.
```

`compat-test` options:

```text
--mongo-version 4|4.4|5|6|6.0.7|6.2|7
                          Required. Version label written into the test report.
--uri mongodb://...        Required. MongoDB connection string.
--out ./dir                Output directory. Defaults to mongo-compat-test-report.
--compat-db name           Database used by compatibility tests. Defaults to mongo_usage_compat_test.
--keep-compat-db           Keep the compatibility test database after the run.
```

## Version-specific behavior

`--mongo-version` controls which MongoDB commands the collector uses. It is not only a report label. Pass the closest target server API version, for example `4`, `4.4`, `5`, `6`, `6.0.7`, `6.2`, `7`, or a full server string such as `v7.0.5`. The report stores the normalized value as `major.minor.patch`.

| Target version | Handshake command | Default read/write concern | Active operations | Query telemetry | Main report difference |
| --- | --- | --- | --- | --- | --- |
| 4.0 - 4.2 | `isMaster` | Skipped | command-form `currentOp` | `$queryStats` skipped | Deployment and workload are based on legacy handshake, `serverStatus`, `top`, existing profiler rows, and `currentOp`. |
| 4.4 | `isMaster` | `getDefaultRWConcern` enabled | command-form `currentOp` | `$queryStats` skipped | Adds default read/write concern information for migration risk review. |
| 5.x | `hello`, with fallback to `isMaster` if needed | `getDefaultRWConcern` enabled | command-form `currentOp` | `$queryStats` skipped | Uses the modern handshake while keeping the older active-operation command path. |
| 6.0.0 - 6.0.6 | `hello`, with fallback to `isMaster` if needed | `getDefaultRWConcern` enabled | command-form `currentOp` | `$queryStats` skipped | Same collection strategy as 5.x for query telemetry; query shapes come from profiler and current operations. |
| 6.0.7 - 6.1.x | `hello`, with fallback to `isMaster` if needed | `getDefaultRWConcern` enabled | command-form `currentOp` | `$queryStats` attempted with a `1000` row limit | Adds query telemetry when the server exposes `$queryStats`; unavailable or unauthorized calls are recorded as command errors. |
| 6.2.x - 7.x | `hello`, with fallback to `isMaster` if needed | `getDefaultRWConcern` enabled | prefers `$currentOp` aggregation, then falls back to command-form `currentOp` | `$queryStats` attempted with a `1000` row limit | Uses newer aggregation-based active-operation collection and includes `$queryStats` output when available. |

Commands that are broadly useful across versions, such as `buildInfo`, `serverStatus`, `connectionStatus`, `getCmdLineOpts`, `hostInfo`, `dbStats`, `collStats`, `listIndexes`, `$indexStats`, `$planCacheStats`, and existing `system.profile` reads, are attempted in all modes. Deployment-specific commands are gated by detected topology: `replSetGetStatus` is collected for replica sets, `listShards` for sharded/mongos deployments, `getDefaultRWConcern` for distributed deployments, and mongod-only diagnostics such as `top` are skipped when connected through `mongos`. If a command is unavailable because of MongoDB version, deployment type, managed-service restrictions, or permissions, the failure is recorded in both the Excel `Command Errors` sheet and `raw.json`; expected topology skips are recorded in `Skipped Diagnostics`; neither case aborts the run.

## Deployment mode detection

The collector classifies deployment information from low-cost metadata that is already collected: the redacted target URI, `hello` / `isMaster`, `serverStatus`, `buildInfo`, `getCmdLineOpts`, and `hostInfo`. The `Deployment` Excel sheet and `raw.json` include:

- `deploymentMode`: `standalone`, `replicaSet`, `sharded`, or `unknown`.
- `hostingType`: `self-managed`, `managed`, `managed-compatible`, or `unknown`.
- `provider`: `self-managed`, `atlas`, `amazon-documentdb`, `azure-cosmosdb-mongodb`, `aliyun-mongodb`, `tencent-cloud-mongodb`, `huawei-cloud-mongodb`, or `unknown`.
- `processType` and `nodeRole`: for example `mongod` / `primary`, `secondary`, `standalone`, or `mongos`.
- Replica set and sharding details: member count, member state strings, shard count, and shard names when the server exposes them.
- Detection signals: the concrete hints used for classification, so ambiguous compatible services can be reviewed manually.

Use the actual target MongoDB version instead of always choosing the latest version. For MongoDB-compatible services, select the closest supported API level. If compatibility is uncertain, start with a lower compatible setting such as `--mongo-version 6` to avoid newer diagnostic commands, then rerun with `6.0.7`, `6.2`, or `7` only when those commands are expected to work.
