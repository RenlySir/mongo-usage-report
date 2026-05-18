package com.example.mongousage.mongo;

import com.example.mongousage.model.DeploymentInfo;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentDetectorTest {
    @Test
    void detectsStandaloneSelfManagedNode() {
        DeploymentInfo info = DeploymentDetector.detectTopology(
                new Document("ok", 1).append("isWritablePrimary", true),
                new Document("process", "mongod"));
        DeploymentDetector.detectHosting(info, "mongodb://127.0.0.1:27017/admin", new Document(), new Document(), new Document(), new Document(), new Document());

        assertThat(info.getDeploymentMode()).isEqualTo("standalone");
        assertThat(info.getHostingType()).isEqualTo("self-managed");
        assertThat(info.getProvider()).isEqualTo("self-managed");
        assertThat(info.getProcessType()).isEqualTo("mongod");
        assertThat(info.getNodeRole()).isEqualTo("standalone");
        assertThat(info.isManagedService()).isFalse();
    }

    @Test
    void detectsReplicaSetAndMemberRoles() {
        DeploymentInfo info = DeploymentDetector.detectTopology(
                new Document("setName", "rs0")
                        .append("isWritablePrimary", true)
                        .append("primary", "mongo-1:27017")
                        .append("hosts", List.of("mongo-1:27017", "mongo-2:27017")),
                new Document("process", "mongod"));
        DeploymentDetector.enrichReplicaSetStatus(info, new Document("members", List.of(
                new Document("name", "mongo-1:27017").append("stateStr", "PRIMARY"),
                new Document("name", "mongo-2:27017").append("stateStr", "SECONDARY"))));

        assertThat(info.getDeploymentMode()).isEqualTo("replicaSet");
        assertThat(info.getReplicaSetName()).isEqualTo("rs0");
        assertThat(info.getNodeRole()).isEqualTo("primary");
        assertThat(info.getReplicaSetMemberCount()).isEqualTo(2);
        assertThat(info.getReplicaSetMembers()).containsExactly("mongo-1:27017:PRIMARY", "mongo-2:27017:SECONDARY");
    }

    @Test
    void detectsAtlasShardedMongos() {
        DeploymentInfo info = DeploymentDetector.detectTopology(
                new Document("msg", "isdbgrid"),
                new Document("process", "mongos"));
        DeploymentDetector.detectHosting(info, "mongodb+srv://cluster0.example.mongodb.net/admin",
                new Document("version", "7.0.11"), new Document(), new Document(), new Document(), new Document());
        DeploymentDetector.enrichShardList(info, new Document("shards", List.of(
                new Document("_id", "shard-a").append("host", "rs-a/a:27017,b:27017"),
                new Document("_id", "shard-b").append("host", "rs-b/c:27017,d:27017"))));

        assertThat(info.getDeploymentMode()).isEqualTo("sharded");
        assertThat(info.getProvider()).isEqualTo("atlas");
        assertThat(info.getHostingType()).isEqualTo("managed");
        assertThat(info.getProcessType()).isEqualTo("mongos");
        assertThat(info.getNodeRole()).isEqualTo("mongos");
        assertThat(info.isManagedService()).isTrue();
        assertThat(info.getShardCount()).isEqualTo(2);
        assertThat(info.getShardNames()).containsExactly("shard-a", "shard-b");
    }

    @Test
    void detectsCompatibleManagedServicesFromConnectionTarget() {
        DeploymentInfo info = DeploymentDetector.detectTopology(new Document("ok", 1), new Document("process", "mongod"));
        DeploymentDetector.detectHosting(info, "mongodb://sample.cluster-docdb.us-east-1.docdb.amazonaws.com:27017/admin",
                new Document(), new Document(), new Document(), new Document(), new Document());

        assertThat(info.getProvider()).isEqualTo("amazon-documentdb");
        assertThat(info.getHostingType()).isEqualTo("managed-compatible");
        assertThat(info.isManagedService()).isTrue();
    }
}
