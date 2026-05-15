# SMILE CLI 模型交互 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现通过终端与 DeepSeek 模型进行流式对话的 Java CLI 工具。

**Architecture:** 当前实现按职责拆成配置、终端 UI、消息类型、模型客户端抽象、DeepSeek 具体客户端、客户端工厂、Main 编排入口：

- `com.github.loong.config.LlmConfig`：读取 `~/.smile_cli/config` 与 `DEEPSEEK_API_KEY`，提供模型名、展示版本、base URL、API key 状态。
- `com.github.loong.message.Message` 及子类：用强类型消息替代 `Map<String, String>`，由 `toMap()` 转换为 API 请求体。
- `com.github.loong.model.LLmClient`：模型客户端接口，负责流式对话、取消与关闭资源。
- `com.github.loong.model.DeepSeekClient`：基于 OkHttp SSE 调用 DeepSeek OpenAI-compatible chat completions API。
- `com.github.loong.model.LLmClientFactoryBuilder`：从 `LlmConfig` 构建 `LLmClient`，当前返回 `DeepSeekClient`。
- `com.github.loong.ui.ConsoleUI`：JLine 终端输入、命令补全、欢迎页、错误页和彩色输出。
- `com.github.loong.Main`：初始化配置/UI/client，维护 `List<Message>` 对话历史并驱动交互循环。

**Type sync notes:**

- 旧 `ConfigManager` 已同步为 `LlmConfig`。
- 旧 `ModelClient` 已拆为 `LLmClient` 接口 + `DeepSeekClient` 实现。
- 旧 `List<Map<String, String>> messages` 已同步为 `List<Message>`，用户和助手消息分别使用 `UserMessage`、`AssistantMessage`。
- 工厂类型为 `LLmClientFactoryBuilder.fromConfig(LlmConfig).build()`，返回接口类型 `LLmClient`。

**Tech Stack:** Java 21, Maven, JLine 3.25.1, OkHttp 4.12.0, OkHttp SSE 4.12.0, Jackson 2.17.0, Maven Shade Plugin 3.5.1, JUnit 3.8.1

---

### Task 1: Update pom.xml with dependencies and build config

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Configure Java and dependency versions**

Use Java 21 and keep dependency versions in Maven properties:

```xml
<properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <maven.compiler.source>21</maven.compiler.source>
    <maven.compiler.target>21</maven.compiler.target>
    <jline.version>3.25.1</jline.version>
    <okhttp.version>4.12.0</okhttp.version>
    <jackson.version>2.17.0</jackson.version>
    <junit.version>3.8.1</junit.version>
</properties>
```

- [ ] **Step 2: Add runtime and test dependencies**

Required dependencies:

- `org.jline:jline:${jline.version}`
- `com.squareup.okhttp3:okhttp:${okhttp.version}`
- `com.squareup.okhttp3:okhttp-sse:${okhttp.version}`
- `com.fasterxml.jackson.core:jackson-databind:${jackson.version}`
- `junit:junit:${junit.version}` with `test` scope

- [ ] **Step 3: Configure executable shaded JAR**

Use `maven-shade-plugin` with:

- `ManifestResourceTransformer` main class: `com.github.loong.Main`
- `ServicesResourceTransformer` for service metadata merging

- [ ] **Step 4: Verify Maven build works**

```bash
mvn compile
```

Expected: `BUILD SUCCESS`.

---

### Task 2: Implement LlmConfig

**Files:**
- Create/modify: `src/main/java/com/github/loong/config/LlmConfig.java`

- [ ] **Step 1: Define configuration type**

`LlmConfig` owns all model/API configuration:

```java
package com.github.loong.config;

public class LlmConfig {
    public String getModelName();
    public String getDisplayVersion();
    public String getBaseUrl();
    public String getApiKey();
    public boolean hasApiKey();
}
```

- [ ] **Step 2: Load defaults and config file**

Defaults:

- `model.name=deepseek-v4-pro`
- `model.displayVersion=deepseek v4 pro`
- `api.baseUrl=https://api.deepseek.com`

Load optional overrides from:

```text
~/.smile_cli/config
```

- [ ] **Step 3: Read API key from environment**

`getApiKey()` reads:

