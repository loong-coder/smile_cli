# smile_cli

`smile_cli` 是一个基于 Java 21 的命令行 AI 聊天客户端。项目通过 DeepSeek Chat Completions 流式接口与模型交互，并提供受工作区限制的本地工具调用能力，让模型可以在当前项目目录内读取文件、写入文件、列目录和执行非 shell 命令。

## 功能特性

- 交互式命令行聊天界面，支持彩色欢迎页、命令补全和流式输出。
- 默认接入 DeepSeek API，模型默认为 `deepseek-v4-pro`。
- 支持 OpenAI/DeepSeek 风格的 function calling 工具调用流程。
- 内置本地系统工具：
  - `read_file`：读取工作区内文本文件。
  - `write_file`：写入工作区内文本文件。
  - `list_directory`：列出工作区内目录内容。
  - `execute_command`：在工作区内执行非 shell 命令数组。
- 工具访问范围限制在 CLI 启动目录内，避免越权访问工作区外文件。
- 使用 Swagger 注解和 JSON Schema 自动生成工具描述。
- 错误日志写入 `logs/smile_cli-error.log`，支持按大小和日期滚动。

## 技术栈

- Java 21
- Maven
- JLine：终端输入、补全和 ANSI 输出
- OkHttp / okhttp-sse：HTTP 与 SSE 流式响应
- Jackson：JSON 序列化与反序列化
- Swagger Annotations + victools/jsonschema-generator：工具参数 Schema 生成
- Logback：日志记录
- JUnit 3.8.1：单元测试

## 项目结构

```text
src/main/java/com/github/loong/
├── Main.java                         # CLI 入口，装配配置、终端、模型客户端和工具注册表
├── chat/
│   ├── ChatContext.java              # 单次会话共享上下文
│   └── ChatLoop.java                 # REACT 聊天循环与内置命令处理
├── config/
│   └── LlmConfig.java                # 模型、API 地址和 API Key 配置
├── llm/
│   ├── LLmClient.java                # LLM 客户端接口
│   ├── LLmClientFactoryBuilder.java  # 客户端工厂
│   ├── ChatResult.java               # 单轮模型响应结果
│   └── impl/DeepSeekClient.java      # DeepSeek SSE 客户端实现
├── message/                          # system/user/assistant/tool 消息抽象
├── tool/
│   ├── LocalSystemTools.java         # 工作区内本地文件和命令工具
│   ├── ToolRegistry.java             # 工具定义与执行器注册
│   ├── ToolCallExecutor.java         # 模型工具调用执行入口
│   ├── ReflectiveToolExecutor.java   # 反射调用 Java 方法
│   └── util/SwaggerToolDescriptionParser.java
└── ui/
    └── TerminalManager.java          # 终端 UI 渲染、输入和内置命令补全

src/main/resources/
└── logback.xml                       # 日志配置

src/test/java/com/github/loong/       # 单元测试
```

## 环境要求

- JDK 21+
- Maven 3.x+
- 可访问 DeepSeek API 的网络环境
- DeepSeek API Key

## 配置

### 1. 设置 API Key

程序从环境变量 `DEEPSEEK_API_KEY` 读取 API Key。

Git Bash / macOS / Linux：

```bash
export DEEPSEEK_API_KEY=your-key
```

PowerShell：

```powershell
$env:DEEPSEEK_API_KEY="your-key"
```

如果未设置该环境变量，启动后会显示配置提示并退出。

### 2. 可选配置文件

启动时会读取用户目录下的配置文件：

```text
~/.smile_cli/config
```

配置文件使用 Java Properties 格式，可覆盖默认模型名称、展示版本和 API 地址：

```properties
model.name=deepseek-v4-pro
model.displayVersion=deepseek v4 pro
api.baseUrl=https://api.deepseek.com
```

`api.baseUrl` 末尾的 `/` 会被自动去除，请不要在配置中包含 `/v1/chat/completions` 路径，程序会自动拼接该接口路径。

## 构建

```bash
mvn compile
```

## 运行测试

```bash
mvn test
```

## 打包

```bash
mvn package
```

打包使用 `maven-shade-plugin` 生成可执行 JAR，主类为：

```text
com.github.loong.Main
```

## 运行

打包后可运行生成的 shaded JAR：

```bash
java -jar target/smile_cli-1.0-SNAPSHOT.jar
```

启动后会进入交互式聊天界面，提示符为：

```text
> 
```

## CLI 内置命令

| 命令 | 说明 |
| --- | --- |
| `/help` | 查看帮助信息 |
| `/tools` | 查看已注册工具 |
| `/clear` | 清空终端输出 |
| `/exit`、`/quit` | 退出 CLI |
| `Ctrl+D` | 退出 CLI |

输入 `/` 时会触发命令补全。

## 工具调用机制

程序启动时会注册 `LocalSystemTools`，并将工具定义传给模型。模型返回工具调用后，`ChatLoop` 会执行以下流程：

1. 将用户消息加入会话历史。
2. 调用 `LLmClient.chat(...)` 发起流式模型请求。
3. 如果模型返回普通文本，则实时输出并保存 assistant 消息。
4. 如果模型返回工具调用，则通过 `ToolCallExecutor` 执行工具。
5. 将工具执行结果作为 tool 消息加入历史，再次请求模型。
6. 单轮最多允许 5 轮工具调用，超过后会终止本轮回复。

内置工具的文件路径都会被解析到 CLI 启动目录内，越权访问工作区外路径会失败。

## 日志

Logback 会将 INFO 及以上级别日志写入：

```text
logs/smile_cli-error.log
```

滚动策略：

- 单文件最大 10MB
- 保留 14 天
- 总大小上限 100MB

## 开发说明

- 本项目没有 Maven Wrapper，请使用系统安装的 `mvn` 命令。
- 新增工具时，可以在 public 方法上添加 Swagger `@Schema` 注解，工具注册表会自动生成工具名称、描述、输入 Schema 和输出 Schema。
- `maven-compiler-plugin` 开启了 `parameters=true`，未显式声明 `@Schema(name=...)` 时可使用 Java 参数名生成工具参数名。
- 测试位于 `src/test/java`，当前使用 JUnit 3 风格测试类。