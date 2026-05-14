package com.github.loong.message;

import java.util.LinkedHashMap;
import java.util.Map;

public class SystemMessage extends Message {

    private final String content;

    public SystemMessage(String content) {
        this.content = content;
    }

    @Override
    public String getRole() {
        return "system";
    }

    public String getContent() {
        return content;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("role", "system");
        map.put("content", content);
        return map;
    }
}
