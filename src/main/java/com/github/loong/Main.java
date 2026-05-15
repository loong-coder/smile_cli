package com.github.loong;

import com.github.loong.chat.ChatContext;
import com.github.loong.chat.ChatLoop;
import com.github.loong.config.LlmConfig;
import com.github.loong.llm.LLmClient;
import com.github.loong.llm.LLmClientFactoryBuilder;
import com.github.loong.tools.function.LocalSystemTools;
import com.github.loong.tools.executor.ToolCallExecutor;
import com.github.loong.tools.ToolRegistry;
import com.github.loong.ui.TerminalManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;

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
                // 本地工具只允许访问 CLI 启动目录内的资源。
                registry.register(new LocalSystemTools(Paths.get("").toAbsolutePath()));
                // 会话上下文集中维护聊天循环需要共享的运行状态。
                ChatContext context = ChatContext.builder()
                        .terminalManager(terminal)
                        .llmClient(client)
                        .toolCallExecutor(new ToolCallExecutor(registry))
                        .toolDefinitions(registry.definitions())
                        .messages(new ArrayList<>())
                        .agentSystemPrompts(new LinkedHashMap<>())
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