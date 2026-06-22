package com.github.loong;

import com.github.loong.chat.ChatContext;
import com.github.loong.chat.ChatLoop;
import com.github.loong.config.LlmConfig;
import com.github.loong.llm.LLmClient;
import com.github.loong.llm.LLmClientFactoryBuilder;
import com.github.loong.memory.AliyunEmbeddingProvider;
import com.github.loong.memory.EmbeddingProvider;
import com.github.loong.memory.LongTermMemoryStore;
import com.github.loong.memory.MemoryConfig;
import com.github.loong.memory.MemoryRetriever;
import com.github.loong.memory.MemoryScorer;
import com.github.loong.memory.MemoryService;
import com.github.loong.memory.NoopMemoryService;
import com.github.loong.memory.QdrantLongTermMemoryStore;
import com.github.loong.memory.WorkspaceId;
import com.github.loong.tools.ToolRegistry;
import com.github.loong.tools.executor.ToolCallExecutor;
import com.github.loong.tools.function.LocalSystemTools;
import com.github.loong.ui.TerminalManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        int exitCode = run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args) {
        LlmConfig config = new LlmConfig();

        try (TerminalManager terminal = new TerminalManager(config)) {
            terminal.showWelcome();

            if (!config.hasApiKey()) {
                terminal.printSetupError();
                return 1;
            }

            try (LLmClient client = LLmClientFactoryBuilder.fromConfig(config).build()) {
                ToolRegistry registry = new ToolRegistry();
                var workspacePath = Paths.get("").toAbsolutePath();
                // 本地工具只允许访问 CLI 启动目录内的资源。
                registry.register(new LocalSystemTools(workspacePath));
                String workspaceId = WorkspaceId.fromPath(workspacePath);
                MemoryConfig memoryConfig = config.getMemoryConfig();
                MemoryService memoryService;
                if (!memoryConfig.enabled()) {
                    memoryService = new NoopMemoryService(workspaceId, memoryConfig, client);
                } else if (memoryConfig.hasLongTermConfig()) {
                    // 长期记忆依赖 embedding 和 Qdrant，二者配置完整时才启用。
                    EmbeddingProvider embeddingProvider = new AliyunEmbeddingProvider(memoryConfig);
                    LongTermMemoryStore store = new QdrantLongTermMemoryStore(memoryConfig);
                    MemoryScorer scorer = new MemoryScorer(memoryConfig.timeDecayHalfLifeDays());
                    MemoryRetriever retriever = new MemoryRetriever(memoryConfig, embeddingProvider, store, scorer);
                    memoryService = new MemoryService(workspaceId, memoryConfig, client, java.time.Clock.systemUTC(), embeddingProvider, store, retriever);
                } else {
                    // 配置不完整时保留短期记忆和摘要记忆，跳过长期记忆。
                    memoryService = MemoryService.localOnly(workspaceId, memoryConfig, client, java.time.Clock.systemUTC());
                }
                // 会话上下文集中维护聊天循环需要共享的运行状态。
                ChatContext context = ChatContext.builder()
                        .terminalManager(terminal)
                        .llmClient(client)
                        .toolCallExecutor(new ToolCallExecutor(registry))
                        .toolDefinitions(registry.definitions())
                        .messages(new ArrayList<>())
                        .agentSystemPrompts(new ConcurrentHashMap<>())
                        .memoryService(memoryService)
                        .build();
                ChatLoop.runChatLoop(context);
            }

            return 0;
        } catch (Exception e) {
            LOGGER.error("Failed to initialize CLI", e);
            System.err.println("Failed to initialize CLI: " + e.getMessage());
            return 1;
        }
    }


}