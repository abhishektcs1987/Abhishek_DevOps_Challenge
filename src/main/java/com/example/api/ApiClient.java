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
import org.apache.hc.core5.http.ProtocolException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ApiClient {
    private final String baseUrl;
    private final LogStorageService storage;
    private final ObjectMapper mapper = new ObjectMapper();
    private final int maxRetries;
    private final long retryDelayMs;
    AppConfig config = ConfigLoader.loadConfig("src/config.yaml");


    public ApiClient(String baseUrl, LogStorageService storage, int maxRetries, long retryDelayMs) throws IOException {
        this.baseUrl = baseUrl;
        this.storage = storage;
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;
    }

    public void fetchAllPages() throws Exception {
        String nextUrl = baseUrl;
        RetryHandler retryHandler = new RetryHandler(maxRetries, retryDelayMs);

        while (nextUrl != null) {
            final String currentUrl = nextUrl; // for lambda scope

            String finalNextUrl = retryHandler.executeWithRetry(() -> {
                try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                    HttpGet request = new HttpGet(currentUrl);
                    request.setHeader("Accept", "application/vnd.github.v3+json");

                    try (CloseableHttpResponse response = httpClient.execute(request)) {
                        int status = response.getCode();

                        if (status == 200) {
                            JsonNode array = mapper.readTree(response.getEntity().getContent());
                            List<LogEntry> logs = new ArrayList<>();

                            for (JsonNode node : array) {
                                LogEntry.Actor actor = new LogEntry.Actor();
                                actor.login = node.get("actor").get("login").asText();

                                logs.add(new LogEntry(
                                        node.get("id").asText(),
                                        node.get("type").asText(),
                                        actor
                                ));
                            }
                            storage.saveLogs(logs);

                            String linkHeader = response.getHeader("Link") != null
                                    ? response.getHeader("Link").getValue()
                                    : null;

                            return extractNextUrl(linkHeader);
                        } else if (status == 403) {
                            System.out.println("Rate limited. Waiting...");
                            Thread.sleep(config.api.rateLimitWaitSeconds);
                            throw new IOException("Rate limited");
                        } else {
                            throw new IOException("Unexpected status code: " + status);
                        }
                    } catch (ProtocolException e) {
                        throw new RuntimeException(e);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            nextUrl = finalNextUrl;
        }
    }

    private String extractNextUrl(String linkHeader) {
        if (linkHeader == null) return null;
        for (String part : linkHeader.split(",")) {
            if (part.contains("rel=\"next\"")) {
                int start = part.indexOf('<') + 1;
                int end = part.indexOf('>');
                return part.substring(start, end);
            }
        }
        return null;
    }
}
