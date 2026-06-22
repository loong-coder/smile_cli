package com.github.loong.agent;

import com.github.loong.chat.ChatContext;
import com.github.loong.llm.ChatResult;
import com.github.loong.llm.LLmClient;
import com.github.loong.message.*;
import com.github.loong.ui.TerminalManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Agent {

    private static final Logger LOGGER = LoggerFactory.getLogger(Agent.class);

    private ChatContext context;

    private String agentName;


    public Agent(ChatContext context, String agentName) {
        this.context = context;
        this.agentName = agentName;
    }

    public void chat(String input) {

        UserMessage userMsg = new UserMessage(input);
        List<Message> messageList = context.messages();
        messageList.add(userMsg);
        if (context.memoryService() != null) {
            context.memoryService().recordUserMessage(input);
        }

        @SuppressWarnings("resource")
        TerminalManager terminalManager = context.terminalManager();
        List<Message> messages = context.messages();

        Map<String, Message> agentSystemPrompts = context.agentSystemPrompts();

        String systemMessage = """
                你是由Garen开发的一个智能编程助手。
                1.你可以使用工具来完成你的任务。
                2.当需要操作文件、执行命令或创建项目时，请使用工具调用。
                3.使用工具后，根据工具返回的结果继续思考下一步行动。
                4.当任务可以直接回答时，禁止使用工具。
                """;

        Message mainAgent = agentSystemPrompts.getOrDefault("MainAgent", new SystemMessage(systemMessage));

        int maxToolRounds = 5;
        for (int round = 0; round < maxToolRounds; round++) {
            @SuppressWarnings("resource")
            LLmClient llmClient = context.llmClient();

            ChatResult result = null;
            try {
                List<Message> messagesNews = new ArrayList<>();
                messagesNews.add(mainAgent);
                if (context.memoryService() != null) {
                    Message memoryMessage = context.memoryService().buildMemoryContext(input);
                    if (memoryMessage != null) {
                        messagesNews.add(memoryMessage);
                    }
                }
                messagesNews.addAll(messageList);
                result = llmClient.chat(messagesNews,
                        context.toolDefinitions(),
                        terminalManager == null ? ignored -> { } : terminalManager::printToken,
                        terminalManager == null ? ignored -> { } : terminalManager::printThinking,
                        terminalManager == null ? ignored -> { } : terminalManager::printError);
            } catch (Exception e) {
                LOGGER.error("llm client char error", e);
                messages.add(new AssistantMessage("[模型调用过程发生一次]" + e.toString()));
            }
            if (result == null) {
                return;
            }
            // 存在工具调用 调用工具后返回
            if (result.hasToolCalls()) {
                AssistantMessage assistantMessage = new AssistantMessage(result.content(), result.reasoningContent(), result.toolCalls());
                messages.add(assistantMessage);
                if (context.memoryService() != null) {
                    context.memoryService().recordAssistantToolCall(result.content());
                }
                for (AssistantMessage.ToolCall call : result.toolCalls()) {
                    String toolResult = context.toolCallExecutor().execute(call);
                    messages.add(new ToolMessage(call.id(), toolResult));
                    if (context.memoryService() != null) {
                        context.memoryService().recordToolResult(call.id(), toolResult);
                    }
                }
                continue;
            }

            if (!result.content().isEmpty()) {
                messages.add(new AssistantMessage(result.content()));
                if (context.memoryService() != null) {
                    context.memoryService().recordAssistantMessage(result.content());
                }
            } else {
                String fallback = "[模型未返回有效内容，本轮回复失败]";
                messages.add(new AssistantMessage(fallback));
                if (context.memoryService() != null) {
                    context.memoryService().recordAssistantMessage(fallback);
                }
            }
            return;
        }
        messages.add(new AssistantMessage("[工具调用轮次过多，本轮回复中止]"));
    }
}
