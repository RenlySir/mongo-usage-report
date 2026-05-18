package com.example.mongousage.compat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MongoCompatTestResultTest {
    @Test
    void resultSummaryCountsPassedFailedAndSkippedCases() {
        MongoCompatTestReport report = new MongoCompatTestReport("compat_test", true);
        report.add(MongoCompatTestResult.passed("a", "crud", "insert", 10));
        report.add(MongoCompatTestResult.failed("b", "query", "find", "boom", 20));
        report.add(MongoCompatTestResult.skipped("c", "transaction", "txn", "standalone", 1));

        assertThat(report.total()).isEqualTo(3);
        assertThat(report.passed()).isEqualTo(1);
        assertThat(report.failed()).isEqualTo(1);
        assertThat(report.skipped()).isEqualTo(1);
        assertThat(report.isSuccess()).isFalse();
    }

    @Test
    void retryableWriteErrorMeansTransactionsAreUnsupportedOnStandaloneDeployments() {
        assertThat(MongoCompatTestRunner.isUnsupportedTransactionMessage(
                "This MongoDB deployment does not support retryable writes. Please add retryWrites=false to your connection string."))
                .isTrue();
    }
}
