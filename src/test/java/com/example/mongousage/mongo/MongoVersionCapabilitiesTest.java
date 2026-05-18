package com.example.mongousage.mongo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MongoVersionCapabilitiesTest {
    @Test
    void parsesMajorMinorPatchVersions() {
        assertThat(MongoVersion.parse("4").major()).isEqualTo(4);
        assertThat(MongoVersion.parse("4.4").minor()).isEqualTo(4);
        assertThat(MongoVersion.parse("6.0.7").patch()).isEqualTo(7);
        assertThat(MongoVersion.parse("v7.0").major()).isEqualTo(7);
    }

    @Test
    void rejectsUnsupportedOrInvalidVersions() {
        assertThatThrownBy(() -> MongoVersion.parse("3.6")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MongoVersion.parse("8.0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MongoVersion.parse("latest")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void selectsCompatibleCommandsByVersion() {
        assertThat(MongoVersionCapabilities.forVersion("4").helloCommand()).isEqualTo("isMaster");
        assertThat(MongoVersionCapabilities.forVersion("5").helloCommand()).isEqualTo("hello");
        assertThat(MongoVersionCapabilities.forVersion("6").useCurrentOpAggregation()).isFalse();
        assertThat(MongoVersionCapabilities.forVersion("6.2").useCurrentOpAggregation()).isTrue();
        assertThat(MongoVersionCapabilities.forVersion("7").useCurrentOpAggregation()).isTrue();
    }

    @Test
    void gatesNewerDiagnosticCommands() {
        assertThat(MongoVersionCapabilities.forVersion("4").supportsDefaultReadWriteConcern()).isFalse();
        assertThat(MongoVersionCapabilities.forVersion("4.4").supportsDefaultReadWriteConcern()).isTrue();
        assertThat(MongoVersionCapabilities.forVersion("6").supportsQueryStats()).isFalse();
        assertThat(MongoVersionCapabilities.forVersion("6.0.7").supportsQueryStats()).isTrue();
        assertThat(MongoVersionCapabilities.forVersion("7").supportsQueryStats()).isTrue();
    }
}
