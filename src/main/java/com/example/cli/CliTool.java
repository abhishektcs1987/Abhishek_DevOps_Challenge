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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.stream.Collectors;

@Command(name = "logparser", mixinStandardHelpOptions = true, version = "1.0",
        description = "Fetch and display logs from a remote API.")
public class CliTool implements Callable<Integer> {
    // Fixed: Proper logger initialization
    private static final Logger logger = Logger.getLogger(CliTool.class.getName());

    // Load config once and handle potential IOException
    private final AppConfig config;
    private final String apiUrl;
    private final String filterType;

    @Option(names = {"--fetch"}, description = "Fetch logs from API and save them")
    boolean fetch;

    @Option(names = {"--display"}, description = "Display stored logs")
    boolean display;

    @Option(names = {"--help", "-h"}, usageHelp = true, description = "Display this help message")
    boolean helpRequested;

    @Option(names = "--type", description = "Filter by log type")
    String typeFilter;

    @Option(names = {"--actor", "-a"}, description = "Filter by actor login name (partial match)")
    String actorFilter;

    @Option(names = {"--limit", "-l"}, description = "Limit number of results displayed")
    Integer limit;

    public CliTool() throws IOException {
        try {
            this.config = ConfigLoader.loadConfig("src/config.yaml");
            this.apiUrl = config.api.baseUrl;
            this.filterType = config.filter.type;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to load configuration from src/config.yaml", e);
            throw e;
        }
    }

    @Override
    public Integer call() throws Exception {
        try {
            LogStorageService storage = new LogStorageService();

            if (fetch) {
                logger.info("Starting fetch operation...");
                try {
                    ApiClient client = new ApiClient(
                            apiUrl != null ? apiUrl : "https://api.github.com/events",
                            storage,
                            config.api.maxRetries,
                            config.api.retryDelayMs
                    );
                    client.fetchAllPages();
                    logger.info("Fetch operation completed successfully");
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Failed to fetch data from API", e);
                    System.err.println("Error: Failed to fetch data from API. Check logs for details.");
                    return 1;
                }
            }

            if (display) {
                logger.info("Starting display operation...");
                try {
                    List<LogEntry> logs = storage.loadLogs();

                    if (logs.isEmpty()) {
                        System.out.println("No logs found. Use --fetch to retrieve data first.");
                        return 0;
                    }

                    logger.info(String.format("Loaded %d total log entries", logs.size()));

                    // Apply type filter if provided
                    String effectiveTypeFilter = typeFilter != null ? typeFilter : filterType;
                    if (effectiveTypeFilter != null && !effectiveTypeFilter.isEmpty()) {
                        logs = logs.stream()
                                .filter(log -> log.getType().equalsIgnoreCase(effectiveTypeFilter))
                                .collect(Collectors.toList());
                    }

                    // Apply actor filter if provided
                    if (actorFilter != null && !actorFilter.isEmpty()) {
                        logs = logs.stream()
                                .filter(log -> log.getActor() != null &&
                                        log.getActor().getLogin() != null &&
                                        log.getActor().getLogin().toLowerCase()
                                                .contains(actorFilter.toLowerCase()))
                                .collect(Collectors.toList());
                    }

                    if (logs.isEmpty()) {
                        String filterDesc = buildFilterDescription(effectiveTypeFilter, actorFilter);
                        logger.info("No logs found matching filter" +
                                (filterDesc.isEmpty() ? "s." : "s: " + filterDesc));
                        return 0;
                    }

                    // Apply limit if specified
                    boolean isLimited = false;
                    if (limit != null && limit > 0 && logs.size() > limit) {
                        logs = logs.stream().limit(limit).collect(Collectors.toList());
                        isLimited = true;
                    }

                    // Display results
                    System.out.println(String.format("Displaying %d log entries:", logs.size()));
                    if (isLimited) {
                        System.out.println("(Limited to " + limit + " entries)");
                    }
                    System.out.println("=" + "=".repeat(50));

                    logs.forEach(log -> System.out.println(log.toString()));

                    logger.info(String.format("Successfully displayed %d log entries", logs.size()));
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Failed to display logs", e);
                    System.err.println("Error: Failed to load or display logs. Check logs for details.");
                    return 1;
                }
            }

            // Help is handled automatically by picocli, but we keep this for explicit handling
            if (helpRequested) {
                CommandLine.usage(this, System.out);
                return 0;
            }

            // If no options provided, show usage
            if (!fetch && !display && !helpRequested) {
                System.out.println("No operation specified. Use --help for usage information.");
                CommandLine.usage(this, System.out);
                return 0;
            }

            return 0;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected error in CLI tool", e);
            System.err.println("Error: An unexpected error occurred. Check logs for details.");
            return 1;
        }
    }

    private String buildFilterDescription(String typeFilter, String actorFilter) {
        List<String> filters = new ArrayList<>();

        if (typeFilter != null && !typeFilter.isEmpty()) {
            filters.add("type='" + typeFilter + "'");
        }

        if (actorFilter != null && !actorFilter.isEmpty()) {
            filters.add("actor contains '" + actorFilter + "'");
        }

        return String.join(", ", filters);
    }

    public static void main(String[] args) {
        try {
            int exitCode = new CommandLine(new CliTool()).execute(args);
            System.exit(exitCode);
        } catch (IOException e) {
            System.err.println("Fatal error: Failed to initialize CLI tool - " + e.getMessage());
            System.exit(1);
        }
    }
}
