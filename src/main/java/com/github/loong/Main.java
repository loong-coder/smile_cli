package com.github.loong;

import com.github.loong.chat.ChatContext;
import com.github.loong.chat.ChatLoop;
import com.github.loong.config.LlmConfig;
import com.github.loong.llm.LLmClient;
import com.github.loong.llm.LLmClientFactoryBuilder;
import com.github.loong.message.Message;
import com.github.loong.message.SystemMessage;
import com.github.loong.tools.function.LocalSystemTools;
import com.github.loong.tools.executor.ToolCallExecutor;
import com.github.loong.tools.ToolRegistry;
import com.github.loong.ui.TerminalManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

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
                String systemMessage = """
                        你是由Garen开发的一个智能编程助手。
                        1.你可以使用工具来完成你的任务。
                        2.当需要操作文件、执行命令或创建项目时，请使用工具调用。
                        3.使用工具后，根据工具返回的结果继续思考下一步行动。
                        4.当任务可以直接回答时，禁止使用工具。
                        """;
                Map<String, Message> systemMessageMap = Map.of("MainAgent", new SystemMessage(systemMessage));
                ChatContext context = ChatContext.builder()
                        .terminalManager(terminal)
                        .llmClient(client)
                        .toolCallExecutor(new ToolCallExecutor(registry))
                        .toolDefinitions(registry.definitions())
                        .messages(new ArrayList<>())
                        .agentSystemPrompts(systemMessageMap)
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