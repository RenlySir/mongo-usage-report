package com.example.mongousage.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ValidationUtils Tests")
class ValidationUtilsTest {

    @Test
    @DisplayName("Valid MongoDB URI should pass validation")
    void validateUri_validUri_success() {
        // Valid URIs
        ValidationUtils.validateUri("mongodb://localhost:27017");
        ValidationUtils.validateUri("mongodb://user:pass@localhost:27017");
        ValidationUtils.validateUri("mongodb://user:pass@host:27017/db?authSource=admin");
        ValidationUtils.validateUri("mongodb://localhost,localhost:27018,localhost:27019/?replicaSet=myReplicaSet");
        ValidationUtils.validateUri("mongodb+srv://cluster.example.com/");
    }

    @Test
    @DisplayName("Invalid MongoDB URI should throw exception")
    void validateUri_invalidUri_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateUri(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or empty");

        assertThatThrownBy(() -> ValidationUtils.validateUri(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or empty");

        assertThatThrownBy(() -> ValidationUtils.validateUri("not-a-valid-uri"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid MongoDB URI");
    }

    @Test
    @DisplayName("Valid MongoDB version should pass validation")
    void validateMongoVersion_validVersion_success() {
        ValidationUtils.validateMongoVersion("4");
        ValidationUtils.validateMongoVersion("4.4");
        ValidationUtils.validateMongoVersion("5");
        ValidationUtils.validateMongoVersion("6");
        ValidationUtils.validateMongoVersion("6.0");
        ValidationUtils.validateMongoVersion("6.0.7");
        ValidationUtils.validateMongoVersion("7");
        ValidationUtils.validateMongoVersion("7.0.5");
    }

    @Test
    @DisplayName("Invalid MongoDB version should throw exception")
    void validateMongoVersion_invalidVersion_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateMongoVersion(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or empty");

        assertThatThrownBy(() -> ValidationUtils.validateMongoVersion(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or empty");

        assertThatThrownBy(() -> ValidationUtils.validateMongoVersion("invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid MongoDB version format");

        assertThatThrownBy(() -> ValidationUtils.validateMongoVersion("4.4.5.6"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid MongoDB version format");
    }

    @Test
    @DisplayName("Valid sample limit should pass validation")
    void validateSampleLimit_validLimit_success() {
        ValidationUtils.validateSampleLimit(0);
        ValidationUtils.validateSampleLimit(1000);
        ValidationUtils.validateSampleLimit(100000);
    }

    @Test
    @DisplayName("Invalid sample limit should throw exception")
    void validateSampleLimit_invalidLimit_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateSampleLimit(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be non-negative");

        assertThatThrownBy(() -> ValidationUtils.validateSampleLimit(100001))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too large");
    }

    @Test
    @DisplayName("Valid profile seconds should pass validation")
    void validateProfileSeconds_validSeconds_success() {
        ValidationUtils.validateProfileSeconds(1);
        ValidationUtils.validateProfileSeconds(300);
        ValidationUtils.validateProfileSeconds(3600);
    }

    @Test
    @DisplayName("Invalid profile seconds should throw exception")
    void validateProfileSeconds_invalidSeconds_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateProfileSeconds(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be at least 1");

        assertThatThrownBy(() -> ValidationUtils.validateProfileSeconds(3601))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too large");
    }

    @Test
    @DisplayName("Valid slow ms should pass validation")
    void validateSlowMs_validSlowMs_success() {
        ValidationUtils.validateSlowMs(0);
        ValidationUtils.validateSlowMs(50);
        ValidationUtils.validateSlowMs(10000);
    }

    @Test
    @DisplayName("Invalid slow ms should throw exception")
    void validateSlowMs_invalidSlowMs_throwsException() {
        assertThatThrownBy(() -> ValidationUtils.validateSlowMs(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be non-negative");
    }
}
