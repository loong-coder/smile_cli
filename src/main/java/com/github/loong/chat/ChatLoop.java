package com.github.loong.chat;

import com.github.loong.llm.ChatResult;
import com.github.loong.message.AssistantMessage;
import com.github.loong.message.Message;
import com.github.loong.message.ToolMessage;
import com.github.loong.message.UserMessage;
import com.github.loong.ui.TerminalManager;

import java.util.List;

/**
 * REACT loop过程
 */
public class ChatLoop {

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
                ui.printInfo("Commands:");
                ui.printInfo("  /exit, /quit  - Exit the CLI");
                ui.printInfo("  /help         - Show this help");
                ui.printInfo("  Ctrl+D        - Exit the CLI");
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
                ui.printError("Interrupted");
                messages.add(new AssistantMessage("[系统中断，本轮回复失败]"));
            } catch (Exception e) {
                ui.printError(e.getMessage());
                messages.add(new AssistantMessage("[系统异常，本轮回复失败]"));
            }
        }
    }

    private static void runAssistantTurn(ChatContext context) throws Exception {
        @SuppressWarnings("resource")
        TerminalManager ui = context.terminalManager();
        List<Message> messages = context.messages();
        int maxToolRounds = 5;
        for (int round = 0; round < maxToolRounds; round++) {
            @SuppressWarnings("resource")
            ChatResult result = context.llmClient().chat(messages, context.toolDefinitions(), ui::printToken, ui::printError);
            if (!result.hasToolCalls()) {
                if (!result.content().isEmpty()) {
                    messages.add(new AssistantMessage(result.content()));
                } else {
                    messages.add(new AssistantMessage("[模型未返回有效内容，本轮回复失败]"));
                }
                return;
            }

            messages.add(new AssistantMessage(result.content(), result.toolCalls()));
            for (AssistantMessage.ToolCall call : result.toolCalls()) {
                messages.add(new ToolMessage(call.id(), context.toolCallExecutor().execute(call)));
            }
        }
        messages.add(new AssistantMessage("[工具调用轮次过多，本轮回复中止]"));
    }
}
