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
        assertThat(out.toString()).contains("--uri");
    }
}
