package com.example.mongousage;

import com.example.mongousage.config.CollectorOptions;
import com.example.mongousage.io.CollectExcelWriter;
import com.example.mongousage.io.CollectJsonWriter;
import com.example.mongousage.model.UsageReport;
import com.example.mongousage.mongo.MongoCollector;
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
        description = "Collect MongoDB inventory and workload signals (Excel output)."
)
public class MongoUsageCollectorApp implements Callable<Integer> {
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

    public static void main(String[] args) {
        int exitCode = new CommandLine(new MongoUsageCollectorApp()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        CollectorOptions options = toOptions();
        UsageReport report = new MongoCollector(options).collect();
        new CollectJsonWriter().write(report, outputDirectory);
        Path excelFile = outputDirectory.resolve("mongo-usage-report.xlsx");
        new CollectExcelWriter().write(report, excelFile);
        System.out.printf("MongoDB usage Excel written to %s%n", excelFile.toAbsolutePath());
        return 0;
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
        return options;
    }
}
