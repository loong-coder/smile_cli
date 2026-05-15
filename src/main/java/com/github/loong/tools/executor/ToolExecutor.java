package com.github.loong.tools.executor;

import java.util.Map;

@FunctionalInterface
public interface ToolExecutor {

    Object execute(Map<String, Object> arguments) throws Exception;
}