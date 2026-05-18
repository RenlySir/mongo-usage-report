package com.example.mongousage.io;

import com.example.mongousage.model.CollectionInfo;
import com.example.mongousage.model.CommandError;
import com.example.mongousage.model.DatabaseInfo;
import com.example.mongousage.model.IndexInfo;
import com.example.mongousage.model.ProfileSample;
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
import java.util.List;

/**
 * Persists collected MongoDB usage information as an Excel workbook.
 */
public class CollectExcelWriter {
    public void write(UsageReport report, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.toAbsolutePath().getParent());
        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle keyStyle = keyStyle(workbook);
            CellStyle wrapStyle = wrapStyle(workbook);

            writeOverview(workbook, report, headerStyle, keyStyle);
            writeDeployment(workbook, report, headerStyle, keyStyle, wrapStyle);
            writeRuntimeMetrics(workbook, report, headerStyle);
            writeDatabases(workbook, report, headerStyle);
            writeCollections(workbook, report, headerStyle, wrapStyle);
            writeIndexes(workbook, report, headerStyle, wrapStyle);
            writeNamespaceUsage(workbook, report, headerStyle, wrapStyle);
            writeQueryStats(workbook, report, headerStyle, wrapStyle);
            writeQueryShapes(workbook, report, headerStyle, wrapStyle);
            writeWorkload(workbook, report, headerStyle, wrapStyle);
            writeCommandErrors(workbook, report, headerStyle, wrapStyle);