```text
DEEPSEEK_API_KEY
```

`hasApiKey()` returns `true` only when the key is non-null and non-blank.

- [ ] **Step 4: Normalize base URL**

`getBaseUrl()` should remove a trailing `/` so API paths can be appended safely.

---

### Task 3: Implement strongly typed messages

**Files:**
- Create/modify: `src/main/java/com/github/loong/message/Message.java`
- Create/modify: `src/main/java/com/github/loong/message/UserMessage.java`
- Create/modify: `src/main/java/com/github/loong/message/AssistantMessage.java`
- Create/modify: `src/main/java/com/github/loong/message/SystemMessage.java`
- Create/modify: `src/main/java/com/github/loong/message/ToolMessage.java`

- [ ] **Step 1: Define Message base type**

```java
package com.github.loong.message;

import java.util.Map;

public abstract class Message {
    public abstract String getRole();
    public abstract Map<String, Object> toMap();
}
```

- [ ] **Step 2: Implement content messages**

`UserMessage`、`AssistantMessage`、`SystemMessage` each store `content`, expose `getContent()`, return the correct role, and serialize to:

```java
Map.of("role", role, "content", content)
```

Use `LinkedHashMap` when preserving JSON field order is useful.

- [ ] **Step 3: Implement ToolMessage**

`ToolMessage` stores `toolCallId` and `content`, returns role `tool`, and serializes to:

```java
{
    "role": "tool",
    "tool_call_id": toolCallId,
    "content": content
}
```

- [ ] **Step 4: Use typed messages everywhere**

Application conversation history should be:

```java
List<Message> messages = new ArrayList<>();
```

Do not pass `List<Map<String, String>>` through the app layer.

---

### Task 4: Implement ConsoleUI

**Files:**
- Create/modify: `src/main/java/com/github/loong/ui/ConsoleUI.java`

- [ ] **Step 1: Use LlmConfig in UI constructor**

```java
package com.github.loong.ui;

import com.github.loong.config.LlmConfig;

public class ConsoleUI implements AutoCloseable {
    public ConsoleUI(LlmConfig config) throws IOException;
}
```

- [ ] **Step 2: Configure JLine terminal and reader**

Use JLine `TerminalBuilder` and `LineReaderBuilder` with command completion for:

- `/exit`
- `/quit`
- `/help`

- [ ] **Step 3: Render terminal output**

Required public methods:

```java
void showWelcome();
void printSetupError();
String readInput(String prompt);
void printToken(String token);
void printInfo(String msg);
void printError(String msg);
void printWarning(String msg);
void println();
void close();
```

- [ ] **Step 4: Keep display width helpers package-private where tests need access**

Helpers such as `renderWelcomeLines`、`renderSetupErrorLines`、`padVisibleRight`、`visibleWidth`、`stripAnsi` can remain package-private/static so `ConsoleUITest` can verify layout behavior.

---

### Task 5: Implement LLmClient abstraction and DeepSeek client

**Files:**
- Create/modify: `src/main/java/com/github/loong/model/LLmClient.java`
- Create/modify: `src/main/java/com/github/loong/model/DeepSeekClient.java`

- [ ] **Step 1: Define LLmClient interface**

```java
package com.github.loong.model;

import com.github.loong.message.Message;

import java.util.List;
import java.util.function.Consumer;

public interface LLmClient extends AutoCloseable {
    void chat(List<Message> messages,
              Consumer<String> onToken,
              Consumer<String> onError) throws Exception;

    void cancel();
}
```

- [ ] **Step 2: Implement DeepSeekClient**

`DeepSeekClient` constructor accepts `LlmConfig`:

```java
public class DeepSeekClient implements LLmClient {
    public DeepSeekClient(LlmConfig config) { ... }
}
```

It should:

- validate `config.getApiKey()` before making a request;
- convert each `Message` with `message.toMap()`;
- POST to `config.getBaseUrl() + "/v1/chat/completions"`;
- send `model`, `messages`, and `stream=true` in the JSON body;
- stream SSE chunks and pass `choices[0].delta.content` to `onToken`;
- pass connection/API failures to `onError`;
- support `cancel()` by cancelling the active `EventSource`;
- implement `close()` by cancelling and shutting down OkHttp resources.

