# 📘 Log Parser Client

The **Log Parser Client** is a Java-based command-line tool that fetches, filters, and displays GitHub event logs from the [GitHub Events API](https://api.github.com/events). It supports pagination, error handling with retries, rate limiting, and persistent storage of logs in JSON format.

---

## 🚀 Features

- ✅ Fetch paginated event data from GitHub’s API
- ✅ Resilient to transient network/API errors (retry logic with backoff)
- ✅ Honors API rate limits
- ✅ Saves logs to local JSON storage
- ✅ CLI interface with filtering by event type
- ✅ Configurable via `config.yaml`
- ✅ Uses Log4j2 for structured logging

---

## 🛠️ Requirements

- Java 11+
- Gradle (or use the provided wrapper)

---

## 📁 Project Structure

log-parser-client/
├── src/
│ ├── main/
│ │ ├── java/
│ │ │ ├── com.example.api/ # API client
| │ │ ├── com.example.AppConfig/ # Config Loader
│ │ │ ├── com.example.cli/ # CLI tool
│ │ │ ├── com.example.model/ # POJOs
│ │ │ ├── com.example.service/ # Log storage
│ │ │ ├── com.example.utils/ # Retry utility
│ │ └── resources/
│ │ └── log4j2.xml # Logging configuration
├── config.yaml
├── build.gradle
└── README.md

---

```
Place this file in src/main/resources/ and ensure it's included in the classpath.

📦 Building the Project

./gradlew clean build
This will generate a JAR under build/libs/.

🧪 Running the CLI Tool
Fetch logs from GitHub API and store locally:
java -jar build/libs/log-parser-client-1.0.jar --fetch

Display all stored logs:
java -jar build/libs/log-parser-client-1.0.jar --display

Filter logs by event type (e.g., PushEvent, PullRequestEvent, etc.):
java -jar build/libs/log-parser-client-1.0.jar --display --type PushEvent

🪵 Logging
This project uses Log4j2. You can configure output level, file output, and formatting in log4j2.xml.

Example:

<Root level="info">
    <AppenderRef ref="Console"/>
</Root>

📈 Real-World Use Case
This client can be used for:

Monitoring GitHub repository activity

Log analysis and filtering

Demonstrating API pagination, retries, and config-driven behavior