            try (OutputStream out = Files.newOutputStream(outputFile)) {
                workbook.write(out);
            }
        }
    }

    private void writeOverview(Workbook workbook, UsageReport report, CellStyle headerStyle, CellStyle keyStyle) {
        Sheet sheet = workbook.createSheet("Overview");
        int row = 0;
        row = keyValue(sheet, row, "Generated At", String.valueOf(report.getGeneratedAt()), keyStyle);
        row = keyValue(sheet, row, "Target", report.getTarget(), keyStyle);
        row = keyValue(sheet, row, "Requested MongoDB Version", report.getRequestedMongoVersion(), keyStyle);
        row = keyValue(sheet, row, "Databases Scanned", report.getDatabases().size(), keyStyle);
        row = keyValue(sheet, row, "Collections Scanned", allCollections(report).size(), keyStyle);
        row = keyValue(sheet, row, "Query Shapes", report.getQueryShapes().size(), keyStyle);
        row = keyValue(sheet, row, "Profile Samples", report.getProfileSamples().size(), keyStyle);
        row = keyValue(sheet, row, "Command Errors", report.getCommandErrors().size(), keyStyle);
        row++;

        Row header = sheet.createRow(row++);
        writeHeader(header, headerStyle, "Metric", "Value");
        writeRow(sheet.createRow(row++), "Build Version", string(report.getBuildInfo().get("version")));
        writeRow(sheet.createRow(row++), "Server Git Version", string(report.getBuildInfo().get("gitVersion")));
        writeRow(sheet.createRow(row), "Hello OK", string(report.getHello().get("ok")));
        finishSheet(sheet, 2);
    }

    private void writeDeployment(Workbook workbook, UsageReport report, CellStyle headerStyle, CellStyle keyStyle, CellStyle wrapStyle) {
        Sheet sheet = workbook.createSheet("Deployment");
        int row = 0;
        row = keyValue(sheet, row, "Deployment Mode", report.getDeploymentInfo().getDeploymentMode(), keyStyle);
        row = keyValue(sheet, row, "Hosting Type", report.getDeploymentInfo().getHostingType(), keyStyle);
        row = keyValue(sheet, row, "Provider", report.getDeploymentInfo().getProvider(), keyStyle);
        row = keyValue(sheet, row, "Managed Service", report.getDeploymentInfo().isManagedService(), keyStyle);
        row = keyValue(sheet, row, "Process Type", report.getDeploymentInfo().getProcessType(), keyStyle);
        row = keyValue(sheet, row, "Node Role", report.getDeploymentInfo().getNodeRole(), keyStyle);
        row = keyValue(sheet, row, "Replica Set Name", report.getDeploymentInfo().getReplicaSetName(), keyStyle);
        row = keyValue(sheet, row, "Replica Set Member Count", report.getDeploymentInfo().getReplicaSetMemberCount(), keyStyle);
        row = keyValue(sheet, row, "Replica Set Members", String.join(", ", report.getDeploymentInfo().getReplicaSetMembers()), keyStyle);
        row = keyValue(sheet, row, "Primary", report.getDeploymentInfo().getPrimary(), keyStyle);
        row = keyValue(sheet, row, "Hosts", String.join(", ", report.getDeploymentInfo().getHosts()), keyStyle);
        row = keyValue(sheet, row, "Arbiters", String.join(", ", report.getDeploymentInfo().getArbiters()), keyStyle);
        row = keyValue(sheet, row, "Sharded", report.getDeploymentInfo().isSharded(), keyStyle);
        row = keyValue(sheet, row, "Shard Count", report.getDeploymentInfo().getShardCount(), keyStyle);
        row = keyValue(sheet, row, "Shard Names", String.join(", ", report.getDeploymentInfo().getShardNames()), keyStyle);
        row = keyValue(sheet, row, "Atlas Hint", report.getDeploymentInfo().getAtlasHint(), keyStyle);
        row = keyValue(sheet, row, "Storage Engine", report.getDeploymentInfo().getStorageEngine(), keyStyle);
        row = keyValue(sheet, row, "Feature Compatibility Version", report.getDeploymentInfo().getFeatureCompatibilityVersion(), keyStyle);
        row = keyValue(sheet, row, "Detection Signals", String.join(", ", report.getDeploymentInfo().getDeploymentSignals()), keyStyle);
        row++;

        writeHeader(sheet.createRow(row++), headerStyle, "Raw Item", "JSON");
        writeRow(sheet.createRow(row++), "replSetGetStatus", json(report.getDeploymentInfo().getReplSetStatus()));
        writeRow(sheet.createRow(row++), "listShards", json(report.getDeploymentInfo().getShardList()));
        writeRow(sheet.createRow(row++), "getCmdLineOpts", json(report.getDeploymentInfo().getGetCmdLineOpts()));
        writeRow(sheet.createRow(row++), "hostInfo", json(report.getDeploymentInfo().getHostInfo()));
        writeRow(sheet.createRow(row++), "connectionStatus", json(report.getConnectionStatus()));
        writeRow(sheet.createRow(row), "defaultReadWriteConcern", json(report.getDefaultReadWriteConcern()));
        for (int i = 0; i <= sheet.getLastRowNum(); i++) {
            Row data = sheet.getRow(i);
            if (data != null && data.getCell(1) != null) {
                data.getCell(1).setCellStyle(wrapStyle);
            }
        }
        finishSheet(sheet, 2);
    }

    private void writeRuntimeMetrics(Workbook workbook, UsageReport report, CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet("Runtime Metrics");
        writeHeader(sheet.createRow(0), headerStyle, "Category", "Metric", "Value");
        int row = 1;
        for (RuntimeMetric metric : report.getRuntimeMetrics()) {
            writeRow(sheet.createRow(row++), metric.category(), metric.name(), metric.value());
        }
        finishSheet(sheet, 3);
    }

    private void writeDatabases(Workbook workbook, UsageReport report, CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet("Databases");
        writeHeader(sheet.createRow(0), headerStyle, "Database", "Collections", "Indexes", "Data Size", "Storage Size", "Objects");
        int row = 1;
        for (DatabaseInfo db : report.getDatabases()) {
            long indexes = db.getCollections().stream().mapToLong(c -> c.getIndexes().size()).sum();
            writeRow(sheet.createRow(row++), db.getName(), db.getCollections().size(), indexes,
                    number(db.getStats(), "dataSize"), number(db.getStats(), "storageSize"), number(db.getStats(), "objects"));
        }
        finishSheet(sheet, 6);
    }

    private void writeCollections(Workbook workbook, UsageReport report, CellStyle headerStyle, CellStyle wrapStyle) {
        Sheet sheet = workbook.createSheet("Collections");
        writeHeader(sheet.createRow(0), headerStyle, "Namespace", "Type", "Count", "Storage Size", "Total Index Size", "Avg Object Size", "Options");
        int row = 1;
        for (CollectionInfo collection : allCollections(report)) {
            Row data = sheet.createRow(row++);
            writeRow(data, collection.getNamespace(), collection.getType(), number(collection.getStats(), "count"),
                    number(collection.getStats(), "storageSize"), number(collection.getStats(), "totalIndexSize"),
                    number(collection.getStats(), "avgObjSize"), json(collection.getOptions()));
            data.getCell(6).setCellStyle(wrapStyle);
        }
        finishSheet(sheet, 7);
    }

    private void writeNamespaceUsage(Workbook workbook, UsageReport report, CellStyle headerStyle, CellStyle wrapStyle) {
        Sheet sheet = workbook.createSheet("Namespace Usage");
        writeHeader(sheet.createRow(0), headerStyle, "Namespace", "Usage JSON");
        int row = 1;
        for (Document usage : report.getNamespaceUsage()) {
            Row data = sheet.createRow(row++);
            writeRow(data, string(usage.get("namespace")), json(asDocument(usage.get("usage"))));
            data.getCell(1).setCellStyle(wrapStyle);
        }
        finishSheet(sheet, 2);
    }

    private void writeQueryStats(Workbook workbook, UsageReport report, CellStyle headerStyle, CellStyle wrapStyle) {
        Sheet sheet = workbook.createSheet("Query Stats");
        writeHeader(sheet.createRow(0), headerStyle, "Query Shape", "Stats JSON");
        int row = 1;
        for (Document stats : report.getQueryStats()) {
            Row data = sheet.createRow(row++);
            writeRow(data, json(asDocument(stats.get("key"))), json(stats));
            data.getCell(0).setCellStyle(wrapStyle);
            data.getCell(1).setCellStyle(wrapStyle);
        }
        finishSheet(sheet, 2);
    }

    private void writeQueryShapes(Workbook workbook, UsageReport report, CellStyle headerStyle, CellStyle wrapStyle) {
        Sheet sheet = workbook.createSheet("Query Shapes");
        writeHeader(sheet.createRow(0), headerStyle, "Namespace", "Operation", "Samples", "Avg ms", "Max ms",
                "Avg Docs Examined", "Avg Keys Examined", "Avg Returned", "Features", "Shape");
        int row = 1;
        for (QueryShape shape : report.getQueryShapes()) {
            Row data = sheet.createRow(row++);
            writeRow(data, shape.getNamespace(), shape.getOperation(), shape.getSampleCount(), shape.getAvgMillis(), shape.getMaxMillis(),
                    shape.getAvgDocsExamined(), shape.getAvgKeysExamined(), shape.getAvgReturned(),
                    String.join(", ", shape.getFeatures()), shape.getShape());
            data.getCell(9).setCellStyle(wrapStyle);
        }
        finishSheet(sheet, 10);
    }

    private void writeIndexes(Workbook workbook, UsageReport report, CellStyle headerStyle, CellStyle wrapStyle) {
        Sheet sheet = workbook.createSheet("Indexes");
        writeHeader(sheet.createRow(0), headerStyle, "Namespace", "Index Name", "Key", "Unique", "TTL Seconds", "Partial Filter", "Collation");
        int row = 1;
        for (CollectionInfo collection : allCollections(report)) {
            for (IndexInfo index : collection.getIndexes()) {
                Row data = sheet.createRow(row++);
                writeRow(data, collection.getNamespace(), index.getName(), json(index.getKey()),
                        index.getRaw().get("unique"), index.getRaw().get("expireAfterSeconds"),
                        json(asDocument(index.getRaw().get("partialFilterExpression"))),
                        json(asDocument(index.getRaw().get("collation"))));
                data.getCell(2).setCellStyle(wrapStyle);
            }
        }
        finishSheet(sheet, 7);
    }

    private void writeWorkload(Workbook workbook, UsageReport report, CellStyle headerStyle, CellStyle wrapStyle) {
        Sheet sheet = workbook.createSheet("Workload");
        writeHeader(sheet.createRow(0), headerStyle, "Database", "Namespace", "Operation", "Millis", "Docs Examined", "Keys Examined", "Returned", "Command");
        int row = 1;
        for (ProfileSample sample : report.getProfileSamples()) {
            Row data = sheet.createRow(row++);
            writeRow(data, sample.getDatabase(), sample.getNamespace(), sample.getOperation(), sample.getMillis(),
                    sample.getDocsExamined(), sample.getKeysExamined(), sample.getNreturned(), json(sample.getCommand()));
            data.getCell(7).setCellStyle(wrapStyle);
        }
        finishSheet(sheet, 8);
    }

    private void writeCommandErrors(Workbook workbook, UsageReport report, CellStyle headerStyle, CellStyle wrapStyle) {
        Sheet sheet = workbook.createSheet("Command Errors");
        writeHeader(sheet.createRow(0), headerStyle, "Scope", "Command", "Message");
        int row = 1;
        for (CommandError error : report.getCommandErrors()) {
            Row data = sheet.createRow(row++);
            writeRow(data, error.scope(), error.command(), error.message());
            data.getCell(2).setCellStyle(wrapStyle);
        }
        finishSheet(sheet, 3);
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

    private String json(Document document) {
        return document == null || document.isEmpty() ? "" : document.toJson();
    }

    private Document asDocument(Object value) {
        return value instanceof Document document ? document : new Document();
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