- [ ] **Step 3: Keep model-specific protocol details inside DeepSeekClient**

`Main` and `ConsoleUI` should only depend on `LLmClient`, not on DeepSeek SSE details.

---

### Task 6: Implement LLmClientFactoryBuilder

**Files:**
- Create/modify: `src/main/java/com/github/loong/model/LLmClientFactoryBuilder.java`

- [ ] **Step 1: Build clients from LlmConfig**

```java
package com.github.loong.model;

import com.github.loong.config.LlmConfig;

public class LLmClientFactoryBuilder {
    private final LlmConfig config;

    private LLmClientFactoryBuilder(LlmConfig config) {
        this.config = config;
    }

    public static LLmClientFactoryBuilder fromConfig(LlmConfig config) {
        return new LLmClientFactoryBuilder(config);
    }

    public LLmClient build() {
        return new DeepSeekClient(config);
    }
}
```

- [ ] **Step 2: Preserve interface return type**

Callers should receive `LLmClient`, not `DeepSeekClient`, so the app can add other providers later without changing `Main`.

- [ ] **Step 3: Future provider extension point**

When multiple providers are introduced, add provider selection to `LlmConfig` and dispatch inside `build()` or replace the hard-coded construction with a registry of provider factories.

---

### Task 7: Implement Main entry point and interaction loop

**Files:**
- Modify: `src/main/java/com/github/loong/Main.java`

- [ ] **Step 1: Initialize config, UI, and client**

`Main` should use current types:

```java
LlmConfig config = new LlmConfig();

try (ConsoleUI ui = new ConsoleUI(config)) {
    ui.showWelcome();

    if (!config.hasApiKey()) {
        ui.printSetupError();
        return 1;
    }

    try (LLmClient client = LLmClientFactoryBuilder.fromConfig(config).build()) {
        runChatLoop(ui, client);
    }

    return 0;
}
```

- [ ] **Step 2: Keep conversation history as Message objects**

```java
List<Message> messages = new ArrayList<>();
```

For each user turn:

```java
UserMessage userMsg = new UserMessage(input);
messages.add(userMsg);
```

After a non-empty streamed response:

```java
AssistantMessage assistantMsg = new AssistantMessage(fullResponse);
messages.add(assistantMsg);
```

- [ ] **Step 3: Stream output through UI**

`client.chat(...)` should append streamed tokens to a `StringBuilder` and call `ui.printToken(token)` for immediate terminal output.

- [ ] **Step 4: Remove failed user turns**

If the model returns no content or throws, remove the latest user message so failed requests do not pollute conversation history.

- [ ] **Step 5: Support built-in commands**

Supported commands:

- `/exit`
- `/quit`
- `/help`
- `Ctrl+D` to exit

---

### Task 8: Add tests and verify end-to-end

**Files:**
- Create/modify: `src/test/java/com/github/loong/model/LLmClientBuilderTest.java`
- Create/modify: `src/test/java/com/github/loong/ui/ConsoleUITest.java`

- [ ] **Step 1: Test client factory construction**

`LLmClientBuilderTest` should verify:

```java
LLmClient client = LLmClientFactoryBuilder.fromConfig(new LlmConfig()).build();
try {
    assertNotNull(client);
} finally {
    client.close();
}
```

Because `LLmClient` extends `AutoCloseable`, the test method must either declare `throws Exception` or catch the checked exception from `client.close()`.

- [ ] **Step 2: Test ConsoleUI rendering helpers**

`ConsoleUITest` should cover width normalization, ANSI stripping, wide character width, truncation, and setup/welcome card rendering.

- [ ] **Step 3: Verify compilation**

```bash
mvn compile
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Verify tests**

```bash
mvn test
```

Expected: `BUILD SUCCESS`, tests pass.

- [ ] **Step 5: Build executable JAR**

```bash
mvn package -DskipTests
```

Expected: produces `target/smile_cli-1.0-SNAPSHOT.jar`.

- [ ] **Step 6: Verify no API key setup path**

```bash
java -jar target/smile_cli-1.0-SNAPSHOT.jar
```

Expected without `DEEPSEEK_API_KEY`: rendered setup guidance and exit code `1`.