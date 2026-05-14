# SMILE CLI — 模型交互设计

日期: 2026-05-14

## 概述

实现一个 Java CLI 工具，通过终端与 DeepSeek 模型进行交互式对话，类似 Claude Code 的体验。

- 使用 `java -jar` 启动
- 欢迎页显示 "SMILE CLI" 品牌名和模型版本
- 默认模型 deepseek v4 pro
- 流式输出模型回复
- 单次会话，不持久化历史

## 依赖

| 库 | 版本 | 用途 |
|---|------|------|
| JLine | 3.25.1 | 终端交互、颜色、行编辑 |
| OkHttp | 4.12.0 | HTTP 客户端 |
| OkHttp SSE | 4.12.0 | 流式响应解析 |
| Jackson Databind | 2.17.0 | JSON 序列化 |
| JUnit | 3.8.1 | 测试（已有） |

Java 版本: 17

## 类结构

```
src/main/java/com/github/loong/
├── Main.java           # 入口 + 主循环
├── ConfigManager.java  # 配置加载
├── ModelClient.java    # API 调用 + SSE 流式
└── ConsoleUI.java      # JLine 终端渲染
```

## 组件职责

### ConfigManager

读取 `~/.smile_cli/config`（Properties 格式），fallback 到硬编码默认值。

| 配置项 | 默认值 | 来源 |
|--------|--------|------|
| `model.name` | `deepseek-chat` | 配置文件 |
| `model.displayVersion` | `deepseek v4 pro` | 配置文件 |
| `api.baseUrl` | `https://api.deepseek.com` | 配置文件 |
| API Key | — | `DEEPSEEK_API_KEY` 环境变量 |

### ConsoleUI

基于 JLine 的 Terminal 封装：

- `showWelcome()` — ASCII art "SMILE CLI" + 模型显示版本
- `readInput()` — 读取用户输入行
- `printToken(String)` — 逐 token 打印模型回复
- `printInfo(String)` / `printError(String)` — 带颜色信息输出

颜色：欢迎页 cyan 粗体，用户输入提示绿色，模型回复白色，信息 cyan，错误红色。

### ModelClient

使用 OkHttp + SSE 与 DeepSeek API 交互：

- `chat(prompt, onToken, onError)` — 发送聊天请求，流式回调
- POST 到 `{baseUrl}/v1/chat/completions`
- 请求体：`{ model, messages: [{role:"user",content}], stream: true }`
- SSE data 解析为 JSON，提取 `choices[0].delta.content`
- 遇到 `[DONE]` 时结束
- 支持 Ctrl+C 中断流式输出

### Main

编排层：
1. 初始化 ConfigManager
2. 初始化 ConsoleUI
3. 显示欢迎页
4. 循环：读取输入 → 校验 → 调用 ModelClient 流式输出 → 回到输入

退出命令：`/exit`, `/quit`, Ctrl+D

## 数据流

```
用户输入 → Main.chat()
  → ModelClient.chat(prompt, ui::printToken, ui::printError)
    → SSE: data: {"choices":[{"delta":{"content":"..."}}]}*
    → data: [DONE]
  → 回到输入循环
```

## 错误处理

| 场景 | 行为 |
|------|------|
| 缺少 DEEPSEEK_API_KEY | 显示 "请设置 DEEPSEEK_API_KEY 环境变量"，退出 |
| 网络连接失败 | 显示错误，继续会话 |
| API 返回 4xx/5xx | 解析错误 body 显示，继续会话 |
| SSE 流中断 | 显示已收到内容 + 中断提示 |

## 构建与运行

```bash
mvn package
java -jar target/smile_cli-1.0-SNAPSHOT.jar
```

Maven Shade 插件打包 fat-jar 以便 `java -jar` 直接运行。
