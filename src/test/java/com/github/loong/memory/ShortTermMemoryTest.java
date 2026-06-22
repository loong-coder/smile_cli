package com.github.loong.memory;

import junit.framework.TestCase;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 验证短期记忆按完整轮次裁剪并遵守轮数和字节限制。
 */
public class ShortTermMemoryTest extends TestCase {

    public void testOverflowByRoundKeepsLatestTenTurns() {
        ShortTermMemory memory = new ShortTermMemory("ws1", 10, 131072);
        for (int i = 1; i <= 11; i++) {
            memory.startTurn("u" + i);
            memory.recordAssistantMessage("a" + i);
        }

        ShortTermOverflow overflow = memory.selectOverflow();

        assertFalse(overflow.empty());
        assertEquals(1, overflow.turns().size());
        assertEquals("u1", overflow.turns().get(0).entries().get(0).content());
        memory.removeOverflow(overflow);
        assertEquals(10, memory.turnCount());
        assertEquals("u2", memory.turns().get(0).entries().get(0).content());
    }

    public void testOverflowByBytesRemovesEnoughOldTurns() {
        ShortTermMemory memory = new ShortTermMemory("ws1", 10, 20);
        memory.startTurn("1234567890");
        memory.recordAssistantMessage("abc");
        memory.startTurn("abcdefghij");
        memory.recordAssistantMessage("xyz");

        ShortTermOverflow overflow = memory.selectOverflow();

        assertFalse(overflow.empty());
        assertEquals(1, overflow.turns().size());
        memory.removeOverflow(overflow);
        assertTrue(memory.byteCount() <= 20);
        assertEquals(1, memory.turnCount());
    }

    public void testToolMessageStaysInSameTurn() {
        ShortTermMemory memory = new ShortTermMemory("ws1", 10, 131072);
        memory.startTurn("需要读取文件");
        memory.recordAssistantToolCall("call read_file");
        memory.recordToolResult("call_1", "file content");
        memory.recordAssistantMessage("读取完成");

        List<MemoryEntry> entries = memory.turns().get(0).entries();

        assertEquals(4, entries.size());
        assertEquals(MemoryRole.USER, entries.get(0).role());
        assertEquals(MemoryRole.ASSISTANT, entries.get(1).role());
        assertEquals(MemoryRole.TOOL, entries.get(2).role());
        assertEquals(MemoryRole.ASSISTANT, entries.get(3).role());
    }

    public void testCreateMemoryEntryCalculatesByteCount() {
        MemoryEntry entry = MemoryEntry.create(
                "ws1",
                MemoryRole.USER,
                MemoryType.CONVERSATION,
                "你好",
                Map.of("turn", "1"),
                0.5d,
                Instant.parse("2026-06-22T00:00:00Z"));

        assertEquals(6, entry.byteCount());
        assertEquals("ws1", entry.workspaceId());
        assertEquals(0.5d, entry.importance());
    }
}
