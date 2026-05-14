package com.github.loong.message;

import java.util.LinkedHashMap;
import java.util.Map;

public class UserMessage extends Message {

    private final String content;

    public UserMessage(String content) {
        this.content = content;
    }

    @Override
    public String getRole() {
        return "user";
    }

    public String getContent() {
        return content;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("role", "user");
        map.put("content", content);
        return map;
    }
}
