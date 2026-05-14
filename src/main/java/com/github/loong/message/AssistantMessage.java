package com.github.loong.message;

import java.util.LinkedHashMap;
import java.util.Map;

public class AssistantMessage extends Message {

    private final String content;

    public AssistantMessage(String content) {
        this.content = content;
    }

    @Override
    public String getRole() {
        return "assistant";
    }

    public String getContent() {
        return content;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("role", "assistant");
        map.put("content", content);
        return map;
    }
}
