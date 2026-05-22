# Agent 长短期记忆设计

## 背景

当前 Java CLI 项目已有 `Agent` 主循环、`ChatContext` 对话上下文和 `TokenizerUtil` token 估算工具，但没有独立的记忆系统。目标是在 agent 中实现短期记忆、长期记忆、上下文阈值压缩、重复记忆去重和相关记忆检索。

## 目标

- 使用 `MemoryEntry` 表达统一记忆对象，包含 `id`、`content`、`type`、`timestamp`、`metadata`、`tokenCount`。
- 使用现有 `TokenizerUtil` 估算上下文大小。
- 最大上下文固定为 1M token，达到 80% 即 800k token 时触发压缩。
- 同时在 LLM 调用前和 LLM 回复后检查上下文。
- 压缩时保留最近 5 轮对话，其余短期记忆归档到长期记忆。
- 长期记忆保存到 SQLite，并用 JSON 文本保存内容、metadata 和 embedding。
- 通过可插拔 embedding 接口实现去重和检索；第一版使用纯 Java 特征哈希向量。
- 检索时使用余弦相似度，默认最多加载 Top 10，且总 token 不超过上下文 5%。

## 非目标

- 第一版不引入 ONNX Runtime、DJL 或实际本地 embedding 模型文件。
- 第一版不做跨会话多用户隔离；默认面向当前 CLI 本地使用场景。
- 第一版不改变底层 LLM 协议，只在现有请求消息前注入记忆上下文。

## 架构

新增 `memory` 包，记忆能力独立于 `Agent` 和 `ChatContext` 的内部实现。

### 核心类型

- `MemoryEntry`：不可变记忆对象，类型包括：
  - `CONVERSATION`：普通对话记忆。
  - `FACT`：事实记忆，例如用户偏好和项目信息。
  - `SUMMARY`：压缩后的摘要记忆。
  - `TOOL_RESULT`：工具执行结果。
- `MemoryService`：记忆系统统一入口，负责记录短期记忆、触发压缩、写入长期记忆、检索长期记忆并生成上下文注入内容。
- `ShortTermMemory`：维护当前会话短期记忆，支持按最近 5 轮保留。
- `LongTermMemoryStore`：长期记忆存储接口。
- `SqliteLongTermMemoryStore`：SQLite 实现，负责建表、写入、查询和加载记忆。
- `EmbeddingProvider`：embedding 可插拔接口。
- `HashingEmbeddingProvider`：第一版纯 Java 特征哈希向量实现。
- `MemoryDeduplicator`：结合规范化文本哈希和余弦相似度去重。
- `MemoryCompressor`：混合压缩策略。
- `MemoryRetriever`：根据查询向量检索相关长期记忆。

## 存储设计

SQLite 表保存长期记忆，每条记录包含：

- `id`：记忆 ID。
- `type`：记忆类型。
- `timestamp`：ISO-8601 时间文本。
- `token_count`：token 数量。
- `content_json`：包含内容的 JSON 文本。
- `metadata_json`：metadata JSON 文本。
- `embedding_json`：浮点向量 JSON 文本。
- `content_hash`：归一化内容哈希，用于完全重复检测。

JSON 文本用于保持结构灵活，SQLite 字段用于常用过滤和去重。

## 数据流

每轮对话执行顺序如下：

1. 用户输入进入 `ChatContext` 后，记录到 `ShortTermMemory`。
2. 调用 LLM 前，`MemoryService` 通过 `TokenizerUtil` 统计当前上下文 token。
3. 如果上下文达到 800k token：
   - 保留最近 5 轮对话；
   - 旧的工具结果和事实记忆直接转入长期记忆；
   - 旧的普通对话批量调用 LLM 生成 `SUMMARY`；
   - 长期保存前先做重复检测。
4. 根据当前用户输入生成 query embedding。
5. 从 SQLite 加载长期记忆到内存，计算余弦相似度。
6. 选择 Top 10，且总 token 不超过 50k token。
7. 将检索结果组织成一条记忆上下文消息，注入本轮 LLM 请求。
8. LLM 回复完成后，把 assistant 回复写入短期记忆。
9. 再次检查上下文；如仍达到阈值，立即压缩，为下一轮准备。

## 压缩策略

压缩采用混合策略：

- `FACT` 和 `TOOL_RESULT` 不摘要，直接长期归档。
- `CONVERSATION` 批量调用 LLM 生成 `SUMMARY` 后归档。
- 最近 5 轮对话始终保留在短期记忆中。
- 摘要生成失败时，不删除原短期记忆；保留原状态并向调用方返回失败信息。

## 去重策略

长期记忆写入前执行两层去重：

1. 规范化文本哈希：去除多余空白、统一大小写后计算哈希，命中即视为完全重复。
2. embedding 余弦相似度：使用 `EmbeddingProvider` 生成向量，与已有长期记忆比较，默认相似度达到 0.92 即视为近似重复。

重复内容默认跳过写入；如新 metadata 包含更多信息，可合并 metadata 后更新原记录。

## 检索策略

检索使用动态预算：

- query 来自当前用户输入，也可合并最近短期上下文。
- 默认最多返回 Top 10。
- 总 token 预算为 1M 的 5%，即最多 50k token。
- 按余弦相似度从高到低选择，低于基础阈值的记忆不注入。
- 注入内容以清晰文本组织，标明记忆类型和时间，避免和用户原始输入混淆。

## Agent 集成点

`Agent` 主循环只调用 `MemoryService`，不直接操作 SQLite 或 embedding：

- 用户消息加入上下文后调用 `recordUserMessage`。
- LLM 调用前调用 `prepareContextBeforeLlmCall`，完成阈值检查、必要压缩和长期记忆检索。
- 工具调用完成后调用 `recordToolResult`。
- assistant 回复完成后调用 `recordAssistantMessage`，再调用 `compressAfterLlmResponse`。

这种集成方式让记忆逻辑集中在 `memory` 包中，降低对现有对话和工具调用流程的侵入。

## 错误处理

- SQLite 初始化或写入失败时，不阻塞普通对话，但应输出明确错误并跳过长期记忆能力。
- embedding 失败时，退化为文本哈希去重和无向量检索。
- LLM 摘要失败时，不删除待压缩短期记忆，下一次仍可重试。
- 记忆注入内容超预算时，按相似度截断。

## 测试计划

- `MemoryEntry` 创建后字段不可变，tokenCount 正确保存。
- `TokenizerUtil` 可用于估算记忆和上下文 token。
- `ShortTermMemory` 压缩时只保留最近 5 轮。
- `SqliteLongTermMemoryStore` 能写入和读取 JSON 记忆。
- `MemoryDeduplicator` 能识别完全重复内容。
- `MemoryDeduplicator` 能通过余弦相似度识别近似重复内容。
- `MemoryRetriever` 按相似度排序并遵守 Top 10 和 50k token 预算。
- `MemoryService` 在 LLM 调用前和回复后都能触发阈值检查。
- Agent 集成测试覆盖用户消息、工具结果、assistant 回复的记录路径。

## 实现顺序建议

1. 添加 memory 基础模型和接口。
2. 实现纯 Java embedding、余弦相似度和去重。
3. 实现 SQLite 长期记忆存储。
4. 实现短期记忆和压缩策略。
5. 实现检索和上下文注入。
6. 集成到 Agent 主循环。
7. 补充单元测试和必要的集成测试。