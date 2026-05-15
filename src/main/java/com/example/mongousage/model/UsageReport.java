package com.example.mongousage.model;

import org.bson.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class UsageReport {
    private Instant generatedAt = Instant.now();
    private String target;
    private Document buildInfo = new Document();
    private Document hello = new Document();
    private Document serverStatus = new Document();
    private List<DatabaseInfo> databases = new ArrayList<>();
    private List<ProfileSample> profileSamples = new ArrayList<>();
    private List<FeatureFinding> featureFindings = new ArrayList<>();
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

    public List<FeatureFinding> getFeatureFindings() {
        return featureFindings;
    }

    public void setFeatureFindings(List<FeatureFinding> featureFindings) {
        this.featureFindings = featureFindings == null ? new ArrayList<>() : featureFindings;
    }

    public List<CommandError> getCommandErrors() {
        return commandErrors;
    }

    public void setCommandErrors(List<CommandError> commandErrors) {
        this.commandErrors = commandErrors == null ? new ArrayList<>() : commandErrors;
    }
}
