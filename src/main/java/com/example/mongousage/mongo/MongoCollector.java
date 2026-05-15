package com.example.mongousage.mongo;

import com.example.mongousage.analysis.QueryShapeAnalyzer;
import com.example.mongousage.config.CollectorOptions;
import com.example.mongousage.model.CollectionInfo;
import com.example.mongousage.model.CommandError;
import com.example.mongousage.model.DatabaseInfo;
import com.example.mongousage.model.DeploymentInfo;
import com.example.mongousage.model.IndexInfo;
import com.example.mongousage.model.ProfileSample;
import com.example.mongousage.model.RuntimeMetric;
import com.example.mongousage.model.UsageReport;
import com.example.mongousage.util.BsonRedactor;
import com.example.mongousage.util.UriRedactor;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.ListCollectionsIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Sorts.descending;

public class MongoCollector {
    private final CollectorOptions options;
    private final BsonRedactor redactor = new BsonRedactor();

    public MongoCollector(CollectorOptions options) {
        this.options = options;
    }

    public UsageReport collect() {
        UsageReport report = new UsageReport();
        report.setTarget(options.isRedact() ? UriRedactor.redact(options.getUri()) : options.getUri());
        try (MongoClient client = MongoClients.create(options.getUri())) {
            MongoDatabase admin = client.getDatabase("admin");
            report.setBuildInfo(runCommand(report, "admin", "buildInfo", admin, new Document("buildInfo", 1)));
            report.setHello(runHello(report, admin));
            report.setServerStatus(runCommand(report, "admin", "serverStatus", admin, new Document("serverStatus", 1)));
            report.setDeploymentInfo(collectDeployment(report, admin));
            report.setRuntimeMetrics(collectRuntimeMetrics(report));
            collectCurrentOperations(report, admin);

            List<String> databaseNames = listDatabaseNames(report, client);
            if (options.isEnableProfiler()) {
                new ProfilerSampler(client, options, report).sample(databaseNames);
            }
            for (String databaseName : databaseNames) {
                if (!shouldCollectDatabase(databaseName)) {
                    continue;
                }
                report.getDatabases().add(collectDatabase(report, client, databaseName));
            }
            report.setQueryShapes(new QueryShapeAnalyzer().analyze(report.getProfileSamples()));
        }
        return report;
    }

    private DeploymentInfo collectDeployment(UsageReport report, MongoDatabase admin) {
        DeploymentInfo info = new DeploymentInfo();
        Document hello = report.getHello();
        if (hello.getString("setName") != null) {
            info.setDeploymentMode("replicaSet");
            info.setReplicaSetName(hello.getString("setName"));
        } else if (hello.containsKey("msg") && String.valueOf(hello.get("msg")).toLowerCase().contains("mongos")) {
            info.setDeploymentMode("sharded");
            info.setSharded(true);
        } else if (!hello.isEmpty()) {
            info.setDeploymentMode("standalone");
        }
        info.setPrimary(hello.getString("primary"));
        info.setHosts(strings(hello.get("hosts")));
        info.setArbiters(strings(hello.get("arbiters")));
        info.setSharded(info.isSharded() || "isdbgrid".equals(hello.getString("msg")));

        Document serverStatus = report.getServerStatus();
        Document storageEngine = serverStatus.get("storageEngine", Document.class);
        if (storageEngine != null) {
            info.setStorageEngine(storageEngine.getString("name"));
        }
        info.setAtlasHint(atlasHint(report));
        info.setFeatureCompatibilityVersion(featureCompatibilityVersion(report, admin));
        info.setReplSetStatus(runCommand(report, "admin", "replSetGetStatus", admin, new Document("replSetGetStatus", 1)));
        info.setShardList(runCommand(report, "admin", "listShards", admin, new Document("listShards", 1)));
        if (!info.getShardList().isEmpty()) {
            info.setDeploymentMode("sharded");
            info.setSharded(true);
        }
        info.setGetCmdLineOpts(runCommand(report, "admin", "getCmdLineOpts", admin, new Document("getCmdLineOpts", 1)));
        info.setHostInfo(runCommand(report, "admin", "hostInfo", admin, new Document("hostInfo", 1)));
        return info;
    }

    private String featureCompatibilityVersion(UsageReport report, MongoDatabase admin) {
        Document result = runCommand(report, "admin", "getParameter.featureCompatibilityVersion", admin,
                new Document("getParameter", 1).append("featureCompatibilityVersion", 1));
        Document fcv = result.get("featureCompatibilityVersion", Document.class);
        return fcv == null ? "" : String.valueOf(fcv.get("version"));
    }

