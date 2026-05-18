package com.example.mongousage.io;

import com.example.mongousage.compat.MongoCompatTestReport;
import com.example.mongousage.compat.MongoCompatTestResult;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CompatTestExcelWriterTest {
    @Test
    void writesCompatibilityResultsWorkbookWithCommandsAndStatus(@TempDir Path tempDir) throws Exception {
        MongoCompatTestReport report = new MongoCompatTestReport("mongo_usage_compat_test", true, "7");
        report.add(MongoCompatTestResult.passed("query-eq", "query", "$eq", 12));
        report.add(MongoCompatTestResult.failed("aggregation-lookup", "aggregation", "Aggregation lookup", "lookup failed", 34));
        report.add(MongoCompatTestResult.skipped("transaction-commit", "transaction", "Transaction commit", "standalone", 1));

        Path output = tempDir.resolve("compat-test-results.xlsx");
        new CompatTestExcelWriter().write(report, output);

        assertThat(output).exists();
        try (InputStream in = Files.newInputStream(output);
             Workbook workbook = WorkbookFactory.create(in)) {
            assertThat(workbook.getSheet("Summary")).isNotNull();
            Sheet detail = workbook.getSheet("Test Results");
            assertThat(detail).isNotNull();

            DataFormatter formatter = new DataFormatter();
            assertThat(rowText(detail.getRow(0), formatter)).contains(
                    "编号",
                    "用例ID",
                    "mongosh命令",
                    "测试结果",
                    "是否成功"
            );

            Row firstResult = detail.getRow(1);
            assertThat(formatter.formatCellValue(firstResult.getCell(1))).isEqualTo("query-eq");
            assertThat(formatter.formatCellValue(firstResult.getCell(4))).contains("db.orders.countDocuments", "$eq");
            assertThat(formatter.formatCellValue(firstResult.getCell(5))).isEqualTo("PASS");
            assertThat(formatter.formatCellValue(firstResult.getCell(6))).isEqualTo("是");

            Row failedResult = detail.getRow(2);
            assertThat(formatter.formatCellValue(failedResult.getCell(5))).isEqualTo("FAIL");
            assertThat(formatter.formatCellValue(failedResult.getCell(6))).isEqualTo("否");
            assertThat(formatter.formatCellValue(failedResult.getCell(8))).contains("lookup failed");
        }
    }

    private String rowText(Row row, DataFormatter formatter) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < row.getLastCellNum(); i++) {
            if (i > 0) {
                text.append('|');
            }
            text.append(formatter.formatCellValue(row.getCell(i)));
        }
        return text.toString();
    }
}
