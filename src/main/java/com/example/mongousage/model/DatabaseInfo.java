package com.example.mongousage.model;

import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public class DatabaseInfo {
    private String name;
    private Document stats = new Document();
    private List<CollectionInfo> collections = new ArrayList<>();

    public DatabaseInfo() {
    }

    public DatabaseInfo(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Document getStats() {
        return stats;
    }

    public void setStats(Document stats) {
        this.stats = stats == null ? new Document() : stats;
    }

    public List<CollectionInfo> getCollections() {
        return collections;
    }

    public void setCollections(List<CollectionInfo> collections) {
        this.collections = collections == null ? new ArrayList<>() : collections;
    }
}
