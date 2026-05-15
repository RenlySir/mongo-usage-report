package com.example.mongousage.model;

import org.bson.Document;

public class IndexInfo {
    private String name;
    private Document key = new Document();
    private Document raw = new Document();

    public IndexInfo() {
    }

    public IndexInfo(String name, Document key, Document raw) {
        this.name = name;
        this.key = key == null ? new Document() : key;
        this.raw = raw == null ? new Document() : raw;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Document getKey() {
        return key;
    }

    public void setKey(Document key) {
        this.key = key == null ? new Document() : key;
    }

    public Document getRaw() {
        return raw;
    }

    public void setRaw(Document raw) {
        this.raw = raw == null ? new Document() : raw;
    }
}
