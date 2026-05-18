package com.example.mongousage.model;

import org.bson.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class UsageReport {
    private Instant generatedAt = Instant.now();
    private String target;
    private String requestedMongoVersion;
    private DeploymentInfo deploymentInfo = new DeploymentInfo();
    private Document buildInfo = new Document();
    private Document hello = new Document();
    private Document serverStatus = new Document();
    private Document connectionStatus = new Document();
    private Document defaultReadWriteConcern = new Document();
    private List<Document> namespaceUsage = new ArrayList<>();
    private List<Document> queryStats = new ArrayList<>();
    private List<DatabaseInfo> databases = new ArrayList<>();
    private List<ProfileSample> profileSamples = new ArrayList<>();
    private List<QueryShape> queryShapes = new ArrayList<>();
    private List<RuntimeMetric> runtimeMetrics = new ArrayList<>();
    private List<CommandError> commandErrors = new ArrayList<>();

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getRequestedMongoVersion() {
        return requestedMongoVersion;
    }

    public void setRequestedMongoVersion(String requestedMongoVersion) {
        this.requestedMongoVersion = requestedMongoVersion;
    }

    public DeploymentInfo getDeploymentInfo() {
        return deploymentInfo;
    }

    public void setDeploymentInfo(DeploymentInfo deploymentInfo) {
        this.deploymentInfo = deploymentInfo == null ? new DeploymentInfo() : deploymentInfo;
    }

    public Document getBuildInfo() {
        return buildInfo;
    }

    public void setBuildInfo(Document buildInfo) {
        this.buildInfo = buildInfo == null ? new Document() : buildInfo;
    }

    public Document getHello() {
        return hello;
    }

    public void setHello(Document hello) {
        this.hello = hello == null ? new Document() : hello;
    }

    public Document getServerStatus() {
        return serverStatus;
    }

    public void setServerStatus(Document serverStatus) {
        this.serverStatus = serverStatus == null ? new Document() : serverStatus;
    }

    public Document getConnectionStatus() {
        return connectionStatus;
    }

    public void setConnectionStatus(Document connectionStatus) {
        this.connectionStatus = connectionStatus == null ? new Document() : connectionStatus;
    }

    public Document getDefaultReadWriteConcern() {
        return defaultReadWriteConcern;
    }

    public void setDefaultReadWriteConcern(Document defaultReadWriteConcern) {
        this.defaultReadWriteConcern = defaultReadWriteConcern == null ? new Document() : defaultReadWriteConcern;
    }

    public List<Document> getNamespaceUsage() {
        return namespaceUsage;
    }

    public void setNamespaceUsage(List<Document> namespaceUsage) {
        this.namespaceUsage = namespaceUsage == null ? new ArrayList<>() : namespaceUsage;
    }

    public List<Document> getQueryStats() {
        return queryStats;
    }

    public void setQueryStats(List<Document> queryStats) {
        this.queryStats = queryStats == null ? new ArrayList<>() : queryStats;
    }

    public List<DatabaseInfo> getDatabases() {
        return databases;
    }

    public void setDatabases(List<DatabaseInfo> databases) {
        this.databases = databases == null ? new ArrayList<>() : databases;
    }

    public List<ProfileSample> getProfileSamples() {
        return profileSamples;
    }

    public void setProfileSamples(List<ProfileSample> profileSamples) {
        this.profileSamples = profileSamples == null ? new ArrayList<>() : profileSamples;
    }

    public List<QueryShape> getQueryShapes() {
        return queryShapes;
    }

    public void setQueryShapes(List<QueryShape> queryShapes) {
        this.queryShapes = queryShapes == null ? new ArrayList<>() : queryShapes;
    }

    public List<RuntimeMetric> getRuntimeMetrics() {
        return runtimeMetrics;
    }

    public void setRuntimeMetrics(List<RuntimeMetric> runtimeMetrics) {
        this.runtimeMetrics = runtimeMetrics == null ? new ArrayList<>() : runtimeMetrics;
    }

    public List<CommandError> getCommandErrors() {
        return commandErrors;
    }

    public void setCommandErrors(List<CommandError> commandErrors) {
        this.commandErrors = commandErrors == null ? new ArrayList<>() : commandErrors;
    }
}
