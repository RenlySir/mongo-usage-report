package com.example.mongousage.io;

import com.example.mongousage.model.CollectionInfo;
import com.example.mongousage.model.DatabaseInfo;
import com.example.mongousage.model.IndexInfo;
import com.example.mongousage.model.QueryShape;
import com.example.mongousage.model.RuntimeMetric;
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
import org.bson.Document;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UsageSummaryExcelWriter {
    public void write(UsageReport report, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.toAbsolutePath().getParent());
        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle keyStyle = keyStyle(workbook);
            CellStyle wrapStyle = wrapStyle(workbook);

            writeExecutiveSummary(workbook, report, keyStyle);
            writeFeatureSummary(workbook, report, headerStyle);
            writeTopCollections(workbook, report, headerStyle);
            writeTopQueryShapes(workbook, report, headerStyle, wrapStyle);
            writeRisks(workbook, report, headerStyle, wrapStyle);

            try (OutputStream out = Files.newOutputStream(outputFile)) {
                workbook.write(out);
            }
        }
    }

    private void writeExecutiveSummary(Workbook workbook, UsageReport report, CellStyle keyStyle) {
        Sheet sheet = workbook.createSheet("Executive Summary");
        int row = 0;
        row = keyValue(sheet, row, "Generated At", String.valueOf(report.getGeneratedAt()), keyStyle);
        row = keyValue(sheet, row, "Target", report.getTarget(), keyStyle);
        row = keyValue(sheet, row, "Requested MongoDB Version", report.getRequestedMongoVersion(), keyStyle);
        row = keyValue(sheet, row, "Server Version", string(report.getBuildInfo().get("version")), keyStyle);
        row = keyValue(sheet, row, "Deployment Mode", report.getDeploymentInfo().getDeploymentMode(), keyStyle);
        row = keyValue(sheet, row, "Hosting Type", report.getDeploymentInfo().getHostingType(), keyStyle);
        row = keyValue(sheet, row, "Provider", report.getDeploymentInfo().getProvider(), keyStyle);
        row = keyValue(sheet, row, "Replica Set Members", report.getDeploymentInfo().getReplicaSetMemberCount(), keyStyle);
        row = keyValue(sheet, row, "Shard Count", report.getDeploymentInfo().getShardCount(), keyStyle);
        row = keyValue(sheet, row, "Database Count", report.getDatabases().size(), keyStyle);
        row = keyValue(sheet, row, "Collection Count", collections(report).size(), keyStyle);
        row = keyValue(sheet, row, "Index Count", indexCount(report), keyStyle);
        row = keyValue(sheet, row, "Total Documents", sumCollectionStat(report, "count"), keyStyle);
        row = keyValue(sheet, row, "Total Storage Size", sumCollectionStat(report, "storageSize"), keyStyle);
        row = keyValue(sheet, row, "Query Shapes", report.getQueryShapes().size(), keyStyle);
        row = keyValue(sheet, row, "Profile Samples", report.getProfileSamples().size(), keyStyle);
        row = keyValue(sheet, row, "Command Errors", report.getCommandErrors().size(), keyStyle);
        row = keyValue(sheet, row, "Skipped Diagnostics", report.getSkippedDiagnostics().size(), keyStyle);
        keyValue(sheet, row, "Risk Items", riskItems(report).size(), keyStyle);
        finishSheet(sheet, 2);
    }

    private void writeFeatureSummary(Workbook workbook, UsageReport report, CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet("Feature Summary");
        writeHeader(sheet.createRow(0), headerStyle, "Feature", "Detected", "Evidence");
        int row = 1;
        Map<String, String> features = detectedFeatures(report);
        for (Map.Entry<String, String> entry : features.entrySet()) {
            writeRow(sheet.createRow(row++), entry.getKey(), !entry.getValue().isBlank() ? "Yes" : "No", entry.getValue());
        }
        finishSheet(sheet, 3);
    }

    private void writeTopCollections(Workbook workbook, UsageReport report, CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet("Top Collections");
        writeHeader(sheet.createRow(0), headerStyle, "Namespace", "Type", "Documents", "Storage Size", "Index Size", "Index Count");
        int row = 1;
        for (CollectionInfo collection : collections(report).stream()
                .sorted(Comparator.comparingLong((CollectionInfo c) -> number(c.getStats(), "storageSize")).reversed())
                .limit(50)
                .toList()) {
            writeRow(sheet.createRow(row++),
                    collection.getNamespace(),
                    collection.getType(),
                    number(collection.getStats(), "count"),
                    number(collection.getStats(), "storageSize"),
                    number(collection.getStats(), "totalIndexSize"),
                    collection.getIndexes().size());
        }
        finishSheet(sheet, 6);
    }

    private void writeTopQueryShapes(Workbook workbook, UsageReport report, CellStyle headerStyle, CellStyle wrapStyle) {
        Sheet sheet = workbook.createSheet("Top Query Shapes");
        writeHeader(sheet.createRow(0), headerStyle, "Namespace", "Operation", "Samples", "Avg ms", "Max ms", "Features", "Shape");
        int row = 1;
        for (QueryShape shape : report.getQueryShapes().stream()
                .sorted(Comparator.comparingLong(QueryShape::getMaxMillis).reversed()
                        .thenComparing(Comparator.comparingInt(QueryShape::getSampleCount).reversed()))
                .limit(50)
                .toList()) {
            Row data = sheet.createRow(row++);
            writeRow(data,
                    shape.getNamespace(),
                    shape.getOperation(),
                    shape.getSampleCount(),
                    shape.getAvgMillis(),
                    shape.getMaxMillis(),
                    String.join(", ", shape.getFeatures()),
                    shape.getShape());
            data.getCell(6).setCellStyle(wrapStyle);
        }
        finishSheet(sheet, 7);
    }

    private void writeRisks(Workbook workbook, UsageReport report, CellStyle headerStyle, CellStyle wrapStyle) {
        Sheet sheet = workbook.createSheet("Risks");
        writeHeader(sheet.createRow(0), headerStyle, "Severity", "Area", "Observation", "Suggested Review");
        int row = 1;
        for (RiskItem risk : riskItems(report)) {
            Row data = sheet.createRow(row++);
            writeRow(data, risk.severity(), risk.area(), risk.observation(), risk.suggestedReview());
            data.getCell(2).setCellStyle(wrapStyle);
            data.getCell(3).setCellStyle(wrapStyle);
        }
        finishSheet(sheet, 4);
    }

    private Map<String, String> detectedFeatures(UsageReport report) {
        Map<String, String> features = new LinkedHashMap<>();
        features.put("jsonSchema validation", collections(report).stream()
                .filter(collection -> collection.getOptions().toJson().contains("$jsonSchema"))
                .map(CollectionInfo::getNamespace)
                .findFirst()
                .orElse(""));
        features.put("unique indexes", indexes(report).stream()
                .filter(index -> Boolean.TRUE.equals(index.getRaw().get("unique")))
                .map(IndexInfo::getName)
                .findFirst()
                .orElse(""));
        features.put("TTL indexes", indexes(report).stream()
                .filter(index -> index.getRaw().containsKey("expireAfterSeconds"))
                .map(IndexInfo::getName)
                .findFirst()
                .orElse(""));
        features.put("partial indexes", indexes(report).stream()
                .filter(index -> index.getRaw().containsKey("partialFilterExpression"))
                .map(IndexInfo::getName)
                .findFirst()
                .orElse(""));
        features.put("query sort", queryFeature(report, "sort"));
        features.put("query projection", queryFeature(report, "projection"));
        features.put("aggregation", report.getQueryShapes().stream()
                .filter(shape -> "aggregate".equalsIgnoreCase(shape.getOperation()) || shape.getShape().contains("aggregate"))
                .map(QueryShape::getNamespace)
                .findFirst()
                .orElse(""));
        features.put("profiling data", report.getProfileSamples().isEmpty() ? "" : report.getProfileSamples().size() + " samples");
        return features;
    }

    private List<RiskItem> riskItems(UsageReport report) {
        List<RiskItem> risks = new java.util.ArrayList<>();
        if (!report.getCommandErrors().isEmpty()) {
            risks.add(new RiskItem("Medium", "Permissions / Compatibility",
                    report.getCommandErrors().size() + " diagnostic commands failed during collection.",
                    "Review Command Errors in the detailed workbook and confirm whether failures are expected for the deployment."));
        }
        for (QueryShape shape : report.getQueryShapes()) {
            if (shape.getAvgDocsExamined() > 0 && shape.getAvgReturned() > 0 && shape.getAvgDocsExamined() > shape.getAvgReturned() * 100) {
                risks.add(new RiskItem("High", "Query Efficiency",
                        "Query shape on " + shape.getNamespace() + " examines far more documents than it returns.",
                        "Review indexes and query predicates before migration cutover."));
            }
        }
        for (CollectionInfo collection : collections(report)) {
            long storageSize = number(collection.getStats(), "storageSize");
            long indexSize = number(collection.getStats(), "totalIndexSize");
            if (storageSize > 0 && indexSize > storageSize * 2) {
                risks.add(new RiskItem("Medium", "Index Footprint",
                        collection.getNamespace() + " has index size more than twice storage size.",
                        "Review unused or duplicate indexes and target storage sizing."));
            }
        }
        if (risks.isEmpty()) {
            risks.add(new RiskItem("Info", "Summary", "No high-signal risks detected from collected metadata.", "Review detailed sheets for workload-specific migration questions."));
        }
        return risks;
    }

    private String queryFeature(UsageReport report, String feature) {
        return report.getQueryShapes().stream()
                .filter(shape -> shape.getFeatures().stream().anyMatch(item -> item.equalsIgnoreCase(feature)))
                .map(QueryShape::getNamespace)
                .findFirst()
                .orElse("");
    }

    private List<CollectionInfo> collections(UsageReport report) {
        return report.getDatabases().stream()
                .flatMap(database -> database.getCollections().stream())
                .toList();
    }

    private List<IndexInfo> indexes(UsageReport report) {
        return collections(report).stream()
                .flatMap(collection -> collection.getIndexes().stream())
                .toList();
    }

    private long indexCount(UsageReport report) {
        return indexes(report).size();
    }

    private long sumCollectionStat(UsageReport report, String key) {
        return collections(report).stream().mapToLong(collection -> number(collection.getStats(), key)).sum();
    }

    private long number(Document document, String key) {
        Object value = document == null ? null : document.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
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

    private record RiskItem(String severity, String area, String observation, String suggestedReview) {
    }
}
