package com.github.loong.chat;

import com.github.loong.llm.ChatResult;
import com.github.loong.message.AssistantMessage;
import com.github.loong.message.Message;
import com.github.loong.message.ToolMessage;
import com.github.loong.message.UserMessage;
import com.github.loong.tool.ToolDefinition;
import com.github.loong.ui.TerminalManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * REACT loop过程
 */
public class ChatLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatLoop.class);

    public static void runChatLoop(ChatContext context) {

        @SuppressWarnings("resource")
        TerminalManager ui = context.terminalManager();
        List<Message> messages = context.messages();

        while (true) {
            String input = ui.readInput("> ");

            if (input == null) {
                break;
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
                for (String line : renderHelpLines()) {
                    ui.printInfo(line);
                }
                continue;
            }

            if ("/clear".equals(input)) {
                ui.clearScreen();
                continue;
            }

            if ("/tools".equals(input)) {
                for (String line : renderTools(context.toolDefinitions())) {
                    ui.printInfo(line);
                }
                continue;
            }

            UserMessage userMsg = new UserMessage(input);
            messages.add(userMsg);

            ui.println();
            try {
                runAssistantTurn(context);
                ui.println();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.error("Chat turn interrupted", e);
                ui.printError("Interrupted");
                messages.add(new AssistantMessage("[系统中断，本轮回复失败]"));
            } catch (Exception e) {
                LOGGER.error("Chat turn failed", e);
                ui.printError(e.getMessage());
                messages.add(new AssistantMessage("[系统异常，本轮回复失败]"));
            }
        }
    }

    public static List<String> renderHelpLines() {
        return List.of(
                "Commands:",
                "  /exit, /quit  - Exit the CLI",
                "  /help         - Show this help",
                "  /tools        - Show installed tools",
                "  /clear        - Clear console output",
                "  Ctrl+D        - Exit the CLI");
    }

    public static List<String> renderTools(List<ToolDefinition> tools) {
        List<String> lines = new ArrayList<>();
        lines.add("Installed tools:");
        if (tools.isEmpty()) {
            lines.add("  (none)");
            return lines;
        }

        for (ToolDefinition tool : tools) {
            // 工具描述为空时保留名称，避免 /tools 输出出现空行。
            String description = tool.description() == null || tool.description().isBlank()
                    ? "No description"
                    : tool.description();
            lines.add("  " + tool.name() + " - " + description);
        }
        return lines;
    }

    private static void runAssistantTurn(ChatContext context) throws Exception {
        @SuppressWarnings("resource")
        TerminalManager ui = context.terminalManager();
        List<Message> messages = context.messages();
        int maxToolRounds = 5;
        for (int round = 0; round < maxToolRounds; round++) {
            @SuppressWarnings("resource")
            ChatResult result = context.llmClient().chat(messages, context.toolDefinitions(), ui::printToken, ui::printError);
            // 存在工具调用 调用工具后返回
            if (result.hasToolCalls()) {
                messages.add(new AssistantMessage(result.content(), result.reasoningContent(), result.toolCalls()));
                for (AssistantMessage.ToolCall call : result.toolCalls()) {
                    messages.add(new ToolMessage(call.id(), context.toolCallExecutor().execute(call)));
                }
                continue;
            }

            if (!result.content().isEmpty()) {
                messages.add(new AssistantMessage(result.content()));
            } else {
                messages.add(new AssistantMessage("[模型未返回有效内容，本轮回复失败]"));
            }
            return;
        }
        messages.add(new AssistantMessage("[工具调用轮次过多，本轮回复中止]"));
    }
}
