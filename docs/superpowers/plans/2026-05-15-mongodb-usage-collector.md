# MongoDB Usage Collector Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Java 17 executable fat JAR that collects MongoDB inventory, feature usage, and workload signals for replacement assessment.

**Architecture:** A Picocli CLI coordinates MongoDB Java Driver collectors. Collectors produce neutral JSON-friendly DTOs, an analyzer derives feature flags and risk findings, and writers emit JSON plus Markdown reports. Default mode is read-only; profiler changes require `--enable-profiler` and are restored after sampling.

**Tech Stack:** Java 17, Maven, MongoDB Java Driver Sync, Jackson, Picocli, JUnit 5, AssertJ, Maven Shade Plugin.

---

### Task 1: Maven Project Skeleton

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/com/example/mongousage/MongoUsageCollectorApp.java`
- Create: `src/test/java/com/example/mongousage/MongoUsageCollectorAppTest.java`

- [ ] Create Maven coordinates `com.example:mongo-usage-collector:0.1.0`.
- [ ] Add dependencies: `mongodb-driver-sync`, `jackson-databind`, `jackson-datatype-jsr310`, `picocli`, `junit-jupiter`, `assertj-core`.
- [ ] Configure `maven-shade-plugin` main class `com.example.mongousage.MongoUsageCollectorApp`.
- [ ] Add a smoke test that Picocli parses `--help`.

### Task 2: Domain Model and Feature Analyzer

**Files:**
- Create: `src/main/java/com/example/mongousage/model/*.java`
- Create: `src/main/java/com/example/mongousage/analysis/FeatureAnalyzer.java`
- Create: `src/test/java/com/example/mongousage/analysis/FeatureAnalyzerTest.java`

- [ ] Model `CollectionInfo`, `IndexInfo`, `DatabaseInfo`, `ProfileSample`, `FeatureFinding`, and `UsageReport`.
- [ ] Write failing tests for detecting JSON Schema validators, time series, capped collections, text/2dsphere/hashed/wildcard/TTL indexes, Change Streams, transactions, geospatial operators, text search, and aggregation stages.
- [ ] Implement analyzer from static metadata and profiler command documents.

### Task 3: MongoDB Collectors

**Files:**
- Create: `src/main/java/com/example/mongousage/mongo/MongoCollector.java`
- Create: `src/main/java/com/example/mongousage/mongo/ProfilerSampler.java`
- Create: `src/test/java/com/example/mongousage/mongo/MongoCollectorTest.java`

- [ ] Implement read-only collection of `buildInfo`, `hello`, `serverStatus`, database stats, collection infos, collection stats, indexes, `$indexStats`, `$planCacheStats`, and existing `system.profile`.
- [ ] Wrap each command in best-effort error capture so missing permissions do not abort the whole run.
- [ ] Implement optional profiler sampling that stores original profiling levels per database, sets requested level/slowms, sleeps, reads profile records, and restores originals.

### Task 4: Report Writers and CLI

**Files:**
- Create: `src/main/java/com/example/mongousage/report/ReportWriter.java`
- Modify: `src/main/java/com/example/mongousage/MongoUsageCollectorApp.java`
- Create: `README.md`

- [ ] Emit `inventory.json`, `features.json`, `workload.json`, `raw.json`, `summary.md`, and `risk-matrix.csv`.
- [ ] CLI options: `--uri`, `--out`, `--include-dbs`, `--exclude-dbs`, `--sample-limit`, `--enable-profiler`, `--profile-seconds`, `--slow-ms`, `--redact`.
- [ ] README documents safe default mode and profiler caution.

### Task 5: Verify

- [ ] Run `mvn test`.
- [ ] Run `mvn package`.
- [ ] Run the fat JAR against the local MongoDB-compatible service.
- [ ] Inspect generated report files.
