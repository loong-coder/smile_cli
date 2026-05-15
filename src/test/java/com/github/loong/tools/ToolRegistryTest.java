package com.github.loong.tools;

import com.github.loong.message.AssistantMessage;
import com.github.loong.tools.executor.ToolCallExecutor;
import io.swagger.v3.oas.annotations.media.Schema;
import junit.framework.TestCase;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * 验证函数注册、重复名称保护和工具调用执行。
 */
public class ToolRegistryTest extends TestCase {

    public void testRegisterObjectCreatesDefinitions() {
        ToolRegistry registry = new ToolRegistry();

        registry.register(new WeatherTools());

        List<ToolDefinition> definitions = registry.definitions();
        assertEquals(2, definitions.size());
        assertEquals("echo", definitions.get(0).name());
        assertEquals("get_weather_data", definitions.get(1).name());
    }

    public void testDuplicateToolNameFails() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        Method method = WeatherTools.class.getDeclaredMethod("getWeatherData", String.class);
        WeatherTools target = new WeatherTools();

        registry.register(target, method);

        try {
            registry.register(target, method);
            fail("duplicate tool name should fail");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("duplicate tool name"));
        }
    }

    public void testReflectiveExecutorConvertsArgumentsAndSerializesResult() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new WeatherTools());
        ToolCallExecutor executor = new ToolCallExecutor(registry);

        String result = executor.execute(new AssistantMessage.ToolCall(
                "call_1",
                "get_weather_data",
                "{\"location\":\"Beijing\"}"));

        assertTrue(result.contains("\"location\":\"Beijing\""));
        assertTrue(result.contains("\"temperature\":26.5"));
    }

    public void testManualExecutorRegistration() {
        ToolRegistry registry = new ToolRegistry();
        ToolDefinition definition = new ToolDefinition(
                "add",
                "Add",
                "Add two numbers",
                Map.of("type", "object"),
                Map.of("type", "number"));
        registry.register(definition, arguments -> ((Number) arguments.get("a")).intValue()
                + ((Number) arguments.get("b")).intValue());
        ToolCallExecutor executor = new ToolCallExecutor(registry);

        String result = executor.execute(new AssistantMessage.ToolCall("call_1", "add", "{\"a\":2,\"b\":3}"));

        assertEquals("5", result);
    }

    public void testUnknownToolReturnsErrorJson() {
        ToolCallExecutor executor = new ToolCallExecutor(new ToolRegistry());

        String result = executor.execute(new AssistantMessage.ToolCall("call_1", "missing", "{}"));

        assertTrue(result.contains("unknown tool: missing"));
    }

    public void testReflectiveExecutorUnwrapsToolException() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new FailingTools());
        ToolCallExecutor executor = new ToolCallExecutor(registry);

        String result = executor.execute(new AssistantMessage.ToolCall("call_1", "fail", "{}"));

        assertTrue(result.contains("tool failed"));
        assertFalse(result.contains("InvocationTargetException"));
    }

    public void testFailureLogMessageIncludesToolContext() {
        String message = ToolCallExecutor.failureLogMessage(
                new AssistantMessage.ToolCall("call_1", "read_file", "{\"path\":\"README.md\"}"),
                new IllegalStateException("tool failed"));

        assertTrue(message.contains("callId=call_1"));
        assertTrue(message.contains("toolName=read_file"));
        assertTrue(message.contains("argumentsJson={\"path\":\"README.md\"}"));
        assertTrue(message.contains("errorType=java.lang.IllegalStateException"));
        assertTrue(message.contains("errorMessage=tool failed"));
    }

    private static class WeatherTools {

        @Schema(name = "echo", description = "Echo text")
        public String echo(@Schema(name = "text") String text) {
            return text;
        }

        @Schema(name = "get_weather_data", title = "Weather Data Retriever", description = "Get current weather data")
        public WeatherData getWeatherData(@Schema(name = "location") String location) {
            return new WeatherData(location, 26.5);
        }
    }

    private static class FailingTools {

        @Schema(name = "fail", description = "Always fail")
        public String fail() {
            throw new IllegalStateException("tool failed");
        }
    }

    private record WeatherData(String location, double temperature) {
    }
}
