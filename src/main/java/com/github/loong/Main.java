package com.github.loong;

import com.github.loong.config.LlmConfig;
import com.github.loong.message.AssistantMessage;
import com.github.loong.message.Message;
import com.github.loong.message.UserMessage;
import com.github.loong.model.LLmClient;
import com.github.loong.model.LLmClientFactoryBuilder;
import com.github.loong.ui.ConsoleUI;

import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        int exitCode = run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args) {
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
        } catch (Exception e) {
            System.err.println("Failed to initialize CLI: " + e.getMessage());
            return 1;
        }
    }

    private static void runChatLoop(ConsoleUI ui, LLmClient client) {
        List<Message> messages = new ArrayList<>();

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

            StringBuilder response = new StringBuilder();

            ui.println();
            try {
                client.chat(messages, token -> {
                    response.append(token);
                    ui.printToken(token);
                }, ui::printError);

                String fullResponse = response.toString();
                if (!fullResponse.isEmpty()) {
                    AssistantMessage assistantMsg = new AssistantMessage(fullResponse);
                    messages.add(assistantMsg);
                } else {
                    messages.add(new AssistantMessage("[模型未返回有效内容，本轮回复失败]"));
                }

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
}