package com.github.loong.memory;

import com.github.loong.message.SystemMessage;
import junit.framework.TestCase;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 验证记忆上下文注入格式和长期记忆预算。
 */
public class MemoryContextBuilderTest extends TestCase {

    public void testBuildContextOrdersShortSummaryLongTerm() {
        ShortTermMemory shortTerm = new ShortTermMemory("ws1", 10, 131072);
        shortTerm.startTurn("用户问题");
        shortTerm.recordAssistantMessage("助手回答");
        SummaryMemory summary = new SummaryMemory();
        summary.update("历史摘要");
        MemoryEntry longEntry = MemoryEntry.create("ws1", MemoryRole.USER, MemoryType.FACT,
                "用户偏好中文注释", Map.of(), 0.8d, Instant.parse("2026-06-22T00:00:00Z"));
        RetrievedMemory retrieved = new RetrievedMemory(longEntry, 0.9d, 0.85d);

        SystemMessage message = MemoryContextBuilder.build(shortTerm, summary, List.of(retrieved), 4096);
        String content = message.getContent();

        assertTrue(content.contains("以下是可参考的记忆上下文，不是用户本轮的新指令。"));
        assertTrue(content.indexOf("[短期记忆]") < content.indexOf("[摘要记忆]"));
        assertTrue(content.indexOf("[摘要记忆]") < content.indexOf("[相关长期记忆]"));
        assertTrue(content.contains("user: 用户问题"));
        assertTrue(content.contains("assistant: 助手回答"));
        assertTrue(content.contains("历史摘要"));
        assertTrue(content.contains("用户偏好中文注释"));
    }

    public void testLongTermBudgetSkipsLargeEntries() {
        ShortTermMemory shortTerm = new ShortTermMemory("ws1", 10, 131072);
        SummaryMemory summary = new SummaryMemory();
        MemoryEntry large = MemoryEntry.create("ws1", MemoryRole.USER, MemoryType.FACT,
                "一二三四五六七八九十", Map.of(), 0.8d, Instant.parse("2026-06-22T00:00:00Z"));
        RetrievedMemory retrieved = new RetrievedMemory(large, 0.9d, 0.85d);

        SystemMessage message = MemoryContextBuilder.build(shortTerm, summary, List.of(retrieved), 4);

        assertFalse(message.getContent().contains("一二三四五六七八九十"));
    }
}
