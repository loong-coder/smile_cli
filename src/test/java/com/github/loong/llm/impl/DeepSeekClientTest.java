package com.github.loong.llm.impl;

import com.github.loong.llm.ChatResult;
import com.github.loong.message.AssistantMessage;
import com.github.loong.message.Message;
import com.github.loong.message.UserMessage;
import com.github.loong.tools.ToolDefinition;
import junit.framework.TestCase;

import java.util.List;
import java.util.Map;

/**
 * 验证 DeepSeek 请求体工具适配和流式工具调用聚合。
 */
public class DeepSeekClientTest extends TestCase {

    public void testBuildRequestBodyWithoutTools() {
        List<Message> messages = List.of(new UserMessage("hello"));

        Map<String, Object> body = DeepSeekClient.buildRequestBody("deepseek-chat", messages, List.of());

        assertEquals("deepseek-chat", body.get("model"));
        assertEquals(Boolean.TRUE, body.get("stream"));
        assertTrue(body.containsKey("messages"));
        assertFalse(body.containsKey("tools"));
        assertFalse(body.containsKey("tool_choice"));
    }

    public void testBuildRequestBodyWithTools() {
        ToolDefinition definition = new ToolDefinition(
                "get_weather_data",
                "Weather Data Retriever",
                "Get current weather data",
                Map.of("type", "object", "properties", Map.of()),
                Map.of("type", "object"));

        Map<String, Object> body = DeepSeekClient.buildRequestBody(
                "deepseek-chat",
                List.of(new UserMessage("weather")),
                List.of(definition));

        assertEquals("auto", body.get("tool_choice"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) body.get("tools");
        assertEquals(1, tools.size());
        assertEquals("function", tools.get(0).get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> function = (Map<String, Object>) tools.get(0).get("function");
        assertEquals("get_weather_data", function.get("name"));
        assertEquals("Get current weather data", function.get("description"));
        assertTrue(function.containsKey("parameters"));
        assertFalse(function.containsKey("outputSchema"));
    }

    public void testParseContentChunks() throws Exception {
        ChatResult result = DeepSeekClient.parseChunks(List.of(
                "{\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}",
                "{\"choices\":[{\"delta\":{\"content\":\" world\"},\"finish_reason\":\"stop\"}]}"));

        assertEquals("hello world", result.content());
        assertEquals("stop", result.finishReason());
        assertFalse(result.hasToolCalls());
    }

    public void testParseStreamingToolCallChunks() throws Exception {
        ChatResult result = DeepSeekClient.parseChunks(List.of(
                "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"get_weather_data\",\"arguments\":\"{\\\"location\\\"\"}}]}}]}",
                "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\":\\\"Beijing\\\"}\"}}]},\"finish_reason\":\"tool_calls\"}]}"));

        assertEquals("tool_calls", result.finishReason());
        assertTrue(result.hasToolCalls());
        AssistantMessage.ToolCall call = result.toolCalls().get(0);
        assertEquals("call_1", call.id());
        assertEquals("get_weather_data", call.name());
        assertEquals("{\"location\":\"Beijing\"}", call.argumentsJson());
    }
}
