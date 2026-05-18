# MongoDB Usage Collector

Java 17 CLI that **collects** MongoDB migration assessment data and writes an Excel report. It is aligned with the migration questionnaire: deployment mode, version/configuration, database and collection scale, indexes, runtime counters, profiler/currentOp workload samples, and de-duplicated query shapes. Default mode is read-only.

## Build

```bash
mvn package
```

Fat JAR:

```text
target/mongo-usage-collector.jar
```

## Run

Read-only: no profiler changes, no index or user changes, no collection document export.

```bash
java -jar target/mongo-usage-collector.jar \
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

The Excel workbook contains these sheets: `Overview`, `Deployment`, `Runtime Metrics`, `Databases`, `Collections`, `Indexes`, `Namespace Usage`, `Query Stats`, `Query Shapes`, `Workload`, and `Command Errors`.

## What it collects

- Deployment: standalone / replica set / sharding hints, replica set name, primary, hosts, arbiters, storage engine, FCV, `replSetGetStatus`, `listShards`, `getCmdLineOpts`, `hostInfo`, `connectionStatus`, `getDefaultRWConcern`.
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
- Excel and JSON generation happens locally after data is fetched, so workbook generation does not add load to MongoDB.

Enabling `--enable-profiler` has a higher impact because it changes profiling settings temporarily:

- The tool records the current profiling settings per selected database, sets profiler level `1` with the requested `--slow-ms`, waits for `--profile-seconds`, and then restores the original settings.
- During the profiling window, MongoDB writes matching slow operations to `system.profile`. Lower `--slow-ms` values and longer sampling windows increase write volume, disk usage, and CPU overhead.
- Avoid `--slow-ms 0` in production unless the customer explicitly approves full operation profiling. Prefer a short off-peak window and start with `--slow-ms 100` or higher, then lower it only if the captured workload is insufficient.

For large or production environments, start with read-only mode, restrict scope with `--include-dbs`, keep `--sample-limit` modest, and run profiler sampling only after customer approval.

## Optional profiler sampling

Use only with explicit approval. The tool records current profiling settings per database, sets profiler level 1 with the requested `slowms`, waits, then restores the original settings.

```bash
java -jar target/mongo-usage-collector.jar \
  --mongo-version 7 \
  --uri "mongodb://user:password@host:27017/admin?authSource=admin" \
  --out ./mongo-usage-report \
  --enable-profiler \
  --profile-seconds 300 \
  --slow-ms 50
```

## Compatibility test mode

Use the separate `compat-test` command to create a temporary MongoDB test database, create schema and indexes, insert sample data, execute feature tests, then write `compat-test-report.json`. This command does not run the inventory/workload collector.

```bash
java -jar target/mongo-usage-collector.jar compat-test \
  --mongo-version 7 \
  --uri "mongodb://user:password@host:27017/admin?authSource=admin" \
  --out ./mongo-usage-report \
  --compat-db mongo_usage_compat_test
```

The compatibility test catalog is maintained in code at `MongoCompatTestCatalog`. It covers JSON schema validation, validation rejection, CRUD lifecycle, bulk writes, filter/sort/projection queries, array and nested predicates, compound/unique/TTL indexes, hint and explain, aggregation `$group` / `$lookup` / `$facet`, transactions, change streams, and read-only admin commands. Transactions and change streams are marked `SKIP` on standalone deployments where MongoDB itself requires a replica set or sharded cluster.

By default the temporary test database is dropped after the run. Add `--keep-compat-db` when you want to inspect the generated schema and sample data manually.

## Options

```text
--mongo-version 4|4.4|5|6|6.0.7|6.2|7
                          Required. Selects version-compatible collection methods.
--include-dbs db1,db2      Only collect listed databases.
--exclude-dbs db1,db2      Skip listed databases. Defaults to local.
--sample-limit 1000        Max existing system.profile rows per database.
--redact / --no-redact     Redact sensitive command fields. Defaults to enabled.
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

Commands that are broadly useful across versions, such as `buildInfo`, `serverStatus`, `connectionStatus`, `replSetGetStatus`, `listShards`, `getCmdLineOpts`, `hostInfo`, `dbStats`, `collStats`, `listIndexes`, `$indexStats`, `$planCacheStats`, `top`, and existing `system.profile` reads, are attempted in all modes. If a command is unavailable because of MongoDB version, deployment type, managed-service restrictions, or permissions, the failure is recorded in both the Excel `Command Errors` sheet and `raw.json`; it does not abort the run.

Use the actual target MongoDB version instead of always choosing the latest version. For MongoDB-compatible services, select the closest supported API level. If compatibility is uncertain, start with a lower compatible setting such as `--mongo-version 6` to avoid newer diagnostic commands, then rerun with `6.0.7`, `6.2`, or `7` only when those commands are expected to work.
