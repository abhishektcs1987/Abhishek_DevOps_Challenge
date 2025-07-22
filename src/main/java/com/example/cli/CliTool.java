package com.example.cli;

import com.example.AppConfig.AppConfig;
import com.example.AppConfig.ConfigLoader;
import com.example.api.ApiClient;
import com.example.model.LogEntry;
import com.example.service.LogStorageService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Command(name = "logparser", mixinStandardHelpOptions = true, version = "1.0",
        description = "Fetch and display logs from a remote API.")
public class CliTool implements Callable<Integer> {
    private static Logger LogManager;
    private static final Logger logger = LogManager.getLogger(String.valueOf(CliTool.class));
    AppConfig config = ConfigLoader.loadConfig("src/config.yaml");
    String apiUrl = config.api.baseUrl;
    String filterType = config.filter.type;

    @Option(names = {"--fetch"}, description = "Fetch logs from API and save them")
    boolean fetch;

    @Option(names = {"--display"}, description = "Display stored logs")
    boolean display;

    @Option(names = {"--help", "-h"}, usageHelp = true, description = "Display this help message")
    boolean helpRequested;

    @Option(names = "--type", description = "Filter by log type")
    String typeFilter;

    public CliTool() throws IOException {
    }

    @Override
    public Integer call() throws Exception {
        LogStorageService storage = new LogStorageService();

        if (fetch) {
            ApiClient client = new ApiClient("https://api.github.com/events", storage, config.api.maxRetries, config.api.retryDelayMs);
            client.fetchAllPages();
        }

        if (display) {
            List<LogEntry> logs = storage.loadLogs();

            if (typeFilter != null && !typeFilter.isEmpty()) {
                logs = logs.stream()
                        .filter(log -> log.getType().equalsIgnoreCase(typeFilter))
                        .collect(Collectors.toList());
            }

            logs.forEach(log -> logger.info(log.toString()));
        }

        return 0;
    }

    public static void main(String[] args) throws IOException {
        int exitCode = new CommandLine(new CliTool()).execute(args);
        System.exit(exitCode);
    }
}
