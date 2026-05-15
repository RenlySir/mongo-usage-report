package com.example.mongousage.report;

import com.example.mongousage.model.CollectionInfo;
import com.example.mongousage.model.CommandError;
import com.example.mongousage.model.DatabaseInfo;
import com.example.mongousage.model.FeatureFinding;
import com.example.mongousage.model.IndexInfo;
import com.example.mongousage.model.ProfileSample;
import com.example.mongousage.model.UsageReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.bson.Document;

import java.io.OutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class ReportWriter {
    private final ObjectMapper objectMapper;

    public ReportWriter() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void write(UsageReport report, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        writeJson(outputDirectory.resolve("raw.json"), report);
        writeJson(outputDirectory.resolve("inventory.json"), inventory(report));
        writeJson(outputDirectory.resolve("features.json"), report.getFeatureFindings());
        writeJson(outputDirectory.resolve("workload.json"), report.getProfileSamples());
        Files.writeString(outputDirectory.resolve("summary.md"), summaryMarkdown(report), StandardCharsets.UTF_8);
        Files.writeString(outputDirectory.resolve("risk-matrix.csv"), riskMatrixCsv(report), StandardCharsets.UTF_8);
        writeExcel(outputDirectory.resolve("mongo-usage-report.xlsx"), report);
    }

    private void writeJson(Path path, Object value) throws IOException {
        objectMapper.writeValue(path.toFile(), value);
    }

    private Map<String, Object> inventory(UsageReport report) {
        long collectionCount = report.getDatabases().stream().mapToLong(db -> db.getCollections().size()).sum();
        long indexCount = report.getDatabases().stream()
                .flatMap(db -> db.getCollections().stream())
                .mapToLong(collection -> collection.getIndexes().size())
                .sum();
        return Map.of(
                "generatedAt", report.getGeneratedAt(),
                "target", report.getTarget(),
                "databaseCount", report.getDatabases().size(),
                "collectionCount", collectionCount,
                "indexCount", indexCount,
                "buildInfo", report.getBuildInfo(),
                "hello", report.getHello(),
                "serverStatus", report.getServerStatus(),
                "databases", report.getDatabases(),
                "commandErrors", report.getCommandErrors()
        );
    }

    private String summaryMarkdown(UsageReport report) {
        StringBuilder out = new StringBuilder();
        out.append("# MongoDB Usage Assessment\n\n");
        out.append("- Generated: ").append(report.getGeneratedAt()).append("\n");
        out.append("- Target: `").append(report.getTarget()).append("`\n");
        out.append("- Databases scanned: ").append(report.getDatabases().size()).append("\n");
        out.append("- Collections scanned: ").append(collectionCount(report)).append("\n");
        out.append("- Profile samples: ").append(report.getProfileSamples().size()).append("\n");
        out.append("- Command errors: ").append(report.getCommandErrors().size()).append("\n\n");

        out.append("## Feature Findings\n\n");
        out.append("| Risk | Category | Feature | Namespace | Evidence |\n");
        out.append("| --- | --- | --- | --- | --- |\n");
        report.getFeatureFindings().stream()
                .sorted(Comparator.comparing(FeatureFinding::getRisk).thenComparing(FeatureFinding::getCategory).thenComparing(FeatureFinding::getFeature))
                .forEach(finding -> out.append("| ")
                        .append(escape(finding.getRisk())).append(" | ")
                        .append(escape(finding.getCategory())).append(" | ")
                        .append(escape(finding.getFeature())).append(" | ")
                        .append(escape(finding.getNamespace())).append(" | ")
                        .append(escape(finding.getEvidence())).append(" |\n"));

        out.append("\n## Database Inventory\n\n");
        out.append("| Database | Collections | Indexes | Data Size | Storage Size |\n");
        out.append("| --- | ---: | ---: | ---: | ---: |\n");
        for (DatabaseInfo db : report.getDatabases()) {
            long indexes = db.getCollections().stream().mapToLong(c -> c.getIndexes().size()).sum();
            out.append("| ").append(escape(db.getName())).append(" | ")
                    .append(db.getCollections().size()).append(" | ")
                    .append(indexes).append(" | ")
                    .append(number(db.getStats(), "dataSize")).append(" | ")
                    .append(number(db.getStats(), "storageSize")).append(" |\n");
        }

        out.append("\n## Largest Collections By Storage\n\n");
        out.append("| Namespace | Count | Storage Size | Total Index Size | Avg Object Size |\n");
        out.append("| --- | ---: | ---: | ---: | ---: |\n");
        report.getDatabases().stream()
                .flatMap(db -> db.getCollections().stream())
                .sorted(Comparator.comparingLong((CollectionInfo c) -> number(c.getStats(), "storageSize")).reversed())
                .limit(20)
                .forEach(collection -> out.append("| ")
                        .append(escape(collection.getNamespace())).append(" | ")
                        .append(number(collection.getStats(), "count")).append(" | ")
                        .append(number(collection.getStats(), "storageSize")).append(" | ")
                        .append(number(collection.getStats(), "totalIndexSize")).append(" | ")
                        .append(number(collection.getStats(), "avgObjSize")).append(" |\n"));

        out.append("\n## Workload Summary\n\n");
        Map<String, Long> ops = report.getProfileSamples().stream()
                .collect(Collectors.groupingBy(ProfileSample::getOperation, TreeMap::new, Collectors.counting()));
        out.append("| Operation | Samples |\n");
        out.append("| --- | ---: |\n");
        ops.forEach((op, count) -> out.append("| ").append(escape(op)).append(" | ").append(count).append(" |\n"));

        if (!report.getCommandErrors().isEmpty()) {
            out.append("\n## Collection Warnings\n\n");
            out.append("Some commands failed because of permissions or server compatibility. See `raw.json` for details.\n");
        }
        return out.toString();
    }

    private String riskMatrixCsv(UsageReport report) {
        StringBuilder out = new StringBuilder("risk,category,feature,namespace,evidence\n");
        for (FeatureFinding finding : report.getFeatureFindings()) {
            out.append(csv(finding.getRisk())).append(',')
                    .append(csv(finding.getCategory())).append(',')
                    .append(csv(finding.getFeature())).append(',')
                    .append(csv(finding.getNamespace())).append(',')
                    .append(csv(finding.getEvidence())).append('\n');
        }
        return out.toString();
    }

    private void writeExcel(Path path, UsageReport report) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle keyStyle = keyStyle(workbook);
            CellStyle wrapStyle = wrapStyle(workbook);

            writeOverviewSheet(workbook, report, headerStyle, keyStyle);
            writeDatabasesSheet(workbook, report, headerStyle);
            writeCollectionsSheet(workbook, report, headerStyle);
            writeIndexesSheet(workbook, report, headerStyle, wrapStyle);
            writeFeaturesSheet(workbook, report, headerStyle);
            writeWorkloadSheet(workbook, report, headerStyle, wrapStyle);
            writeCommandErrorsSheet(workbook, report, headerStyle, wrapStyle);

            try (OutputStream output = Files.newOutputStream(path)) {
                workbook.write(output);
            }
        }
    }

    private void writeOverviewSheet(Workbook workbook, UsageReport report, CellStyle headerStyle, CellStyle keyStyle) {
        Sheet sheet = workbook.createSheet("Overview");
        int rowIndex = 0;
        rowIndex = keyValue(sheet, rowIndex, "Generated At", String.valueOf(report.getGeneratedAt()), keyStyle);
        rowIndex = keyValue(sheet, rowIndex, "Target", report.getTarget(), keyStyle);
        rowIndex = keyValue(sheet, rowIndex, "Databases Scanned", report.getDatabases().size(), keyStyle);
        rowIndex = keyValue(sheet, rowIndex, "Collections Scanned", collectionCount(report), keyStyle);
        rowIndex = keyValue(sheet, rowIndex, "Profile Samples", report.getProfileSamples().size(), keyStyle);
        rowIndex = keyValue(sheet, rowIndex, "Command Errors", report.getCommandErrors().size(), keyStyle);
        rowIndex++;

        Row header = sheet.createRow(rowIndex++);
        writeHeader(header, headerStyle, "Risk", "Category", "Feature", "Namespace", "Evidence");
        for (FeatureFinding finding : report.getFeatureFindings()) {
            Row row = sheet.createRow(rowIndex++);
            writeCells(row, finding.getRisk(), finding.getCategory(), finding.getFeature(), finding.getNamespace(), finding.getEvidence());
        }
        finishSheet(sheet, 5);
    }

    private void writeDatabasesSheet(Workbook workbook, UsageReport report, CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet("Databases");
        Row header = sheet.createRow(0);
        writeHeader(header, headerStyle, "Database", "Collections", "Indexes", "Data Size", "Storage Size", "Objects");
        int rowIndex = 1;
        for (DatabaseInfo db : report.getDatabases()) {
            long indexes = db.getCollections().stream().mapToLong(c -> c.getIndexes().size()).sum();
            Row row = sheet.createRow(rowIndex++);
            writeCells(row, db.getName(), db.getCollections().size(), indexes,
                    number(db.getStats(), "dataSize"), number(db.getStats(), "storageSize"), number(db.getStats(), "objects"));
        }
        finishSheet(sheet, 6);
    }

    private void writeCollectionsSheet(Workbook workbook, UsageReport report, CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet("Collections");
        Row header = sheet.createRow(0);
        writeHeader(header, headerStyle, "Namespace", "Type", "Count", "Storage Size", "Total Index Size", "Avg Object Size", "Options");
        int rowIndex = 1;
        for (CollectionInfo collection : allCollections(report)) {
            Row row = sheet.createRow(rowIndex++);
            writeCells(row, collection.getNamespace(), collection.getType(), number(collection.getStats(), "count"),
                    number(collection.getStats(), "storageSize"), number(collection.getStats(), "totalIndexSize"),
                    number(collection.getStats(), "avgObjSize"), documentJson(collection.getOptions()));
        }
        finishSheet(sheet, 7);
    }

    private void writeIndexesSheet(Workbook workbook, UsageReport report, CellStyle headerStyle, CellStyle wrapStyle) {
        Sheet sheet = workbook.createSheet("Indexes");
        Row header = sheet.createRow(0);
        writeHeader(header, headerStyle, "Namespace", "Index Name", "Key", "Unique", "TTL Seconds", "Partial Filter", "Collation");
        int rowIndex = 1;
        for (CollectionInfo collection : allCollections(report)) {
            for (IndexInfo index : collection.getIndexes()) {
                Row row = sheet.createRow(rowIndex++);
                writeCells(row, collection.getNamespace(), index.getName(), documentJson(index.getKey()),
                        index.getRaw().get("unique"), index.getRaw().get("expireAfterSeconds"),
                        documentJson(document(index.getRaw().get("partialFilterExpression"))),
                        documentJson(document(index.getRaw().get("collation"))));
                row.getCell(2).setCellStyle(wrapStyle);
            }
        }
        finishSheet(sheet, 7);
    }

    private void writeFeaturesSheet(Workbook workbook, UsageReport report, CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet("Features");
        Row header = sheet.createRow(0);
        writeHeader(header, headerStyle, "Risk", "Category", "Feature", "Namespace", "Evidence");
        int rowIndex = 1;
        for (FeatureFinding finding : report.getFeatureFindings()) {
            Row row = sheet.createRow(rowIndex++);
            writeCells(row, finding.getRisk(), finding.getCategory(), finding.getFeature(), finding.getNamespace(), finding.getEvidence());
        }
        finishSheet(sheet, 5);
    }

    private void writeWorkloadSheet(Workbook workbook, UsageReport report, CellStyle headerStyle, CellStyle wrapStyle) {
        Sheet sheet = workbook.createSheet("Workload");
        Row header = sheet.createRow(0);
        writeHeader(header, headerStyle, "Database", "Namespace", "Operation", "Millis", "Docs Examined", "Keys Examined", "Returned", "Command");
        int rowIndex = 1;
        for (ProfileSample sample : report.getProfileSamples()) {
            Row row = sheet.createRow(rowIndex++);
            writeCells(row, sample.getDatabase(), sample.getNamespace(), sample.getOperation(), sample.getMillis(),
                    sample.getDocsExamined(), sample.getKeysExamined(), sample.getNreturned(), documentJson(sample.getCommand()));
            row.getCell(7).setCellStyle(wrapStyle);
        }
        finishSheet(sheet, 8);
    }

    private void writeCommandErrorsSheet(Workbook workbook, UsageReport report, CellStyle headerStyle, CellStyle wrapStyle) {
        Sheet sheet = workbook.createSheet("Command Errors");
        Row header = sheet.createRow(0);
        writeHeader(header, headerStyle, "Scope", "Command", "Message");
        int rowIndex = 1;
        for (CommandError error : report.getCommandErrors()) {
            Row row = sheet.createRow(rowIndex++);
            writeCells(row, error.scope(), error.command(), error.message());
            row.getCell(2).setCellStyle(wrapStyle);
        }
        finishSheet(sheet, 3);
    }

    private long collectionCount(UsageReport report) {
        return report.getDatabases().stream().mapToLong(db -> db.getCollections().size()).sum();
    }

    private List<CollectionInfo> allCollections(UsageReport report) {
        return report.getDatabases().stream()
                .flatMap(db -> db.getCollections().stream())
                .toList();
    }

    private long number(Document document, String key) {
        Object value = document == null ? null : document.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private String documentJson(Document document) {
        return document == null || document.isEmpty() ? "" : document.toJson();
    }

    private Document document(Object value) {
        return value instanceof Document document ? document : new Document();
    }

    private String escape(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replace("|", "\\|").replace("\n", " ");
    }

    private String csv(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private int keyValue(Sheet sheet, int rowIndex, String key, Object value, CellStyle keyStyle) {
        Row row = sheet.createRow(rowIndex);
        Cell keyCell = row.createCell(0);
        keyCell.setCellValue(key);
        keyCell.setCellStyle(keyStyle);
        setCell(row.createCell(1), value);
        return rowIndex + 1;
    }

    private void writeHeader(Row row, CellStyle headerStyle, String... headers) {
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void writeCells(Row row, Object... values) {
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
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(0, sheet.getLastRowNum()), 0, columns - 1));
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
            int width = Math.min(Math.max(sheet.getColumnWidth(i), 3000), 18000);
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
