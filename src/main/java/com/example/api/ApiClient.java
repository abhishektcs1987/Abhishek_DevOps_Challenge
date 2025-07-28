package com.example.api;

import com.example.AppConfig.AppConfig;
import com.example.AppConfig.ConfigLoader;
import com.example.model.LogEntry;
import com.example.service.LogStorageService;
import com.example.utils.RetryHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.ProtocolException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ApiClient {
    private static final Logger logger = Logger.getLogger(ApiClient.class.getName());

    private final String baseUrl;
    private final LogStorageService storage;
    private final ObjectMapper mapper;
    private final AppConfig config;
    private final RetryHandler retryHandler;

    public ApiClient(String baseUrl, LogStorageService storage, int maxRetries, long retryDelayMs) throws IOException {
        this.baseUrl = baseUrl;
        this.storage = storage;
        this.mapper = new ObjectMapper();
        this.config = ConfigLoader.loadConfig("src/config.yaml");
        this.config.api.maxRetries = maxRetries;
        this.config.api.retryDelayMs = retryDelayMs;
        this.retryHandler = new RetryHandler(maxRetries, retryDelayMs);
    }

    public void fetchAllPages() throws IOException {
        String nextUrl = baseUrl;
        int pageCount = 0;
        int totalRecords = 0;

        logger.info("Starting to fetch data from: " + baseUrl);

        while (nextUrl != null) {
            pageCount++;
            logger.info(String.format("Fetching page %d: %s", pageCount, nextUrl));

            try {
                PageResult result = fetchPage(nextUrl);
                storage.saveLogs(result.logs);
                totalRecords += result.logs.size();
                nextUrl = result.nextUrl;

                logger.info(String.format("Page %d completed. Records: %d", pageCount, result.logs.size()));

                // Optional: Add small delay between requests to be API-friendly
                if (nextUrl != null) {
                    Thread.sleep(100); // 100ms delay
                }

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to fetch page after retries: " + nextUrl, e);
                throw new IOException("API fetch failed", e);
            }
        }

        logger.info(String.format("Fetch completed. Total pages: %d, Total records: %d", pageCount, totalRecords));
    }

    private PageResult fetchPage(String url) throws Exception {
        return retryHandler.executeWithRetry(() -> {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpGet request = new HttpGet(url);
                request.setHeader("Accept", "application/vnd.github.v3+json");
                request.setHeader("User-Agent", "LogParser/1.0"); // GitHub requires User-Agent

                try (CloseableHttpResponse response = httpClient.execute(request)) {
                    return handleResponse(response);
                } catch (InterruptedException e) {
                    logger.warning("Request failed for URL: " + url + ". Error: " + e.getMessage());
                    throw new RuntimeException(e);
                } catch (ProtocolException e) {
                    logger.warning("Request failed for URL: " + url + ". Error: " + e.getMessage());
                    throw new RuntimeException(e);
                }
            } catch (IOException e) {
                logger.warning("Request failed for URL: " + url + ". Error: " + e.getMessage());
                try {
                    throw e;
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    private PageResult handleResponse(CloseableHttpResponse response) throws IOException, InterruptedException, ProtocolException {
        int status = response.getCode();

        switch (status) {
            case 200:
                return processSuccessResponse(response);
            case 403:
                handleRateLimit(response);
                throw new IOException("Rate limited - will retry");
            case 404:
                logger.warning("Resource not found (404)");
                return new PageResult(new ArrayList<>(), null);
            default:
                String errorMsg = String.format("Unexpected status code: %d", status);
                logger.severe(errorMsg);
                throw new IOException(errorMsg);
        }
    }

    private PageResult processSuccessResponse(CloseableHttpResponse response) throws IOException, ProtocolException {
        JsonNode array = mapper.readTree(response.getEntity().getContent());
        List<LogEntry> logs = new ArrayList<>();

        // Validate response structure
        if (!array.isArray()) {
            throw new IOException("Expected JSON array in response");
        }

        for (JsonNode node : array) {
            try {
                LogEntry entry = parseLogEntry(node);
                if (entry != null) {
                    logs.add(entry);
                }
            } catch (Exception e) {
                logger.warning("Failed to parse log entry: " + e.getMessage());
                // Continue processing other entries
            }
        }

        String nextUrl = extractNextUrl(response.getHeader("Link"));
        return new PageResult(logs, nextUrl);
    }

    private LogEntry parseLogEntry(JsonNode node) {
        // Add null checks and validation
        if (node == null || node.get("id") == null || node.get("type") == null) {
            return null;
        }

        LogEntry.Actor actor = new LogEntry.Actor();
        JsonNode actorNode = node.get("actor");
        if (actorNode != null && actorNode.get("login") != null) {
            actor.login = actorNode.get("login").asText();
        }

        return new LogEntry(
                node.get("id").asText(),
                node.get("type").asText(),
                actor
        );
    }

    private void handleRateLimit(CloseableHttpResponse response) throws InterruptedException, ProtocolException {
        // Check for X-RateLimit-Reset header
        Header resetHeader = response.getHeader("X-RateLimit-Reset");
        long waitTime = config.api.rateLimitWaitSeconds;

        if (resetHeader != null) {
            try {
                long resetTime = Long.parseLong(resetHeader.getValue());
                long currentTime = System.currentTimeMillis() / 1000;
                long calculatedWait = Math.max(1, resetTime - currentTime + 1);
                waitTime = Math.min(calculatedWait, config.api.rateLimitWaitSeconds);
            } catch (NumberFormatException e) {
                logger.warning("Invalid X-RateLimit-Reset header value");
            }
        }

        logger.info(String.format("Rate limited. Waiting %d seconds...", waitTime));
        Thread.sleep(TimeUnit.SECONDS.toMillis(waitTime));
    }

    private String extractNextUrl(Header linkHeader) {
        if (linkHeader == null || linkHeader.getValue() == null) {
            return null;
        }

        String linkHeaderValue = linkHeader.getValue();
        for (String part : linkHeaderValue.split(",")) {
            if (part.contains("rel=\"next\"")) {
                int start = part.indexOf('<') + 1;
                int end = part.indexOf('>');
                if (start > 0 && end > start) {
                    return part.substring(start, end);
                }
            }
        }
        return null;
    }

    private static class PageResult {
        final List<LogEntry> logs;
        final String nextUrl;

        PageResult(List<LogEntry> logs, String nextUrl) {
            this.logs = logs;
            this.nextUrl = nextUrl;
        }
    }
}