package com.example.mongousage.model;

import org.bson.Document;

public class ProfileSample {
    private String database;
    private String namespace;
    private String operation;
    private long millis;
    private long docsExamined;
    private long keysExamined;
    private long nreturned;
    private Document command = new Document();
    private Document raw = new Document();

    public ProfileSample() {
    }

    public ProfileSample(String database, String namespace, String operation, Document command) {
        this.database = database;
        this.namespace = namespace;
        this.operation = operation;
        this.command = command == null ? new Document() : command;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public long getMillis() {
        return millis;
    }

    public void setMillis(long millis) {
        this.millis = millis;
    }

    public long getDocsExamined() {
        return docsExamined;
    }

    public void setDocsExamined(long docsExamined) {
        this.docsExamined = docsExamined;
    }

    public long getKeysExamined() {
        return keysExamined;
    }

    public void setKeysExamined(long keysExamined) {
        this.keysExamined = keysExamined;
    }

    public long getNreturned() {
        return nreturned;
    }

    public void setNreturned(long nreturned) {
        this.nreturned = nreturned;
    }

    public Document getCommand() {
        return command;
    }

    public void setCommand(Document command) {
        this.command = command == null ? new Document() : command;
    }

    public Document getRaw() {
        return raw;
    }

    public void setRaw(Document raw) {
        this.raw = raw == null ? new Document() : raw;
    }
}
