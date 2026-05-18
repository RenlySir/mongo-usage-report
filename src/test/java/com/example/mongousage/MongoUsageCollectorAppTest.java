package com.example.mongousage;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

class MongoUsageCollectorAppTest {
    @Test
    void helpPrintsUsage() {
        StringWriter out = new StringWriter();
        CommandLine commandLine = new CommandLine(new MongoUsageCollectorApp());
        commandLine.setOut(new PrintWriter(out));

        int exitCode = commandLine.execute("--help");

        assertThat(exitCode).isZero();
        assertThat(out.toString()).contains("mongo-usage-collector");
        assertThat(out.toString()).contains("Commands:");
        assertThat(out.toString()).containsPattern("(?m)^\\s+collect\\s+");
        assertThat(out.toString()).containsPattern("(?m)^\\s+compat-test\\s+");
        assertThat(out.toString()).containsPattern("(?m)^\\s+summarize\\s+");
        assertThat(out.toString()).doesNotContain("--enable-profiler");
        assertThat(out.toString()).doesNotContain("--compat-db");
    }

    @Test
    void collectHelpIsSeparateSubcommand() {
        StringWriter out = new StringWriter();
        CommandLine commandLine = new CommandLine(new MongoUsageCollectorApp());
        commandLine.setOut(new PrintWriter(out));

        int exitCode = commandLine.execute("collect", "--help");

        assertThat(exitCode).isZero();
        assertThat(out.toString()).contains("Usage: mongo-usage-collector collect");
        assertThat(out.toString()).contains("--uri");
        assertThat(out.toString()).contains("--mongo-version");
        assertThat(out.toString()).contains("--enable-profiler");
        assertThat(out.toString()).doesNotContain("--compat-db");
    }

    @Test
    void compatTestHelpIsSeparateSubcommand() {
        StringWriter out = new StringWriter();
        CommandLine commandLine = new CommandLine(new MongoUsageCollectorApp());
        commandLine.setOut(new PrintWriter(out));

        int exitCode = commandLine.execute("compat-test", "--help");

        assertThat(exitCode).isZero();
        assertThat(out.toString()).contains("compat-test");
        assertThat(out.toString()).contains("--compat-db");
        assertThat(out.toString()).contains("--keep-compat-db");
        assertThat(out.toString()).doesNotContain("--enable-profiler");
    }

    @Test
    void summarizeHelpIsSeparateSubcommand() {
        StringWriter out = new StringWriter();
        CommandLine commandLine = new CommandLine(new MongoUsageCollectorApp());
        commandLine.setOut(new PrintWriter(out));

        int exitCode = commandLine.execute("summarize", "--help");

        assertThat(exitCode).isZero();
        assertThat(out.toString()).contains("Usage: mongo-usage-collector summarize");
        assertThat(out.toString()).contains("--report-dir");
        assertThat(out.toString()).doesNotContain("--uri");
        assertThat(out.toString()).doesNotContain("--enable-profiler");
    }
}
