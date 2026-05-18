package com.example.mongousage.io;

import com.example.mongousage.compat.MongoCompatTestReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CompatTestJsonWriter {
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public void write(MongoCompatTestReport report, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        objectMapper.writeValue(outputDirectory.resolve("compat-test-report.json").toFile(), report);
    }
}
