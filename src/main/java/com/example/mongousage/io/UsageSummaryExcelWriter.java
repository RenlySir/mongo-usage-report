package com.example.mongousage.io;

import com.example.mongousage.model.UsageReport;
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

public class UsageSummaryExcelWriter {
    public void write(UsageReport report, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.toAbsolutePath().getParent());
        UsageSummaryData summary = UsageSummaryData.from(report);
        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle keyStyle = keyStyle(workbook);
            CellStyle wrapStyle = wrapStyle(workbook);

            writeExecutiveSummary(workbook, summary, keyStyle);
            writeFeatureSummary(workbook, summary, headerStyle);
            writeTopCollections(workbook, summary, headerStyle);
            writeTopQueryShapes(workbook, summary, headerStyle, wrapStyle);
            writeRisks(workbook, summary, headerStyle, wrapStyle);

            try (OutputStream out = Files.newOutputStream(outputFile)) {
                workbook.write(out);
            }
        }
    }

    private void writeExecutiveSummary(Workbook workbook, UsageSummaryData summary, CellStyle keyStyle) {
        Sheet sheet = workbook.createSheet("Executive Summary");
        int row = 0;
        for (UsageSummaryData.KeyValue item : summary.executiveSummary()) {
            row = keyValue(sheet, row, item.key(), item.value(), keyStyle);
        }
        finishSheet(sheet, 2);
    }

    private void writeFeatureSummary(Workbook workbook, UsageSummaryData summary, CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet("Feature Summary");
        writeHeader(sheet.createRow(0), headerStyle, "Feature", "Detected", "Evidence");
        int row = 1;
        for (UsageSummaryData.FeatureItem item : summary.featureSummary()) {
            writeRow(sheet.createRow(row++), item.feature(), item.detected() ? "Yes" : "No", item.evidence());
        }
        finishSheet(sheet, 3);
    }

    private void writeTopCollections(Workbook workbook, UsageSummaryData summary, CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet("Top Collections");
        writeHeader(sheet.createRow(0), headerStyle, "Namespace", "Type", "Documents", "Storage Size", "Index Size", "Index Count");
        int row = 1;
        for (UsageSummaryData.CollectionItem collection : summary.topCollections()) {
            writeRow(sheet.createRow(row++),
                    collection.namespace(),
                    collection.type(),
                    collection.documents(),
                    collection.storageSize(),
                    collection.indexSize(),
                    collection.indexCount());
        }
        finishSheet(sheet, 6);
    }

    private void writeTopQueryShapes(Workbook workbook, UsageSummaryData summary, CellStyle headerStyle, CellStyle wrapStyle) {
        Sheet sheet = workbook.createSheet("Top Query Shapes");
        writeHeader(sheet.createRow(0), headerStyle, "Namespace", "Operation", "Samples", "Avg ms", "Max ms", "Features", "Shape");
        int row = 1;
        for (UsageSummaryData.QueryShapeItem shape : summary.topQueryShapes()) {
            Row data = sheet.createRow(row++);
            writeRow(data,
                    shape.namespace(),
                    shape.operation(),
                    shape.samples(),
                    shape.avgMillis(),
                    shape.maxMillis(),
                    shape.features(),
                    shape.shape());
            data.getCell(6).setCellStyle(wrapStyle);
        }
        finishSheet(sheet, 7);
    }

    private void writeRisks(Workbook workbook, UsageSummaryData summary, CellStyle headerStyle, CellStyle wrapStyle) {
        Sheet sheet = workbook.createSheet("Risks");
        writeHeader(sheet.createRow(0), headerStyle, "Severity", "Area", "Observation", "Suggested Review");
        int row = 1;
        for (UsageSummaryData.RiskItem risk : summary.risks()) {
            Row data = sheet.createRow(row++);
            writeRow(data, risk.severity(), risk.area(), risk.observation(), risk.suggestedReview());
            data.getCell(2).setCellStyle(wrapStyle);
            data.getCell(3).setCellStyle(wrapStyle);
        }
        finishSheet(sheet, 4);
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
            int maxWidth = i >= 2 ? 22000 : 14000;
            int width = Math.min(Math.max(sheet.getColumnWidth(i), 3000), maxWidth);
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
