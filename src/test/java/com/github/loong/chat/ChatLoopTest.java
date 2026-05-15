package com.github.loong.chat;

import com.github.loong.tool.ToolDefinition;
import junit.framework.TestCase;

import java.util.List;
import java.util.Map;

/**
 * 验证聊天循环本地命令的输出渲染。
 */
public class ChatLoopTest extends TestCase {

    public void testRenderHelpLinesShowsClearCommand() {
        String output = String.join("\n", ChatLoop.renderHelpLines());

        assertTrue(output.contains("/clear        - Clear console output"));
    }

    public void testRenderToolsShowsInstalledToolDescriptions() {
        List<ToolDefinition> tools = List.of(
                new ToolDefinition("search_docs", "Search Docs", "Search project documentation", Map.of(), null),
                new ToolDefinition("echo", "Echo", "", Map.of(), null));

        String output = String.join("\n", ChatLoop.renderTools(tools));

        assertTrue(output.contains("Installed tools:"));
        assertTrue(output.contains("search_docs - Search project documentation"));
        assertTrue(output.contains("echo - No description"));
    }

    public void testRenderToolsShowsEmptyState() {
        List<String> lines = ChatLoop.renderTools(List.of());

        assertEquals(2, lines.size());
        assertEquals("Installed tools:", lines.get(0));
        assertEquals("  (none)", lines.get(1));
    }
}