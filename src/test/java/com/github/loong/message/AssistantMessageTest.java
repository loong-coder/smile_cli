package com.github.loong.message;

import junit.framework.TestCase;

import java.util.List;
import java.util.Map;

/**
 * 验证助手消息在普通回复和工具调用两种场景下的序列化格式。
 */
public class AssistantMessageTest extends TestCase {

    public void testTextMessageToMap() {
        AssistantMessage message = new AssistantMessage("hello");

        Map<String, Object> map = message.toMap();

        assertEquals("assistant", map.get("role"));
        assertEquals("hello", map.get("content"));
        assertFalse(map.containsKey("tool_calls"));
    }

    public void testToolCallMessageToMap() {
        AssistantMessage.ToolCall call = new AssistantMessage.ToolCall(
                "call_1",
                "get_weather_data",
                "{\"location\":\"Beijing\"}");
        AssistantMessage message = new AssistantMessage(null, null, List.of(call));

        Map<String, Object> map = message.toMap();

        assertEquals("assistant", map.get("role"));
        assertNull(map.get("content"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> calls = (List<Map<String, Object>>) map.get("tool_calls");
        assertEquals(1, calls.size());
        assertEquals("call_1", calls.get(0).get("id"));
        assertEquals("function", calls.get(0).get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> function = (Map<String, Object>) calls.get(0).get("function");
        assertEquals("get_weather_data", function.get("name"));
        assertEquals("{\"location\":\"Beijing\"}", function.get("arguments"));
    }
}
