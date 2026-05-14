# SMILE CLI 模型交互 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现通过终端与 DeepSeek 模型进行流式对话的 Java CLI 工具

**Architecture:** 4 个类按依赖顺序构建 — ConfigManager 管理配置，ConsoleUI 处理终端交互，ModelClient 封装 SSE 流式 API 调用，Main 编排交互循环。使用 JLine 做终端 UI，OkHttp+SSE 做 HTTP 流式通信，Jackson 做 JSON 序列化。

**Tech Stack:** Java 17, Maven, JLine 3.25.1, OkHttp 4.12.0, OkHttp SSE 4.12.0, Jackson 2.17.0, Maven Shade Plugin 3.5.1

---

### Task 1: Update pom.xml with dependencies and build config

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Update pom.xml**

Replace the entire pom.xml with the following:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.github.loong</groupId>
    <artifactId>smile_cli</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <name>smile_cli</name>

    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.jline</groupId>
            <artifactId>jline</artifactId>
            <version>3.25.1</version>
        </dependency>
        <dependency>
            <groupId>com.squareup.okhttp3</groupId>
            <artifactId>okhttp</artifactId>
            <version>4.12.0</version>
        </dependency>
        <dependency>
            <groupId>com.squareup.okhttp3</groupId>
            <artifactId>okhttp-sse</artifactId>
            <version>4.12.0</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>2.17.0</version>
        </dependency>
        <dependency>
            <groupId>junit</groupId>
            <artifactId>junit</artifactId>
            <version>3.8.1</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.5.1</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>shade</goal>
                        </goals>
                        <configuration>
                            <transformers>
                                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                    <mainClass>com.github.loong.Main</mainClass>
                                </transformer>
                            </transformers>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Verify Maven build works**

```bash
cd /Users/garen/Code/java/smile_cli/smile_cli && mvn compile
```

