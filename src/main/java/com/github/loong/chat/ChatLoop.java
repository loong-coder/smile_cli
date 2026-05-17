package com.github.loong.chat;

import com.github.loong.agent.Agent;
import com.github.loong.message.AssistantMessage;
import com.github.loong.message.Message;
import com.github.loong.plan.PlanCommand;
import com.github.loong.tools.ToolDefinition;
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
        TerminalManager terminalManager = context.terminalManager();
        List<Message> messages = context.messages();

        label:
        while (true) {
            String input = terminalManager.readInput("> ");

            if (input == null) {
                break;
            }

            input = input.trim();

            switch (input) {
                case "":
                    continue;
                case "/exit":
                case "/quit":
                    terminalManager.printInfo("Goodbye!");
                    break label;
                case "/help":
                    for (String line : renderHelpLines()) {
                        terminalManager.printInfo(line);
                    }
                    continue;
                case "/clear":
                    terminalManager.clearScreen();
                    continue;
                case "/tools":
                    for (String line : renderTools(context.toolDefinitions())) {
                        terminalManager.printInfo(line);
                    }
                    continue;
                default:
                    // /plan 命令需要处理带参数的情况
                    if (input.startsWith("/plan")) {
                        String taskDesc = input.substring("/plan".length()).trim();
                        if (taskDesc.isEmpty()) {
                            terminalManager.printWarning("请提供任务描述，例如: /plan 实现用户登录功能");
                            continue;
                        }
                        terminalManager.println();
                        new PlanCommand(context).execute(taskDesc);
                        terminalManager.println();
                        continue;
                    }
            }

            terminalManager.println();
            try {
                Agent reactAgent = new Agent(context, "MainAgent");
                reactAgent.chat(input);
                terminalManager.println();
            } catch (Exception e) {
                LOGGER.error("Chat turn failed", e);
                terminalManager.printError(e.getMessage());
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
                "  /plan <desc>  - Generate task execution plan",
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
}
