package com.example.mongousage.model;

import java.util.ArrayList;
import java.util.List;

public class QueryShape {
    private String operation;
    private String namespace;
    private String shape;
    private List<String> features = new ArrayList<>();
    private int sampleCount;
    private long avgMillis;
    private long maxMillis;
    private long avgDocsExamined;
    private long avgKeysExamined;
    private long avgReturned;

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getShape() {
        return shape;
    }

    public void setShape(String shape) {
        this.shape = shape;
    }

    public List<String> getFeatures() {
        return features;
    }

    public void setFeatures(List<String> features) {
        this.features = features == null ? new ArrayList<>() : features;
    }

    public int getSampleCount() {
        return sampleCount;
    }

    public void setSampleCount(int sampleCount) {
        this.sampleCount = sampleCount;
    }

    public long getAvgMillis() {
        return avgMillis;
    }

    public void setAvgMillis(long avgMillis) {
        this.avgMillis = avgMillis;
    }

    public long getMaxMillis() {
        return maxMillis;
    }

    public void setMaxMillis(long maxMillis) {
        this.maxMillis = maxMillis;
    }

    public long getAvgDocsExamined() {
        return avgDocsExamined;
    }

    public void setAvgDocsExamined(long avgDocsExamined) {
        this.avgDocsExamined = avgDocsExamined;
    }

    public long getAvgKeysExamined() {
        return avgKeysExamined;
    }

    public void setAvgKeysExamined(long avgKeysExamined) {
        this.avgKeysExamined = avgKeysExamined;
    }

    public long getAvgReturned() {
        return avgReturned;
    }

    public void setAvgReturned(long avgReturned) {
        this.avgReturned = avgReturned;
    }
}
