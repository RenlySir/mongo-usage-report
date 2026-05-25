package com.example.mongousage;

import com.example.mongousage.config.CollectorOptions;
import com.example.mongousage.compat.MongoCompatTestReport;
import com.example.mongousage.compat.MongoCompatTestRunner;
import com.example.mongousage.io.CompatTestExcelWriter;
import com.example.mongousage.io.CompatTestJsonWriter;
import com.example.mongousage.io.CollectExcelWriter;
import com.example.mongousage.io.CollectJsonWriter;
import com.example.mongousage.io.UsageReportJsonReader;
import com.example.mongousage.io.UsageSummaryExcelWriter;
import com.example.mongousage.io.UsageSummaryHtmlWriter;
import com.example.mongousage.model.UsageReport;
import com.example.mongousage.mongo.MongoCollector;
import com.example.mongousage.util.ValidationUtils;
import com.mongodb.client.MongoClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "mongo-usage-collector",
        mixinStandardHelpOptions = true,
        version = "mongo-usage-collector 0.1.0",
        description = "Run MongoDB migration assessment tools.",
        subcommands = {
                MongoUsageCollectorApp.CollectCommand.class,
                MongoUsageCollectorApp.CompatTestCommand.class,
                MongoUsageCollectorApp.SummarizeCommand.class
        }
)
public class MongoUsageCollectorApp implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(MongoUsageCollectorApp.class);

    public static void main(String[] args) {
        int exitCode = new CommandLine(new MongoUsageCollectorApp()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        CommandLine.usage(this, System.out);
        return 0;
    }

    @Command(
            name = "collect",
            mixinStandardHelpOptions = true,
            description = "Collect MongoDB inventory and workload signals only."
    )
    static class CollectCommand implements Callable<Integer> {
        private static final Logger logger = LoggerFactory.getLogger(CollectCommand.class);
        @Option(names = "--uri", description = "MongoDB connection string.", required = true)
        private String uri;

        @Option(names = "--mongo-version", description = "MongoDB server version family to target. Supported: 4, 4.4, 5, 6, 6.0.7, 6.2, 7.", required = true)
        private String mongoVersion;

        @Option(names = "--out", description = "Output directory.", defaultValue = "mongo-usage-report")
        private Path outputDirectory;

        @Option(names = "--include-dbs", split = ",", description = "Comma-separated database allow-list.")
        private List<String> includeDatabases = new ArrayList<>();

        @Option(names = "--exclude-dbs", split = ",", description = "Comma-separated database deny-list.", defaultValue = "local")
        private List<String> excludeDatabases = new ArrayList<>(List.of("local"));

        @Option(names = "--sample-limit", description = "Maximum system.profile samples per database.", defaultValue = "1000")
        private int sampleLimit;

        @Option(names = "--enable-profiler", description = "Temporarily enable profiler level 1 and restore it after sampling.")
        private boolean enableProfiler;

        @Option(names = "--profile-seconds", description = "Seconds to collect profiler samples when --enable-profiler is set.", defaultValue = "300")
        private int profileSeconds;

        @Option(names = "--slow-ms", description = "Profiler slowms when --enable-profiler is set.", defaultValue = "50")
        private int slowMs;

        @Option(names = "--redact", negatable = true, description = "Redact sensitive fields in sampled command documents.", defaultValue = "true")
        private boolean redact;

        @Option(names = "--parallel", negatable = true, description = "Collect database information in parallel for faster processing on large deployments.", defaultValue = "false")
        private boolean parallelCollection;

        @Option(names = "--parallel-threads", description = "Number of parallel threads for database collection. Only used with --parallel.", defaultValue = "4")
        private int parallelThreads;

        @Override
        public Integer call() throws Exception {
            logger.info("Starting MongoDB usage collection");
            validateInputs();
            CollectorOptions options = toOptions();
            if (enableProfiler) {
                logger.warn("Profiler sampling is enabled. This will temporarily modify profiling settings on the target MongoDB deployment.");
            }
            if (parallelCollection) {
                logger.info("Parallel collection enabled with {} threads", parallelThreads);
            }
            UsageReport report = new MongoCollector(options).collect();
            new CollectJsonWriter().write(report, outputDirectory);
            Path excelFile = outputDirectory.resolve("mongo-usage-report.xlsx");
            new CollectExcelWriter().write(report, excelFile);
            logger.info("MongoDB usage Excel written to {}", excelFile.toAbsolutePath());
            System.out.printf("MongoDB usage Excel written to %s%n", excelFile.toAbsolutePath());
            return 0;
        }

        private void validateInputs() {
            ValidationUtils.validateUri(uri);
            ValidationUtils.validateMongoVersion(mongoVersion);
            ValidationUtils.validateSampleLimit(sampleLimit);
            if (enableProfiler) {
                ValidationUtils.validateProfileSeconds(profileSeconds);
                ValidationUtils.validateSlowMs(slowMs);
            }
            if (parallelCollection && parallelThreads < 1) {
                throw new IllegalArgumentException("Parallel threads must be at least 1: " + parallelThreads);
            }
            if (parallelCollection && parallelThreads > 32) {
                throw new IllegalArgumentException("Parallel threads too large (max 32): " + parallelThreads);
            }
        }

        private CollectorOptions toOptions() {
            CollectorOptions options = new CollectorOptions();
            options.setUri(uri);
            options.setMongoVersion(mongoVersion);
            options.setOutputDirectory(outputDirectory);
            options.setIncludeDatabases(includeDatabases);
            options.setExcludeDatabases(excludeDatabases);
            options.setSampleLimit(sampleLimit);
            options.setEnableProfiler(enableProfiler);
            options.setProfileSeconds(profileSeconds);
            options.setSlowMs(slowMs);
            options.setRedact(redact);
            options.setParallelCollection(parallelCollection);
            options.setParallelThreads(parallelThreads);
            return options;
        }
    }

    @Command(
            name = "compat-test",
            mixinStandardHelpOptions = true,
            description = "Create MongoDB test schema/data and run feature compatibility tests only."
    )
    static class CompatTestCommand implements Callable<Integer> {
        private static final Logger logger = LoggerFactory.getLogger(CompatTestCommand.class);
        @Option(names = "--uri", description = "MongoDB connection string.", required = true)
        private String uri;

        @Option(names = "--mongo-version", description = "MongoDB server version family to label in output.", required = true)
        private String mongoVersion;

        @Option(names = "--out", description = "Output directory.", defaultValue = "mongo-compat-test-report")
        private Path outputDirectory;

        @Option(names = "--compat-db", description = "Database used by compatibility tests.", defaultValue = "mongo_usage_compat_test")
        private String compatDatabase;

        @Option(names = "--keep-compat-db", description = "Keep compatibility test database after the run.")
        private boolean keepCompatDatabase;

        @Override
        public Integer call() throws Exception {
            logger.info("Starting MongoDB compatibility tests");
            validateInputs();
            try (var client = MongoClients.create(uri)) {
                MongoCompatTestReport compatReport = new MongoCompatTestRunner(client, compatDatabase, !keepCompatDatabase, mongoVersion).run();
                new CompatTestJsonWriter().write(compatReport, outputDirectory);
                Path reportFile = outputDirectory.resolve("compat-test-report.json");
                Path excelFile = outputDirectory.resolve("compat-test-results.xlsx");
                new CompatTestExcelWriter().write(compatReport, excelFile);
                logger.info("MongoDB compatibility test report written to {}", reportFile.toAbsolutePath());
                logger.info("Compatibility tests: total={}, passed={}, failed={}, skipped={}",
                        compatReport.total(), compatReport.passed(), compatReport.failed(), compatReport.skipped());
                System.out.printf("MongoDB compatibility test report written to %s%n", reportFile.toAbsolutePath());
                System.out.printf("MongoDB compatibility test Excel written to %s%n", excelFile.toAbsolutePath());
                System.out.printf("Compatibility tests: total=%d, passed=%d, failed=%d, skipped=%d%n",
                        compatReport.total(), compatReport.passed(), compatReport.failed(), compatReport.skipped());
                return compatReport.isSuccess() ? 0 : 2;
            }
        }

        private void validateInputs() {
            ValidationUtils.validateUri(uri);
            ValidationUtils.validateMongoVersion(mongoVersion);
        }
    }

    @Command(
            name = "summarize",
            mixinStandardHelpOptions = true,
            description = "Read an existing collect output directory and generate summarized Excel and HTML reports."
    )
    static class SummarizeCommand implements Callable<Integer> {
        private static final Logger logger = LoggerFactory.getLogger(SummarizeCommand.class);
        @Option(names = "--report-dir", description = "Directory produced by the collect command.", defaultValue = "mongo-usage-report")
        private Path reportDirectory;

        @Option(names = "--out", description = "Summary Excel file path. Defaults to <report-dir>/mongo-usage-summary.xlsx.")
        private Path outputFile;

        @Option(names = "--html-out", description = "Summary HTML file path. Defaults to <report-dir>/mongo-usage-summary.html.")
        private Path htmlOutputFile;

        @Override
        public Integer call() throws Exception {
            logger.info("Starting MongoDB usage report summarization");
            Path rawJson = reportDirectory.resolve("raw.json");
            Path summaryFile = outputFile == null ? reportDirectory.resolve("mongo-usage-summary.xlsx") : outputFile;
            Path htmlSummaryFile = htmlOutputFile == null ? reportDirectory.resolve("mongo-usage-summary.html") : htmlOutputFile;
            UsageReport report = new UsageReportJsonReader().read(rawJson);
            new UsageSummaryExcelWriter().write(report, summaryFile);
            new UsageSummaryHtmlWriter().write(report, htmlSummaryFile);
            logger.info("MongoDB usage summary Excel written to {}", summaryFile.toAbsolutePath());
            logger.info("MongoDB usage summary HTML written to {}", htmlSummaryFile.toAbsolutePath());
            System.out.printf("MongoDB usage summary Excel written to %s%n", summaryFile.toAbsolutePath());
            System.out.printf("MongoDB usage summary HTML written to %s%n", htmlSummaryFile.toAbsolutePath());
            return 0;
        }
    }
}
