package com.github.loong.chat;

import com.github.loong.llm.LLmClient;
import com.github.loong.message.Message;
import com.github.loong.tools.executor.ToolCallExecutor;
import com.github.loong.tools.ToolDefinition;
import com.github.loong.ui.TerminalManager;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 保存一次聊天会话共享的终端、模型、工具、消息历史和智能体提示词。
 */
public class ChatContext {

    private final TerminalManager terminalManager;
    private final LLmClient llmClient;
    private final ToolCallExecutor toolCallExecutor;
    private final List<ToolDefinition> toolDefinitions;
    private final List<Message> messages;
    private final Map<String, Message> agentSystemPrompts;

    private ChatContext(Builder builder) {
        this.terminalManager = Objects.requireNonNull(builder.terminalManager, "terminalManager cannot be null");
        this.llmClient = Objects.requireNonNull(builder.llmClient, "llmClient cannot be null");
        this.toolCallExecutor = Objects.requireNonNull(builder.toolCallExecutor, "toolCallExecutor cannot be null");
        this.toolDefinitions = List.copyOf(Objects.requireNonNull(builder.toolDefinitions, "toolDefinitions cannot be null"));
        this.messages = Objects.requireNonNull(builder.messages, "messages cannot be null");
        this.agentSystemPrompts = Objects.requireNonNull(builder.agentSystemPrompts, "agentSystemPrompts cannot be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    public TerminalManager terminalManager() {
        return terminalManager;
    }

    public LLmClient llmClient() {
        return llmClient;
    }

    public ToolCallExecutor toolCallExecutor() {
        return toolCallExecutor;
    }

    public List<ToolDefinition> toolDefinitions() {
        return toolDefinitions;
    }

    public List<Message> messages() {
        return messages;
    }

    public Map<String, Message> agentSystemPrompts() {
        return agentSystemPrompts;
    }

    /**
     * 分步收集会话依赖，避免构造函数参数持续膨胀。
     */
    public static class Builder {

        private TerminalManager terminalManager;
        private LLmClient llmClient;
        private ToolCallExecutor toolCallExecutor;
        private List<ToolDefinition> toolDefinitions;
        private List<Message> messages;
        private Map<String, Message> agentSystemPrompts;

        public Builder terminalManager(TerminalManager terminalManager) {
            this.terminalManager = terminalManager;
            return this;
        }

        public Builder llmClient(LLmClient llmClient) {
            this.llmClient = llmClient;
            return this;
        }

        public Builder toolCallExecutor(ToolCallExecutor toolCallExecutor) {
            this.toolCallExecutor = toolCallExecutor;
            return this;
        }

        public Builder toolDefinitions(List<ToolDefinition> toolDefinitions) {
            this.toolDefinitions = toolDefinitions;
            return this;
        }

        public Builder messages(List<Message> messages) {
            this.messages = messages;
            return this;
        }

        public Builder agentSystemPrompts(Map<String, Message> agentSystemPrompts) {
            this.agentSystemPrompts = agentSystemPrompts;
            return this;
        }

        public ChatContext build() {
            return new ChatContext(this);
        }
    }
}