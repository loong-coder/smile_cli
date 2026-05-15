package com.github.loong.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.loong.message.AssistantMessage;

import java.util.Map;

/**
 * 执行模型返回的工具调用，并把执行结果转换为工具消息内容。
 */
public class ToolCallExecutor {

    private final ToolRegistry registry;
    private final ObjectMapper objectMapper;

    public ToolCallExecutor(ToolRegistry registry) {
        this(registry, new ObjectMapper());
    }

    ToolCallExecutor(ToolRegistry registry, ObjectMapper objectMapper) {
        if (registry == null) {
            throw new IllegalArgumentException("registry cannot be null");
        }
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper cannot be null");
        }
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    /**
     * 工具调用失败时返回错误 JSON，避免单次工具异常中断后续对话。
     */
    public String execute(AssistantMessage.ToolCall call) {
        try {
            return executeOrThrow(call);
        } catch (Exception e) {
            return errorJson(e);
        }
    }

    private String executeOrThrow(AssistantMessage.ToolCall call) throws Exception {
        if (call == null) {
            throw new IllegalArgumentException("call cannot be null");
        }

        ToolExecutor executor = registry.executor(call.name());
        if (executor == null) {
            throw new IllegalArgumentException("unknown tool: " + call.name());
        }

        Object result = executor.execute(parseArguments(call.argumentsJson()));
        return objectMapper.writeValueAsString(result);
    }

    private Map<String, Object> parseArguments(String argumentsJson) throws Exception {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return Map.of();
        }
        return objectMapper.readValue(argumentsJson, new TypeReference<Map<String, Object>>() {
        });
    }

    private String errorJson(Exception e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        try {
            return objectMapper.writeValueAsString(Map.of("error", message));
        } catch (Exception ignored) {
            return "{\"error\":\"\"}";
        }
    }
}