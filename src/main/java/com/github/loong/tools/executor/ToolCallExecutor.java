package com.github.loong.tools.executor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.loong.message.AssistantMessage;
import com.github.loong.tools.ToolRegistry;
import com.github.loong.tools.exception.WorkspaceConstraintException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 执行模型返回的工具调用，并把执行结果转换为工具消息内容。
 */
public class ToolCallExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ToolCallExecutor.class);
    private static final int MAX_LOG_ARGUMENT_CHARS = 4096;

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
     * 工作区越界不视为系统错误，返回带有状态标识的结果供调用方识别。
     */
    public String execute(AssistantMessage.ToolCall call) {
        try {
            return executeOrThrow(call);
        } catch (WorkspaceConstraintException e) {
            // 工作区越界是预期内约束，WARN 级别即可，返回结构化状态让调用方知道越界
            LOGGER.warn("{}", failureLogMessage(call, e));
            return outOfBoundsJson(e);
        } catch (Exception e) {
            LOGGER.error("{}", failureLogMessage(call, e), e);
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

    public static String failureLogMessage(AssistantMessage.ToolCall call, Exception e) {
        String errorType = e.getClass().getName();
        String errorMessage = e.getMessage() == null ? "" : e.getMessage();
        if (call == null) {
            return "Tool call execution failed: callId=<null>, toolName=<null>, argumentsJson=<null>, errorType="
                    + errorType + ", errorMessage=" + errorMessage;
        }
        return "Tool call execution failed: callId=" + nullSafe(call.id())
                + ", toolName=" + nullSafe(call.name())
                + ", argumentsJson=" + truncateArguments(call.argumentsJson())
                + ", errorType=" + errorType
                + ", errorMessage=" + errorMessage;
    }

    private static String truncateArguments(String argumentsJson) {
        // 入参可能很长，日志只截断展示，完整错误仍通过堆栈定位。
        String value = nullSafe(argumentsJson);
        if (value.length() <= MAX_LOG_ARGUMENT_CHARS) {
            return value;
        }
        return value.substring(0, MAX_LOG_ARGUMENT_CHARS) + "...<truncated>";
    }

    private static String nullSafe(String value) {
        return value == null ? "<null>" : value;
    }

    /**
     * 工作区越界时返回带状态标识的 JSON，与普通 error 区分，
     * 调用方可据此判断是越界而非系统故障。
     */
    private String outOfBoundsJson(WorkspaceConstraintException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        String path = e.requestedPath() == null ? "" : e.requestedPath();
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "status", "path_outside_workspace",
                    "path", path,
                    "message", message));
        } catch (Exception ignored) {
            return "{\"status\":\"path_outside_workspace\"}";
        }
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