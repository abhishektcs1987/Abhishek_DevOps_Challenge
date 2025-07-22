package com.example.AppConfig;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AppConfig {
    public ApiConfig api;
    public FilterConfig filter;

    public static class ApiConfig {
        public String baseUrl;
        public int maxRetries;
        public int rateLimitWaitSeconds;
        public long retryDelayMs;
    }

    public static class FilterConfig {
        public String type;
    }


}
