package com.github.loong.message;

import java.util.LinkedHashMap;
import java.util.Map;

public class ToolMessage extends Message {

    private final String content;
    private final String toolCallId;

    public ToolMessage(String toolCallId, String content) {
        this.toolCallId = toolCallId;
        this.content = content;
    }

    @Override
    public String getRole() {
        return "tool";
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public String getContent() {
        return content;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("role", "tool");
        map.put("tool_call_id", toolCallId);
        map.put("content", content);
        return map;
    }
}
