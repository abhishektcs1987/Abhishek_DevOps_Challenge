package com.example.service;

import com.example.model.LogEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LogStorageService {
    private final File file = new File("logs.json");
    private final ObjectMapper mapper = new ObjectMapper();

    public void saveLogs(List<LogEntry> logs) throws IOException {
        List<LogEntry> existing = loadLogs();
        existing.addAll(logs);
        mapper.writerWithDefaultPrettyPrinter().writeValue(file, existing);
    }

    public List<LogEntry> loadLogs() {
        try {
            if (!file.exists()) return new ArrayList<>();
            CollectionType listType = mapper.getTypeFactory()
                    .constructCollectionType(List.class, LogEntry.class);
            return mapper.readValue(file, listType);
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

}
