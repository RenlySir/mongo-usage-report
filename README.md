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

## Options

```text
--mongo-version 4|4.4|5|6|6.0.7|6.2|7
                          Required. Selects version-compatible collection methods.
--include-dbs db1,db2      Only collect listed databases.
--exclude-dbs db1,db2      Skip listed databases. Defaults to local.
--sample-limit 1000        Max existing system.profile rows per database.
--redact / --no-redact     Redact sensitive command fields. Defaults to enabled.
```

## Version-specific behavior

- MongoDB 4.x: uses `isMaster`; skips `$queryStats`; skips `getDefaultRWConcern` unless version is `4.4` or newer.
- MongoDB 5.x and 6.0 before 6.0.7: uses `hello`; uses command-form `currentOp`; skips `$queryStats`.
- MongoDB 6.0.7 and newer: attempts `$queryStats` where the server supports it.
- MongoDB 6.2 and 7.x: prefers `$currentOp` aggregation and falls back to command-form `currentOp` if unavailable.

Command failures from permissions or server differences are recorded in both the Excel `Command Errors` sheet and `raw.json`; they do not abort the run.
