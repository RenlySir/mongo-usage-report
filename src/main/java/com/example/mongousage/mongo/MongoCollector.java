package com.example.mongousage.mongo;

import com.example.mongousage.config.CollectorOptions;
import com.example.mongousage.model.CollectionInfo;
import com.example.mongousage.model.CommandError;
import com.example.mongousage.model.DatabaseInfo;
import com.example.mongousage.model.IndexInfo;
import com.example.mongousage.model.ProfileSample;
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
        }
        return report;
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
