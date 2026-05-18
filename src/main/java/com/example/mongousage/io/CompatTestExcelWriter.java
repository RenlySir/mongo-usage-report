package com.example.mongousage.io;

import com.example.mongousage.compat.MongoCompatTestCaseReference;
import com.example.mongousage.compat.MongoCompatTestReport;
import com.example.mongousage.compat.MongoCompatTestResult;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class CompatTestExcelWriter {
    public void write(MongoCompatTestReport report, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.toAbsolutePath().getParent());
        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle keyStyle = keyStyle(workbook);
            CellStyle wrapStyle = wrapStyle(workbook);

            writeSummary(workbook, report, keyStyle);
            writeResults(workbook, report, headerStyle, wrapStyle);

            try (OutputStream out = Files.newOutputStream(outputFile)) {
                workbook.write(out);
            }
        }
    }

    private void writeSummary(Workbook workbook, MongoCompatTestReport report, CellStyle keyStyle) {
        Sheet sheet = workbook.createSheet("Summary");
        int row = 0;
        row = keyValue(sheet, row, "Generated At", String.valueOf(report.getGeneratedAt()), keyStyle);
        row = keyValue(sheet, row, "MongoDB Version", report.getMongoVersion(), keyStyle);
        row = keyValue(sheet, row, "Compat Database", report.getDatabaseName(), keyStyle);
        row = keyValue(sheet, row, "Drop Database After Run", report.isDropDatabaseAfterRun(), keyStyle);
        row = keyValue(sheet, row, "Total", report.total(), keyStyle);
        row = keyValue(sheet, row, "Passed", report.passed(), keyStyle);
        row = keyValue(sheet, row, "Failed", report.failed(), keyStyle);
        keyValue(sheet, row, "Skipped", report.skipped(), keyStyle);
        finishSheet(sheet, 2);
    }

    private void writeResults(Workbook workbook, MongoCompatTestReport report, CellStyle headerStyle, CellStyle wrapStyle) {
        Sheet sheet = workbook.createSheet("Test Results");
        writeHeader(sheet.createRow(0), headerStyle,
                "编号",
                "用例ID",
                "分类",
                "测试名称",
                "mongosh命令",
                "测试结果",
                "是否成功",
                "耗时ms",
                "错误/跳过原因");
        int row = 1;
        for (MongoCompatTestResult result : report.getResults()) {
            MongoCompatTestCaseReference.Reference reference = MongoCompatTestCaseReference.find(result.id())
                    .orElse(new MongoCompatTestCaseReference.Reference(row, ""));
            Row data = sheet.createRow(row++);
            writeRow(data,
                    reference.formattedNumber(),
                    result.id(),
                    result.category(),
                    result.name(),
                    reference.mongoshCommand(),
                    result.status(),
                    "PASS".equals(result.status()) ? "是" : "否",
                    result.elapsedMillis(),
                    result.message());
            data.getCell(4).setCellStyle(wrapStyle);
            data.getCell(8).setCellStyle(wrapStyle);
        }
        finishSheet(sheet, 9);
    }

    private int keyValue(Sheet sheet, int rowIndex, String key, Object value, CellStyle keyStyle) {
        Row row = sheet.createRow(rowIndex);
        Cell keyCell = row.createCell(0);
        keyCell.setCellValue(key);
        keyCell.setCellStyle(keyStyle);
        setCell(row.createCell(1), value);
        return rowIndex + 1;
    }

    private void writeHeader(Row row, CellStyle style, String... values) {
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(values[i]);
            cell.setCellStyle(style);
        }
    }

    private void writeRow(Row row, Object... values) {
        for (int i = 0; i < values.length; i++) {
            setCell(row.createCell(i), values[i]);
        }
    }

    private void setCell(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else if (value instanceof Boolean bool) {
            cell.setCellValue(bool);
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }

    private void finishSheet(Sheet sheet, int columns) {
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, sheet.getLastRowNum()), 0, columns - 1));
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
            int maxWidth = i == 4 || i == 8 ? 24000 : 12000;
            int width = Math.min(Math.max(sheet.getColumnWidth(i), 2500), maxWidth);
            sheet.setColumnWidth(i, width);
        }
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private CellStyle keyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle wrapStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        return style;
    }
}
