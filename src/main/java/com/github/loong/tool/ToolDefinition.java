package com.github.loong.tool;

import java.util.LinkedHashMap;
import java.util.Map;

public class ToolDefinition {

    private final String name;
    private final String title;
    private final String description;
    private final Map<String, Object> inputSchema;
    private final Map<String, Object> outputSchema;

    public ToolDefinition(String name, String title, String description, Map<String, Object> inputSchema,
                          Map<String, Object> outputSchema) {
        this.name = name;
        this.title = title;
        this.description = description;
        this.inputSchema = inputSchema;
        this.outputSchema = outputSchema;
    }

    public String name() {
        return name;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public Map<String, Object> inputSchema() {
        return inputSchema;
    }

    public Map<String, Object> outputSchema() {
        return outputSchema;
    }

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
