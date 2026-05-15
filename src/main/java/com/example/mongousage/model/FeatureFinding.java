package com.example.mongousage.model;

public class FeatureFinding {
    private String category;
    private String feature;
    private String namespace;
    private String evidence;
    private String risk;

    public FeatureFinding() {
    }

    public FeatureFinding(String category, String feature, String namespace, String evidence, String risk) {
        this.category = category;
        this.feature = feature;
        this.namespace = namespace;
        this.evidence = evidence;
        this.risk = risk;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getFeature() {
        return feature;
    }

    public void setFeature(String feature) {
        this.feature = feature;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getEvidence() {
        return evidence;
    }

    public void setEvidence(String evidence) {
        this.evidence = evidence;
    }

    public String getRisk() {
        return risk;
    }

    public void setRisk(String risk) {
        this.risk = risk;
    }
}
