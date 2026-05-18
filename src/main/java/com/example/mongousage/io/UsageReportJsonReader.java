package com.example.mongousage.io;

import com.example.mongousage.model.UsageReport;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Path;

public class UsageReportJsonReader {
    private final ObjectMapper objectMapper;

    public UsageReportJsonReader() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public UsageReport read(Path rawJsonFile) throws IOException {
        return objectMapper.readValue(rawJsonFile.toFile(), UsageReport.class);
    }
}
