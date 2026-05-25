package com.example.mongousage.mongo;

import com.example.mongousage.model.DeploymentInfo;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DeploymentDetector Tests")
class DeploymentDetectorTest {

    @Test
    @DisplayName("Should detect standalone deployment")
    void detectTopology_standalone_detectsStandalone() {
        Document hello = new Document()
                .append("ok", 1.0);
        Document serverStatus = new Document()
                .append("process", "mongod");

        DeploymentInfo info = DeploymentDetector.detectTopology(hello, serverStatus);

        assertThat(info.getDeploymentMode()).isEqualTo("standalone");
        assertThat(info.isSharded()).isFalse();
        assertThat(info.getProcessType()).isEqualTo("mongod");
    }

    @Test
    @DisplayName("Should detect replica set deployment")
    void detectTopology_replicaSet_detectsReplicaSet() {
        Document hello = new Document()
                .append("ok", 1.0)
                .append("setName", "rs0");
        Document serverStatus = new Document()
                .append("process", "mongod");

        DeploymentInfo info = DeploymentDetector.detectTopology(hello, serverStatus);

        assertThat(info.getDeploymentMode()).isEqualTo("replicaSet");
        assertThat(info.getReplicaSetName()).isEqualTo("rs0");
        assertThat(info.isSharded()).isFalse();
    }

    @Test
    @DisplayName("Should detect mongos/sharded deployment")
    void detectTopology_mongos_detectsSharded() {
        Document hello = new Document()
                .append("ok", 1.0)
                .append("msg", "isdbgrid");
        Document serverStatus = new Document()
                .append("process", "mongos");

        DeploymentInfo info = DeploymentDetector.detectTopology(hello, serverStatus);

        assertThat(info.getDeploymentMode()).isEqualTo("sharded");
        assertThat(info.isSharded()).isTrue();
        assertThat(info.getProcessType()).isEqualTo("mongos");
    }

    @Test
    @DisplayName("Should detect Atlas hosting")
    void detectHosting_atlas_detectsAtlas() {
        DeploymentInfo info = new DeploymentInfo();
        Document buildInfo = new Document()
                .append("version", "7.0.5")
                .append("mongodb.net", "atlas");
        Document hello = new Document();
        Document serverStatus = new Document();
        Document getCmdLineOpts = new Document();
        Document hostInfo = new Document();

        DeploymentDetector.detectHosting(info, "mongodb+srv://cluster.mongodb.net",
                buildInfo, hello, serverStatus, getCmdLineOpts, hostInfo);

        assertThat(info.getProvider()).isEqualTo("atlas");
        assertThat(info.getHostingType()).isEqualTo("managed");
        assertThat(info.isManagedService()).isTrue();
    }

    @Test
    @DisplayName("Should detect self-managed deployment")
    void detectHosting_selfManaged_detectsSelfManaged() {
        DeploymentInfo info = new DeploymentInfo();

        DeploymentDetector.detectHosting(info, "mongodb://localhost:27017",
                new Document(), new Document(), new Document(), new Document(), new Document());

        assertThat(info.getProvider()).isEqualTo("self-managed");
        assertThat(info.getHostingType()).isEqualTo("self-managed");
        assertThat(info.isManagedService()).isFalse();
    }

    @Test
    @DisplayName("Should enrich replica set status")
    void enrichReplicaSetStatus_validStatus_extractsMembers() {
        DeploymentInfo info = new DeploymentInfo();
        Document replSetStatus = new Document()
                .append("members", List.of(
                        new Document()
                                .append("name", "mongodb1:27017")
                                .append("stateStr", "PRIMARY"),
                        new Document()
                                .append("name", "mongodb2:27017")
                                .append("stateStr", "SECONDARY"),
                        new Document()
                                .append("name", "mongodb3:27017")
                                .append("stateStr", "ARBITER")
                ));

        DeploymentDetector.enrichReplicaSetStatus(info, replSetStatus);

        assertThat(info.getReplicaSetMemberCount()).isEqualTo(3);
        assertThat(info.getReplicaSetMembers()).containsExactly("mongodb1:27017:PRIMARY",
                "mongodb2:27017:SECONDARY", "mongodb3:27017:ARBITER");
    }

    @Test
    @DisplayName("Should enrich shard list")
    void enrichShardList_validShards_extractsShardNames() {
        DeploymentInfo info = new DeploymentInfo();
        Document shardList = new Document()
                .append("shards", List.of(
                        new Document().append("_id", "shard01"),
                        new Document().append("_id", "shard02"),
                        new Document().append("_id", "shard03")
                ));

        DeploymentDetector.enrichShardList(info, shardList);

        assertThat(info.getShardCount()).isEqualTo(3);
        assertThat(info.getShardNames()).containsExactly("shard01", "shard02", "shard03");
    }

    @Test
    @DisplayName("Should handle null inputs gracefully")
    void detectTopology_nullInputs_returnsDefaultInfo() {
        DeploymentInfo info = DeploymentDetector.detectTopology(null, null);

        assertThat(info.getDeploymentMode()).isEqualTo("unknown");
        assertThat(info.getProcessType()).isEmpty();
    }

    @Test
    @DisplayName("Should detect primary node role")
    void detectTopology_primary_detectsPrimary() {
        Document hello = new Document()
                .append("setName", "rs0")
                .append("isWritablePrimary", true);
        Document serverStatus = new Document();

        DeploymentInfo info = DeploymentDetector.detectTopology(hello, serverStatus);

        assertThat(info.getNodeRole()).isEqualTo("primary");
    }

    @Test
    @DisplayName("Should detect secondary node role")
    void detectTopology_secondary_detectsSecondary() {
        Document hello = new Document()
                .append("setName", "rs0")
                .append("secondary", true);
        Document serverStatus = new Document();

        DeploymentInfo info = DeploymentDetector.detectTopology(hello, serverStatus);

        assertThat(info.getNodeRole()).isEqualTo("secondary");
    }
}
