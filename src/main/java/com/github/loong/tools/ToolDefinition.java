package com.github.loong.tools;

import java.util.LinkedHashMap;
import java.util.Map;

public record ToolDefinition(String name,
                             String title,
                             String description,
                             Map<String, Object> inputSchema,
                             Map<String, Object> outputSchema) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", name);
        map.put("title", title);
        map.put("description", description);
        map.put("inputSchema", inputSchema);
        if (outputSchema != null) {
            map.put("outputSchema", outputSchema);
        }
        return map;
    }
}
