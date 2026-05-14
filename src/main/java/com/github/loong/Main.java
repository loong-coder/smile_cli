package com.github.loong;

import com.github.loong.message.AssistantMessage;
import com.github.loong.message.Message;
import com.github.loong.message.UserMessage;

import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        ConfigManager config = new ConfigManager();

        if (!config.hasApiKey()) {
            System.err.println("Error: DEEPSEEK_API_KEY environment variable is not set.");
            System.err.println("Please set it with: export DEEPSEEK_API_KEY=your-key");
            System.exit(1);
        }

        try (ConsoleUI ui = new ConsoleUI(config);
             ModelClient client = new ModelClient(config)) {

            ui.showWelcome();

            List<Message> messages = new ArrayList<>();

            while (true) {
                String input = ui.readInput("> ");

                if (input == null) {
                    break; // Ctrl+D
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
                    }, error -> {
                        ui.printError(error);
                    });

                    String fullResponse = response.toString();
                    if (!fullResponse.isEmpty()) {
                        AssistantMessage assistantMsg = new AssistantMessage(fullResponse);
                        messages.add(assistantMsg);
                    } else {
                        messages.remove(messages.size() - 1);
                    }

                    ui.println();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    ui.printError("Interrupted");
                    messages.remove(messages.size() - 1);
                } catch (Exception e) {
                    ui.printError(e.getMessage());
                    messages.remove(messages.size() - 1);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to initialize CLI: " + e.getMessage());
            System.exit(1);
        }
    }
}
