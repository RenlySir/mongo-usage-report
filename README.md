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

The Excel workbook contains these sheets: `Overview`, `Deployment`, `Runtime Metrics`, `Databases`, `Collections`, `Indexes`, `Query Shapes`, `Workload`, and `Command Errors`.

## What it collects

- Deployment: standalone / replica set / sharding hints, replica set name, primary, hosts, arbiters, storage engine, FCV, `replSetGetStatus`, `listShards`, `getCmdLineOpts`, `hostInfo`.
- Runtime and load: `serverStatus` connection counters, opcounters, network bytes, memory, WiredTiger cache where available.
- Inventory: databases, collections, collection stats, indexes, index stats, plan cache stats where supported.
- Workload: existing `system.profile` rows, optional profiler sampling, and active operations from `currentOp`.
- Query usage: normalized query shapes with literal values replaced by `?`, so the same query pattern with different parameters is de-duplicated and aggregated.

## Optional profiler sampling

Use only with explicit approval. The tool records current profiling settings per database, sets profiler level 1 with the requested `slowms`, waits, then restores the original settings.

```bash
java -jar target/mongo-usage-collector.jar \
  --uri "mongodb://user:password@host:27017/admin?authSource=admin" \
  --out ./mongo-usage-report \
  --enable-profiler \
  --profile-seconds 300 \
  --slow-ms 50
```

## Options

```text
--include-dbs db1,db2      Only collect listed databases.
--exclude-dbs db1,db2      Skip listed databases. Defaults to local.
--sample-limit 1000        Max existing system.profile rows per database.
--redact / --no-redact     Redact sensitive command fields. Defaults to enabled.
```

Command failures from permissions or server differences are recorded in both the Excel `Command Errors` sheet and `raw.json`; they do not abort the run.
