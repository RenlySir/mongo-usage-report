# MongoDB Usage Collector

Java 17 executable collector for MongoDB replacement assessment. It gathers inventory, feature usage, workload signals, and risk findings from a MongoDB-compatible endpoint.

## Build

```bash
mvn package
```

The executable fat JAR is generated at:

```text
target/mongo-usage-collector.jar
```

## Safe Default Run

Default mode is read-only. It does not enable profiler, does not modify indexes, users, roles, or replica-set settings, and does not export collection documents.

```bash
java -jar target/mongo-usage-collector.jar \
  --uri "mongodb://user:password@host:27017/admin?authSource=admin" \
  --out ./mongo-usage-report
```

Generated files:

```text
mongo-usage-report/
  inventory.json
  features.json
  workload.json
  raw.json
  summary.md
  risk-matrix.csv
  mongo-usage-report.xlsx
```

## Optional Profiler Sampling

Use profiler sampling only with explicit approval from the customer. The tool records current profiling settings per database, sets profiler level 1 with the requested `slowms`, sleeps for the requested period, then restores the original settings.

```bash
java -jar target/mongo-usage-collector.jar \
  --uri "mongodb://user:password@host:27017/admin?authSource=admin" \
  --out ./mongo-usage-report \
  --enable-profiler \
  --profile-seconds 300 \
  --slow-ms 50
```

The Excel workbook contains these sheets: `Overview`, `Databases`, `Collections`, `Indexes`, `Features`, `Workload`, and `Command Errors`.

## Useful Options

```text
--include-dbs db1,db2      Only collect listed databases.
--exclude-dbs db1,db2      Skip listed databases. Defaults to local.
--sample-limit 1000        Max existing system.profile rows per database.
--redact / --no-redact     Redact sensitive command fields. Defaults to enabled.
```

## What It Detects

- Collection features: validators, JSON Schema, views, capped collections, time series.
- Index features: TTL, unique, partial, collation, text, 2dsphere/2d, hashed, wildcard.
- Workload features from profiler samples: aggregation stages, transactions, Change Streams, text search, geospatial queries, array filter updates.
- Operational signals: database stats, collection stats, index stats, plan cache stats, server status when permissions allow.

Command failures caused by permissions or server incompatibility are recorded in `raw.json` and do not abort the whole collection.
