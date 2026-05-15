package com.example.mongousage.model;

import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public class DeploymentInfo {
    private String deploymentMode = "unknown";
    private String replicaSetName = "";
    private String primary = "";
    private List<String> hosts = new ArrayList<>();
    private List<String> arbiters = new ArrayList<>();
    private boolean sharded;
    private String atlasHint = "";
    private String storageEngine = "";
    private String featureCompatibilityVersion = "";
    private Document replSetStatus = new Document();
    private Document shardList = new Document();
    private Document getCmdLineOpts = new Document();
    private Document hostInfo = new Document();

    public String getDeploymentMode() {
        return deploymentMode;
    }

    public void setDeploymentMode(String deploymentMode) {
        this.deploymentMode = deploymentMode == null ? "unknown" : deploymentMode;
    }

    public String getReplicaSetName() {
        return replicaSetName;
    }

    public void setReplicaSetName(String replicaSetName) {
        this.replicaSetName = replicaSetName == null ? "" : replicaSetName;
    }

    public String getPrimary() {
        return primary;
    }

    public void setPrimary(String primary) {
        this.primary = primary == null ? "" : primary;
    }

    public List<String> getHosts() {
        return hosts;
    }

    public void setHosts(List<String> hosts) {
        this.hosts = hosts == null ? new ArrayList<>() : hosts;
    }

    public List<String> getArbiters() {
        return arbiters;
    }

    public void setArbiters(List<String> arbiters) {
        this.arbiters = arbiters == null ? new ArrayList<>() : arbiters;
    }

    public boolean isSharded() {
        return sharded;
    }

    public void setSharded(boolean sharded) {
        this.sharded = sharded;
    }

    public String getAtlasHint() {
        return atlasHint;
    }

    public void setAtlasHint(String atlasHint) {
        this.atlasHint = atlasHint == null ? "" : atlasHint;
    }

    public String getStorageEngine() {
        return storageEngine;
    }

    public void setStorageEngine(String storageEngine) {
        this.storageEngine = storageEngine == null ? "" : storageEngine;
    }

    public String getFeatureCompatibilityVersion() {
        return featureCompatibilityVersion;
    }

    public void setFeatureCompatibilityVersion(String featureCompatibilityVersion) {
        this.featureCompatibilityVersion = featureCompatibilityVersion == null ? "" : featureCompatibilityVersion;
    }

    public Document getReplSetStatus() {
        return replSetStatus;
    }

    public void setReplSetStatus(Document replSetStatus) {
        this.replSetStatus = replSetStatus == null ? new Document() : replSetStatus;
    }

    public Document getShardList() {
        return shardList;
    }

    public void setShardList(Document shardList) {
        this.shardList = shardList == null ? new Document() : shardList;
    }

    public Document getGetCmdLineOpts() {
        return getCmdLineOpts;
    }

    public void setGetCmdLineOpts(Document getCmdLineOpts) {
        this.getCmdLineOpts = getCmdLineOpts == null ? new Document() : getCmdLineOpts;
    }

    public Document getHostInfo() {
        return hostInfo;
    }

    public void setHostInfo(Document hostInfo) {
        this.hostInfo = hostInfo == null ? new Document() : hostInfo;
    }
}
