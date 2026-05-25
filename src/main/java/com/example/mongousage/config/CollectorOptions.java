package com.example.mongousage.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CollectorOptions {
    private String uri;
    private String mongoVersion;
    private Path outputDirectory;
    private List<String> includeDatabases = new ArrayList<>();
    private List<String> excludeDatabases = new ArrayList<>(List.of("local"));
    private int sampleLimit = 1000;
    private boolean enableProfiler;
    private int profileSeconds = 300;
    private int slowMs = 50;
    private boolean redact = true;
    private boolean parallelCollection;
    private int parallelThreads = 4;

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getMongoVersion() {
        return mongoVersion;
    }

    public void setMongoVersion(String mongoVersion) {
        this.mongoVersion = mongoVersion;
    }

    public Path getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(Path outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public List<String> getIncludeDatabases() {
        return includeDatabases;
    }

    public void setIncludeDatabases(List<String> includeDatabases) {
        this.includeDatabases = includeDatabases == null ? new ArrayList<>() : includeDatabases;
    }

    public List<String> getExcludeDatabases() {
        return excludeDatabases;
    }

    public void setExcludeDatabases(List<String> excludeDatabases) {
        this.excludeDatabases = excludeDatabases == null ? new ArrayList<>() : excludeDatabases;
    }

    public int getSampleLimit() {
        return sampleLimit;
    }

    public void setSampleLimit(int sampleLimit) {
        this.sampleLimit = sampleLimit;
    }

    public boolean isEnableProfiler() {
        return enableProfiler;
    }

    public void setEnableProfiler(boolean enableProfiler) {
        this.enableProfiler = enableProfiler;
    }

    public int getProfileSeconds() {
        return profileSeconds;
    }

    public void setProfileSeconds(int profileSeconds) {
        this.profileSeconds = profileSeconds;
    }

    public int getSlowMs() {
        return slowMs;
    }

    public void setSlowMs(int slowMs) {
        this.slowMs = slowMs;
    }

    public boolean isRedact() {
        return redact;
    }

    public void setRedact(boolean redact) {
        this.redact = redact;
    }

    public boolean isParallelCollection() {
        return parallelCollection;
    }

    public void setParallelCollection(boolean parallelCollection) {
        this.parallelCollection = parallelCollection;
    }

    public int getParallelThreads() {
        return parallelThreads;
    }

    public void setParallelThreads(int parallelThreads) {
        this.parallelThreads = parallelThreads;
    }
}
