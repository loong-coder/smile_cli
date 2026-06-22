package com.github.loong.memory;

import junit.framework.TestCase;

import java.util.List;

/**
 * 验证 /memory 命令的纯渲染逻辑。
 */
public class MemoryCommandTest extends TestCase {

    public void testRenderStatusShowsCoreFields() {
        MemoryStatus status = new MemoryStatus("ws1", 2, 100, true, false, "", "检索失败");

        List<String> lines = MemoryCommand.renderStatus(status);
        String output = String.join("\n", lines);

        assertTrue(output.contains("Workspace ID: ws1"));
        assertTrue(output.contains("Short-term turns: 2"));
        assertTrue(output.contains("Short-term bytes: 100"));
        assertTrue(output.contains("Has summary: true"));
        assertTrue(output.contains("Long-term enabled: false"));
        assertTrue(output.contains("Last retrieval error: 检索失败"));
    }

    public void testRenderSearchResultsShowsScoresAndContent() {
        MemoryEntry entry = MemoryEntry.create("ws1", MemoryRole.USER, MemoryType.FACT,
                "用户偏好中文", java.util.Map.of(), 0.8d, java.time.Instant.parse("2026-06-22T00:00:00Z"));
        List<String> lines = MemoryCommand.renderSearchResults(List.of(new RetrievedMemory(entry, 0.9d, 0.85d)));
        String output = String.join("\n", lines);

        assertTrue(output.contains("1. [FACT] score=0.85 similarity=0.90"));
        assertTrue(output.contains("用户偏好中文"));
    }

    public void testRenderSearchResultsEmpty() {
        assertEquals("No memory found.", MemoryCommand.renderSearchResults(List.of()).get(0));
    }
}
