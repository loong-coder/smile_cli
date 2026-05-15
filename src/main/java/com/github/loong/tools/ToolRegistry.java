package com.github.loong.tools;


import com.github.loong.tools.executor.ReflectiveToolExecutor;
import com.github.loong.tools.executor.ToolExecutor;
import com.github.loong.tools.util.SwaggerToolDescriptionParser;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 维护工具定义和执行器映射，不直接执行工具调用。
 */
public class ToolRegistry {

    private final Map<String, RegisteredTool> tools;

    public ToolRegistry() {
        this.tools = new LinkedHashMap<>();
    }

    public void register(Object target) {
        if (target == null) {
            throw new IllegalArgumentException("target cannot be null");
        }

        Method[] methods = target.getClass().getDeclaredMethods();
        Arrays.sort(methods, Comparator.comparing(Method::getName));
        for (Method method : methods) {
            if (Modifier.isPublic(method.getModifiers()) && !method.isBridge() && !method.isSynthetic()) {
                register(target, method);
            }
        }
    }

    public void register(Object target, Method method) {
        if (target == null) {
            throw new IllegalArgumentException("target cannot be null");
        }
        if (method == null) {
            throw new IllegalArgumentException("method cannot be null");
        }

        ToolDefinition definition = SwaggerToolDescriptionParser.parse(method);
        register(definition, new ReflectiveToolExecutor(target, method));
    }

    public void register(ToolDefinition definition, ToolExecutor executor) {
        if (definition == null) {
            throw new IllegalArgumentException("definition cannot be null");
        }
        if (executor == null) {
            throw new IllegalArgumentException("executor cannot be null");
        }
        if (tools.containsKey(definition.name())) {
            throw new IllegalArgumentException("duplicate tool name: " + definition.name());
        }
        tools.put(definition.name(), new RegisteredTool(definition, executor));
    }

    public List<ToolDefinition> definitions() {
        List<ToolDefinition> definitions = new ArrayList<>();
        for (RegisteredTool tool : tools.values()) {
            definitions.add(tool.definition());
        }
        return List.copyOf(definitions);
    }

    public ToolExecutor executor(String name) {
        RegisteredTool tool = tools.get(name);
        if (tool == null) {
            return null;
        }
        return tool.executor();
    }

    private record RegisteredTool(ToolDefinition definition, ToolExecutor executor) {
    }
}