    private String atlasHint(UsageReport report) {
        String value = (report.getBuildInfo().toJson() + report.getHello().toJson() + report.getServerStatus().toJson()).toLowerCase();
        return value.contains("atlas") || value.contains("mongodb.net") ? "possible Atlas-managed deployment" : "";
    }

    @SuppressWarnings("unchecked")
    private List<String> strings(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return new ArrayList<>();
    }

    private List<RuntimeMetric> collectRuntimeMetrics(UsageReport report) {
        List<RuntimeMetric> metrics = new ArrayList<>();
        Document status = report.getServerStatus();
        appendMetric(metrics, "connections", "current", nested(status, "connections", "current"));
        appendMetric(metrics, "connections", "available", nested(status, "connections", "available"));
        appendMetric(metrics, "opcounters", "insert", nested(status, "opcounters", "insert"));
        appendMetric(metrics, "opcounters", "query", nested(status, "opcounters", "query"));
        appendMetric(metrics, "opcounters", "update", nested(status, "opcounters", "update"));
        appendMetric(metrics, "opcounters", "delete", nested(status, "opcounters", "delete"));
        appendMetric(metrics, "opcounters", "getmore", nested(status, "opcounters", "getmore"));
        appendMetric(metrics, "opcounters", "command", nested(status, "opcounters", "command"));
        appendMetric(metrics, "network", "bytesIn", nested(status, "network", "bytesIn"));
        appendMetric(metrics, "network", "bytesOut", nested(status, "network", "bytesOut"));
        appendMetric(metrics, "mem", "resident", nested(status, "mem", "resident"));
        appendMetric(metrics, "mem", "virtual", nested(status, "mem", "virtual"));
        appendMetric(metrics, "wiredTiger.cache", "bytes currently in cache", nested(status, "wiredTiger", "cache", "bytes currently in the cache"));
        return metrics;
    }

    private Object nested(Document document, String... path) {
        Object current = document;
        for (String key : path) {
            if (!(current instanceof Document currentDocument)) {
                return null;
            }
            current = currentDocument.get(key);
        }
        return current;
    }

    private void appendMetric(List<RuntimeMetric> metrics, String category, String name, Object value) {
        if (value != null) {
            metrics.add(new RuntimeMetric(category, name, String.valueOf(value)));
        }
    }

    private void collectCurrentOperations(UsageReport report, MongoDatabase admin) {
        Document currentOp = runCommand(report, "admin", "currentOp", admin,
                new Document("currentOp", 1).append("active", true));
        Object inprog = currentOp.get("inprog");
        if (!(inprog instanceof List<?> operations)) {
            return;
        }
        for (Object operation : operations) {
            if (operation instanceof Document raw) {
                ProfileSample sample = toProfileSample("currentOp", raw);
                if (sample.getCommand().isEmpty()) {
                    Document command = raw.get("command", Document.class);
                    sample.setCommand(command == null ? new Document() : maybeRedact(command));
                }
                if (sample.getNamespace() == null) {
                    sample.setNamespace(raw.getString("ns"));
                }
                report.getProfileSamples().add(sample);
            }
        }
    }

    private Document runHello(UsageReport report, MongoDatabase admin) {
        Document hello = runCommand(report, "admin", "hello", admin, new Document("hello", 1));
        if (hello.isEmpty()) {
            return runCommand(report, "admin", "isMaster", admin, new Document("isMaster", 1));
        }
        return hello;
    }

    private List<String> listDatabaseNames(UsageReport report, MongoClient client) {
        List<String> names = new ArrayList<>();
        try {
            for (String databaseName : client.listDatabaseNames()) {
                names.add(databaseName);
            }
        } catch (Exception e) {
            report.getCommandErrors().add(new CommandError("cluster", "listDatabaseNames", e.getMessage()));
        }
        return names;
    }

    private boolean shouldCollectDatabase(String databaseName) {
        if (!options.getIncludeDatabases().isEmpty() && !options.getIncludeDatabases().contains(databaseName)) {
            return false;
        }
        return !options.getExcludeDatabases().contains(databaseName);
    }

    private DatabaseInfo collectDatabase(UsageReport report, MongoClient client, String databaseName) {
        MongoDatabase database = client.getDatabase(databaseName);
        DatabaseInfo info = new DatabaseInfo(databaseName);
        info.setStats(runCommand(report, databaseName, "dbStats", database, new Document("dbStats", 1)));
        collectProfileSamples(report, database);
        for (CollectionInfo collectionInfo : listCollections(report, databaseName, database)) {
            collectCollectionDetails(report, database, collectionInfo);
            info.getCollections().add(collectionInfo);
        }
        return info;
    }

