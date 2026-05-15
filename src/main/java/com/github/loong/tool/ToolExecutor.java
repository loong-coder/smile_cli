package com.github.loong.tool;

import java.util.Map;

@FunctionalInterface
public interface ToolExecutor {

    Object execute(Map<String, Object> arguments) throws Exception;
}