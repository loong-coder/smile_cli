package com.github.loong.llm;

import com.github.loong.message.AssistantMessage;

import java.util.List;

public record ChatResult(String content, List<AssistantMessage.ToolCall> toolCalls, String finishReason) {

    public ChatResult {
        content = content == null ? "" : content;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}