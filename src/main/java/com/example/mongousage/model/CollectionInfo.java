package com.example.mongousage.model;

import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public class CollectionInfo {
    private String database;
    private String name;
    private String type;
    private Document options = new Document();
    private Document stats = new Document();
    private List<IndexInfo> indexes = new ArrayList<>();
    private List<Document> indexStats = new ArrayList<>();
    private List<Document> planCacheStats = new ArrayList<>();

    public CollectionInfo() {
    }

    public CollectionInfo(String database, String name, String type, Document options) {
        this.database = database;
        this.name = name;
        this.type = type;
        this.options = options == null ? new Document() : options;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNamespace() {
        return database + "." + name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Document getOptions() {
        return options;
    }

    public void setOptions(Document options) {
        this.options = options == null ? new Document() : options;
    }

    public Document getStats() {
        return stats;
    }

    public void setStats(Document stats) {
        this.stats = stats == null ? new Document() : stats;
    }

    public List<IndexInfo> getIndexes() {
        return indexes;
    }

    public void setIndexes(List<IndexInfo> indexes) {
        this.indexes = indexes == null ? new ArrayList<>() : indexes;
    }

    public List<Document> getIndexStats() {
        return indexStats;
    }

    public void setIndexStats(List<Document> indexStats) {
        this.indexStats = indexStats == null ? new ArrayList<>() : indexStats;
    }

    public List<Document> getPlanCacheStats() {
        return planCacheStats;
    }

    public void setPlanCacheStats(List<Document> planCacheStats) {
        this.planCacheStats = planCacheStats == null ? new ArrayList<>() : planCacheStats;
    }
}
