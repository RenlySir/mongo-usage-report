package com.example.mongousage.report;

import com.example.mongousage.model.FeatureFinding;
import com.example.mongousage.model.UsageReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

class ReportWriterTest {
    @Test
    void writesExpectedReportFiles(@TempDir Path tempDir) throws Exception {
        UsageReport report = new UsageReport();
        report.setTarget("mongodb://user:****@host/admin");
        report.setFeatureFindings(List.of(new FeatureFinding("Index", "Text index", "db.col", "idx", "HIGH")));

        new ReportWriter().write(report, tempDir);

        assertThat(tempDir.resolve("raw.json")).exists();
        assertThat(tempDir.resolve("inventory.json")).exists();
        assertThat(tempDir.resolve("features.json")).exists();
        assertThat(tempDir.resolve("workload.json")).exists();
        assertThat(tempDir.resolve("summary.md")).exists();
        assertThat(tempDir.resolve("risk-matrix.csv")).exists();
        assertThat(tempDir.resolve("mongo-usage-report.xlsx")).exists();
        assertThat(Files.readString(tempDir.resolve("summary.md"))).contains("Text index");
        assertThat(Files.readString(tempDir.resolve("risk-matrix.csv"))).contains("\"HIGH\",\"Index\",\"Text index\"");

        try (ZipFile workbook = new ZipFile(tempDir.resolve("mongo-usage-report.xlsx").toFile())) {
            String workbookXml = new String(workbook.getInputStream(workbook.getEntry("xl/workbook.xml")).readAllBytes());
            assertThat(workbookXml).contains("Overview", "Databases", "Features", "Workload", "Command Errors");
        }
    }
}
