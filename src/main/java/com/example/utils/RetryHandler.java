package com.example.utils;

import com.example.AppConfig.AppConfig;
import com.example.AppConfig.ConfigLoader;

import java.io.IOException;
import java.util.function.Supplier;

public class RetryHandler {

    private final int maxRetries;
    private final long baseDelayMs;
    AppConfig config = ConfigLoader.loadConfig("src/config.yaml");

    public RetryHandler(int maxRetries, long baseDelayMs) throws IOException {
        this.maxRetries = maxRetries;
        this.baseDelayMs = baseDelayMs;
    }

    /**
     * Retry a supplier operation (e.g., a lambda that returns a value) with exponential backoff.
     *
     * @param operation The operation to execute
     * @param <T>       The type of the return value
     * @return          The result of the operation
     * @throws Exception if all retries fail
     */
    public <T> T executeWithRetry(Supplier<T> operation) throws Exception {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < maxRetries) {
            try {
                return operation.get();
            } catch (Exception ex) {
                lastException = ex;
                attempt++;

                if (attempt >= maxRetries) break;

                long delay = (long) (baseDelayMs * Math.pow(2, attempt - 1));
                System.err.printf("Attempt %d failed. Retrying in %d ms...%n", attempt, delay);
                Thread.sleep(delay);
            }
        }

        throw new Exception("Operation failed after " + maxRetries + " attempts", lastException);
    }
}