    private List<CollectionInfo> listCollections(UsageReport report, String databaseName, MongoDatabase database) {
        List<CollectionInfo> collections = new ArrayList<>();
        try {
            ListCollectionsIterable<Document> iterable = database.listCollections();
            for (Document raw : iterable) {
                String name = raw.getString("name");
                String type = raw.getString("type");
                Document optionsDoc = raw.get("options", Document.class);
                CollectionInfo collectionInfo = new CollectionInfo(databaseName, name, type, optionsDoc);
                collections.add(collectionInfo);
            }
        } catch (Exception e) {
            report.getCommandErrors().add(new CommandError(databaseName, "listCollections", e.getMessage()));
        }
        return collections;
    }

    private void collectCollectionDetails(UsageReport report, MongoDatabase database, CollectionInfo collectionInfo) {
        String collectionName = collectionInfo.getName();
        MongoCollection<Document> collection = database.getCollection(collectionName);
        collectionInfo.setStats(runCommand(report, collectionInfo.getNamespace(), "collStats", database, new Document("collStats", collectionName)));
        collectionInfo.setIndexes(listIndexes(report, collectionInfo, collection));
        collectionInfo.setIndexStats(runAggregation(report, collectionInfo, collection, new Document("$indexStats", new Document()), "$indexStats"));
        collectionInfo.setPlanCacheStats(runAggregation(report, collectionInfo, collection, new Document("$planCacheStats", new Document()), "$planCacheStats"));
    }

    private List<IndexInfo> listIndexes(UsageReport report, CollectionInfo collectionInfo, MongoCollection<Document> collection) {
        List<IndexInfo> indexes = new ArrayList<>();
        try {
            for (Document raw : collection.listIndexes()) {
                indexes.add(new IndexInfo(raw.getString("name"), raw.get("key", Document.class), raw));
            }
        } catch (Exception e) {
            report.getCommandErrors().add(new CommandError(collectionInfo.getNamespace(), "listIndexes", e.getMessage()));
        }
        return indexes;
    }

    private List<Document> runAggregation(UsageReport report, CollectionInfo collectionInfo, MongoCollection<Document> collection, Document stage, String name) {
        List<Document> results = new ArrayList<>();
        try {
            AggregateIterable<Document> iterable = collection.aggregate(List.of(stage));
            iterable.maxTime(10, TimeUnit.SECONDS);
            for (Document document : iterable) {
                results.add(maybeRedact(document));
            }
        } catch (Exception e) {
            report.getCommandErrors().add(new CommandError(collectionInfo.getNamespace(), name, e.getMessage()));
        }
        return results;
    }

    private void collectProfileSamples(UsageReport report, MongoDatabase database) {
        try {
            MongoCollection<Document> profile = database.getCollection("system.profile");
            FindIterable<Document> iterable = profile.find().sort(descending("ts")).limit(options.getSampleLimit());
            for (Document raw : iterable) {
                report.getProfileSamples().add(toProfileSample(database.getName(), raw));
            }
        } catch (Exception e) {
            report.getCommandErrors().add(new CommandError(database.getName(), "system.profile.find", e.getMessage()));
        }
    }

    ProfileSample toProfileSample(String databaseName, Document raw) {
        Document safeRaw = maybeRedact(raw);
        ProfileSample sample = new ProfileSample();
        sample.setDatabase(databaseName);
        sample.setNamespace(safeRaw.getString("ns"));
        sample.setOperation(safeRaw.getString("op"));
        sample.setMillis(numberAsLong(safeRaw.get("millis")));
        sample.setDocsExamined(numberAsLong(safeRaw.get("docsExamined")));
        sample.setKeysExamined(numberAsLong(safeRaw.get("keysExamined")));
        sample.setNreturned(numberAsLong(safeRaw.get("nreturned")));
        Document command = safeRaw.get("command", Document.class);
        sample.setCommand(command == null ? new Document() : command);
        sample.setRaw(safeRaw);
        return sample;
    }

    private long numberAsLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private Document runCommand(UsageReport report, String scope, String commandName, MongoDatabase database, Document command) {
        try {
            return maybeRedact(database.runCommand(command));
        } catch (Exception e) {
            report.getCommandErrors().add(new CommandError(scope, commandName, e.getMessage()));
            return new Document();
        }
    }

    private Document maybeRedact(Document document) {
        return options.isRedact() ? redactor.redact(document) : document;
    }
}
