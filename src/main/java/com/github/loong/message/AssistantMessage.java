package com.github.loong.message;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AssistantMessage extends Message {

    private final String content;
    private final String reasoningContent;
    private final List<ToolCall> toolCalls;

    public AssistantMessage(String content) {
        this(content, null, List.of());
    }

    public AssistantMessage(String content, String reasoningContent, List<ToolCall> toolCalls) {
        this.content = content;
        this.reasoningContent = reasoningContent;
        this.toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    @Override
    public String getRole() {
        return "assistant";
    }

    public String getContent() {
        return content;
    }

    public String getReasoningContent() {
        return reasoningContent;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public boolean hasReasoningContent() {
        return reasoningContent != null && !reasoningContent.isBlank();
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("role", "assistant");
        map.put("content", content);
        if (!toolCalls.isEmpty()) {
            List<Map<String, Object>> callMaps = toolCalls.stream().map(ToolCall::toMap).toList();
            map.put("tool_calls", callMaps);
        }
        if (reasoningContent != null && !reasoningContent.isBlank()) {
            map.put("reasoning_content", reasoningContent);
        }
        return map;
    }

    public record ToolCall(String id, String name, String argumentsJson) {

        public Map<String, Object> toMap() {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", name);
            function.put("arguments", argumentsJson == null ? "{}" : argumentsJson);

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", id);
            map.put("type", "function");
            map.put("function", function);
            return map;
        }
    }
}