Expected: BUILD SUCCESS with new dependencies downloaded.

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "build: add JLine, OkHttp, Jackson deps, Java 17, Shade plugin"
```

---

### Task 2: Implement ConfigManager

**Files:**
- Create: `src/main/java/com/github/loong/ConfigManager.java`

- [ ] **Step 1: Create ConfigManager.java**

```java
package com.github.loong;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class ConfigManager {

    private final Properties props;

    private static final String DEFAULT_MODEL_NAME = "deepseek-chat";
    private static final String DEFAULT_DISPLAY_VERSION = "deepseek v4 pro";
    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";

    public ConfigManager() {
        this.props = new Properties();
        props.setProperty("model.name", DEFAULT_MODEL_NAME);
        props.setProperty("model.displayVersion", DEFAULT_DISPLAY_VERSION);
        props.setProperty("api.baseUrl", DEFAULT_BASE_URL);

        loadConfigFile();
    }

    private void loadConfigFile() {
        Path configPath = Paths.get(System.getProperty("user.home"), ".smile_cli", "config");
        if (Files.exists(configPath)) {
            try (InputStream in = Files.newInputStream(configPath)) {
                Properties fileProps = new Properties();
                fileProps.load(in);
                props.putAll(fileProps);
            } catch (IOException e) {
                // ignore malformed config, use defaults
            }
        }
    }

    public String getModelName() {
        return props.getProperty("model.name");
    }

    public String getDisplayVersion() {
        return props.getProperty("model.displayVersion");
    }

    public String getBaseUrl() {
        String url = props.getProperty("api.baseUrl");
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    public String getApiKey() {
        return System.getenv("DEEPSEEK_API_KEY");
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
cd /Users/garen/Code/java/smile_cli/smile_cli && mvn compile
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/github/loong/ConfigManager.java
git commit -m "feat: add ConfigManager for ~/.smile_cli/config and env var support"
```

---

### Task 3: Implement ConsoleUI

**Files:**
- Create: `src/main/java/com/github/loong/ConsoleUI.java`

- [ ] **Step 1: Create ConsoleUI.java**

```java
package com.github.loong;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.EndOfFileException;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;

public class ConsoleUI implements AutoCloseable {

    private static final String ANSI_RESET = "[0m";
    private static final String ANSI_CYAN = "[36m";
    private static final String ANSI_GREEN = "[32m";
    private static final String ANSI_RED = "[31m";
    private static final String ANSI_BOLD = "[1m";
    private static final String ANSI_YELLOW = "[33m";

    private final Terminal terminal;
    private final LineReader reader;
    private final ConfigManager config;

    public ConsoleUI(ConfigManager config) throws IOException {
        this.config = config;
        this.terminal = TerminalBuilder.builder()
                .system(true)
                .jansi(true)
                .build();
        this.reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .build();
    }

    public void showWelcome() {
        String banner =
                ANSI_CYAN + ANSI_BOLD +
                "  ╔═════════════════════════════════════════╗\n" +
                "  ║                                         ║\n" +
                "  ║         " + ANSI_YELLOW + "S M I L E   C L I" + ANSI_CYAN + "               ║\n" +
                "  ║                                         ║\n" +
                "  ╠═════════════════════════════════════════╣\n" +
                "  ║  Model: " + ANSI_RESET + ANSI_GREEN + padRight(config.getDisplayVersion(), 28) + ANSI_CYAN + " ║\n" +
                "  ║  Type /help for commands                ║\n" +
                "  ╚═════════════════════════════════════════╝\n" +
                ANSI_RESET;
        terminal.writer().println(banner);
        terminal.writer().flush();
    }

    public String readInput(String prompt) {
        try {
            String line = reader.readLine(ANSI_GREEN + prompt + ANSI_RESET);
            return line;
        } catch (EndOfFileException e) {
            return null; // Ctrl+D
        } catch (UserInterruptException e) {
            return ""; // Ctrl+C on empty line
        }
    }

    public void printToken(String token) {
        terminal.writer().print(token);
        terminal.writer().flush();
    }

    public void printInfo(String msg) {
        terminal.writer().println(ANSI_CYAN + msg + ANSI_RESET);
        terminal.writer().flush();
    }

    public void printError(String msg) {
        terminal.writer().println(ANSI_RED + ANSI_BOLD + "  Error: " + msg + ANSI_RESET);
        terminal.writer().flush();
    }

    public void println() {
        terminal.writer().println();
        terminal.writer().flush();
    }

    @Override
    public void close() {
        try {
            terminal.close();
        } catch (IOException e) {
            // ignore
        }
    }

    private static String padRight(String s, int n) {
        if (s.length() >= n) return s;
        return s + " ".repeat(n - s.length());
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
cd /Users/garen/Code/java/smile_cli/smile_cli && mvn compile
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/github/loong/ConsoleUI.java
git commit -m "feat: add ConsoleUI with welcome banner, colored output, JLine reader"
```

---

### Task 4: Implement ModelClient

**Files:**
- Create: `src/main/java/com/github/loong/ModelClient.java`

- [ ] **Step 1: Create ModelClient.java**

```java
package com.github.loong;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ModelClient {

    private static final MediaType JSON = MediaType.parse("application/json");

    private final ConfigManager config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private volatile EventSource activeEventSource;

    public ModelClient(ConfigManager config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.MINUTES)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public void chat(List<Map<String, String>> messages,
                     Consumer<String> onToken,
                     Consumer<String> onError) throws Exception {

        String apiKey = config.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("DEEPSEEK_API_KEY environment variable is not set");
        }

        Map<String, Object> body = Map.of(
                "model", config.getModelName(),
                "messages", messages,
                "stream", true
        );

        String jsonBody = objectMapper.writeValueAsString(body);

        Request request = new Request.Builder()
                .url(config.getBaseUrl() + "/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, JSON))
                .build();

        CountDownLatch latch = new CountDownLatch(1);

        EventSourceListener listener = new EventSourceListener() {
            @Override
            public void onEvent(EventSource eventSource, String id, String type, String data) {
                if ("[DONE]".equals(data.trim())) {
                    latch.countDown();
                    return;
                }
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> chunk = objectMapper.readValue(data, Map.class);
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) chunk.get("choices");
                    if (choices != null && !choices.isEmpty()) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
                        if (delta != null) {
                            Object content = delta.get("content");
                            if (content != null) {
                                onToken.accept(content.toString());
                            }
                        }
                    }
                } catch (Exception e) {
                    // skip malformed chunks
                }
            }

            @Override
            public void onFailure(EventSource eventSource, Throwable t, Response response) {
                if (t != null) {
                    onError.accept(t.getMessage());
                } else if (response != null) {
                    onError.accept("HTTP " + response.code() + " " + response.message());
                }
                latch.countDown();
            }

            @Override
            public void onClosed(EventSource eventSource) {
                latch.countDown();
            }
        };

        EventSource.Factory factory = EventSources.createFactory(httpClient);
        activeEventSource = factory.newEventSource(request, listener);
        latch.await();
    }

    public void cancel() {
        if (activeEventSource != null) {
            activeEventSource.cancel();
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
cd /Users/garen/Code/java/smile_cli/smile_cli && mvn compile
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/github/loong/ModelClient.java
git commit -m "feat: add ModelClient with SSE streaming via OkHttp"
```

---

### Task 5: Implement Main entry point and interaction loop

**Files:**
- Modify: `src/main/java/com/github/loong/Main.java`

- [ ] **Step 1: Replace Main.java**

Replace the entire content of `src/main/java/com/github/loong/Main.java` with:

```java
package com.github.loong;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {

    public static void main(String[] args) {
        ConfigManager config = new ConfigManager();

        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            System.err.println("Error: DEEPSEEK_API_KEY environment variable is not set.");
            System.err.println("Please set it with: export DEEPSEEK_API_KEY=your-key");
            System.exit(1);
        }

        try (ConsoleUI ui = new ConsoleUI(config)) {
            ModelClient client = new ModelClient(config);

            ui.showWelcome();

            List<Map<String, String>> messages = new ArrayList<>();

            while (true) {
                String input = ui.readInput("> ");

                if (input == null) {
                    break; // Ctrl+D
                }

                input = input.trim();

                if (input.isEmpty()) {
                    continue;
                }

                if ("/exit".equals(input) || "/quit".equals(input)) {
                    ui.printInfo("Goodbye!");
                    break;
                }

                if ("/help".equals(input)) {
                    ui.printInfo("Commands:");
                    ui.printInfo("  /exit, /quit  - Exit the CLI");
                    ui.printInfo("  /help         - Show this help");
                    ui.printInfo("  Ctrl+D        - Exit the CLI");
                    continue;
                }

                Map<String, String> userMsg = new HashMap<>();
                userMsg.put("role", "user");
                userMsg.put("content", input);
                messages.add(userMsg);

                StringBuilder response = new StringBuilder();

                ui.printInfo(""); // newline before response
                try {
                    client.chat(messages, token -> {
                        response.append(token);
                        ui.printToken(token);
                    }, error -> {
                        ui.printError(error);
                    });

                    String fullResponse = response.toString();
                    if (!fullResponse.isEmpty()) {
                        Map<String, String> assistantMsg = new HashMap<>();
                        assistantMsg.put("role", "assistant");
                        assistantMsg.put("content", fullResponse);
                        messages.add(assistantMsg);
                    } else {
                        // remove the user message if we couldn't get a response
                        messages.remove(messages.size() - 1);
                    }

                    ui.println();
                } catch (Exception e) {
                    ui.printError(e.getMessage());
                    messages.remove(messages.size() - 1); // remove failed user msg
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to initialize CLI: " + e.getMessage());
            System.exit(1);
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
cd /Users/garen/Code/java/smile_cli/smile_cli && mvn compile
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/github/loong/Main.java
git commit -m "feat: implement Main interaction loop with conversation history"
```

---

### Task 6: Build fat-jar and verify end-to-end

**Files:** None

- [ ] **Step 1: Package the fat-jar**

```bash
cd /Users/garen/Code/java/smile_cli/smile_cli && mvn package -DskipTests
```

Expected: BUILD SUCCESS, produces `target/smile_cli-1.0-SNAPSHOT.jar`

- [ ] **Step 2: Verify JAR is runnable (no API key)**

```bash
cd /Users/garen/Code/java/smile_cli/smile_cli && java -jar target/smile_cli-1.0-SNAPSHOT.jar
```

Expected: Error message about missing DEEPSEEK_API_KEY, then exits.

- [ ] **Step 3: Verify maven test still passes**

```bash
cd /Users/garen/Code/java/smile_cli/smile_cli && mvn test
```

Expected: BUILD SUCCESS, tests pass.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "chore: verify fat-jar build and tests pass"
```
